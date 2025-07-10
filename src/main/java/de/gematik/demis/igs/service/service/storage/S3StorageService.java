package de.gematik.demis.igs.service.service.storage;

/*-
 * #%L
 * Integrierte-Genomische-Surveillance-Service
 * %%
 * Copyright (C) 2025 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static de.gematik.demis.igs.service.exception.ErrorCode.FILE_NOT_FOUND;
import static de.gematik.demis.igs.service.exception.ErrorCode.INTERNAL_SERVER_ERROR;
import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_FILE_SIZE;
import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_UPLOAD;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS_DONE;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_DESCRIPTION;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.FILE_SIZE_TO_LARGE_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INTERNAL_SERVER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.utils.ErrorMessages.RESOURCE_NOT_FOUND_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.Pair.pair;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static org.awaitility.Awaitility.await;
import static software.amazon.awssdk.services.s3.model.MetadataDirective.REPLACE;

import de.gematik.demis.igs.service.api.model.CompletedChunk;
import de.gematik.demis.igs.service.api.model.S3Info;
import de.gematik.demis.igs.service.api.model.ValidationInfo;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.validation.ValidationTracker;
import de.gematik.demis.igs.service.utils.Pair;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements SimpleStorageService {

  public static final String LIFECYCLE_RULE_ID_TO_VALIDATE = "Delete not validated documents after";
  public static final String LIFECYCLE_RULE_ID_VALID = "Delete validated documents after";
  private final SimpleStorageServiceConfiguration s3configuration;
  private final ValidationTracker validationTracker;
  private final S3Client s3;
  private final S3Presigner presigner;

  @EventListener
  public void handleApplicationReady(ApplicationReadyEvent event) {
    ensureBucket();
    ensureBucket(s3configuration.getValidatedBucket().getName());
    createLifeCycleRule(
        LIFECYCLE_RULE_ID_TO_VALIDATE,
        s3configuration.getUploadBucket().getDeletionDeadlineInDays(),
        s3configuration.getUploadBucket().getName());
    createLifeCycleRule(
        LIFECYCLE_RULE_ID_VALID,
        s3configuration.getValidatedBucket().getDeletionDeadlineInDays(),
        s3configuration.getValidatedBucket().getName());
  }

  @Override
  public void putBlob(String documentId, Map<String, String> metadata, InputStream stream) {
    try {
      ensureBucket();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      TeeInputStream proxy = new TeeInputStream(stream, out);
      long contentLength = getInputStreamLength(proxy);
      PutObjectRequest putRequest =
          PutObjectRequest.builder()
              .bucket(s3configuration.getUploadBucket().getName())
              .key(documentId)
              .metadata(metadata)
              .build();
      s3.putObject(
          putRequest,
          RequestBody.fromInputStream(new ByteArrayInputStream(out.toByteArray()), contentLength));
      log.debug("successfully uploaded");
    } catch (Exception ex) {
      handleBucketError(ex);
    }
  }

  @Override
  public Map<String, String> getMetadata(String documentId) throws IgsServiceException {
    HeadObjectResponse response = getHeadObjectResponse(documentId);
    if (response != null) {
      return response.metadata();
    }
    return Map.of();
  }

  @Override
  public InputStream getBlob(String documentId) {
    try {
      checkIfDocumentExistsAndNotEmpty(documentId);
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder()
              .bucket(s3configuration.getUploadBucket().getName())
              .key(documentId)
              .build();
      return s3.getObject(getObjectRequest);
    } catch (Exception ex) {
      handleBucketError(ex);
    }
    return null;
  }

  @Override
  public S3Info createSignedUrls(String documentId, double fileSize) {
    try {
      if (fileSize > s3configuration.getMultipartMaxUploadSizeInBytes()) {
        throw new IgsServiceException(INVALID_FILE_SIZE, FILE_SIZE_TO_LARGE_ERROR_MSG);
      }
      Map<String, String> metaData = getMetadata(documentId);
      double neededPartCount =
          Math.ceil(fileSize / s3configuration.getMultipartUploadChunkSizeInBytes());
      CreateMultipartUploadRequest createMultipartUploadRequest =
          CreateMultipartUploadRequest.builder()
              .bucket(s3configuration.getUploadBucket().getName())
              .metadata(metaData)
              .key(documentId)
              .build();
      CreateMultipartUploadResponse response =
          s3.createMultipartUpload(createMultipartUploadRequest);
      String uploadId = response.uploadId();
      List<String> presignedUrls = new ArrayList<>();
      for (int i = 1; i <= neededPartCount; i++) {
        UploadPartRequest uploadPartRequest =
            UploadPartRequest.builder()
                .bucket(s3configuration.getUploadBucket().getName())
                .key(documentId)
                .uploadId(uploadId)
                .partNumber(i)
                .build();
        UploadPartPresignRequest uploadPartPresignRequest =
            UploadPartPresignRequest.builder()
                .uploadPartRequest(uploadPartRequest)
                .signatureDuration(
                    Duration.ofMinutes(s3configuration.getSignedUrlExpirationInMinutes()))
                .build();
        PresignedUploadPartRequest presignedUrl =
            presigner.presignUploadPart(uploadPartPresignRequest);
        presignedUrls.add(
            presignedUrl.toBuilder().isBrowserExecutable(true).build().url().toURI().toString());
      }
      return new S3Info(
          uploadId, presignedUrls, s3configuration.getMultipartUploadChunkSizeInBytes());
    } catch (Exception ex) {
      handleBucketError(ex);
    }
    return null;
  }

  @Override
  public void checkIfDocumentExists(String documentId) {
    this.getMetadata(documentId);
  }

  @Override
  public void updateMetaDataValues(String documentId, Pair... pairs) {
    List<Pair> filteredList =
        Arrays.stream(pairs).filter(p -> !p.first().equals(VALIDATION_STATUS)).toList();
    updateMetaData(documentId, filteredList);
  }

  @Override
  public void setValidatingStatusToPending(String documentId) {
    updateMetaData(documentId, List.of(pair(VALIDATION_STATUS, VALIDATING.name())));
  }

  // This Function got called after validation. Only waits for the other threads. 10 seconds should
  // be enough
  @Override
  public void finalizeValidation(String documentId) {
    try {
      await()
          .atMost(ofSeconds(10))
          .pollInterval(ofMillis(500))
          .until(() -> validationTracker.isFinished(documentId));
    } catch (ConditionTimeoutException ex) {
      log.error("Validation did not finish in time");
      updateMetaData(
          documentId,
          List.of(
              pair(VALIDATION_STATUS, VALIDATION_FAILED.name()),
              pair(VALIDATION_DESCRIPTION, INTERNAL_SERVER_ERROR_MESSAGE)));
      this.emptyFile(documentId);
      return;
    }

    List<Pair> finalMetaData = validationTracker.calculateMetaData(documentId);
    updateMetaData(documentId, finalMetaData);
    if (finalMetaData.stream().map(Pair::second).toList().contains(VALIDATION_FAILED.name())) {
      this.emptyFile(documentId);
    }
    if (finalMetaData.stream().map(Pair::second).toList().contains(VALID.name())) {
      moveFileToValidBucket(documentId);
    }
  }

  protected synchronized void updateMetaData(String documentId, List<Pair> newMetaData) {
    Map<String, String> metaData = new HashMap<>(getMetadata(documentId));
    for (Pair pair : newMetaData) {
      metaData.put(pair.first(), pair.second());
    }
    CopyObjectRequest copyRequest =
        CopyObjectRequest.builder()
            .sourceBucket(s3configuration.getUploadBucket().getName())
            .sourceKey(documentId)
            .destinationBucket(s3configuration.getUploadBucket().getName())
            .destinationKey(documentId)
            .metadata(metaData)
            .metadataDirective(REPLACE)
            .build();
    s3.copyObject(copyRequest);
  }

  @Override
  public Pair getFirstBytesOf(String documentId) {
    checkIfDocumentExistsAndNotEmpty(documentId);
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder()
            .bucket(s3configuration.getUploadBucket().getName())
            .key(documentId)
            .range("bytes=0-1")
            .build();
    ResponseInputStream<GetObjectResponse> s3Object = s3.getObject(getObjectRequest);
    try {
      Pair firstBytes = new Pair(String.valueOf(s3Object.read()), String.valueOf(s3Object.read()));
      s3Object.close();
      return firstBytes;
    } catch (IOException ex) {
      handleBucketError(ex);
    }
    return null;
  }

  @Override
  public void emptyFile(String documentId) {
    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(s3configuration.getUploadBucket().getName())
            .key(documentId)
            .metadata(getMetadata(documentId))
            .build();
    s3.putObject(putRequest, RequestBody.fromInputStream(InputStream.nullInputStream(), 0));
    log.debug("File {} emptied", documentId);
  }

  @Override
  public ValidationInfo getStatusOfDocument(ValidationInfo validationInfo) {
    if (validationInfo.isDone()) {
      return validationInfo;
    }
    Map<String, String> metadata = getMetadata(validationInfo.getDocumentId());
    validationInfo.setStatus(metadata.get(VALIDATION_STATUS));
    validationInfo.setMessage(metadata.get(VALIDATION_DESCRIPTION));
    return validationInfo;
  }

  @Override
  public void informUploadComplete(
      String documentId, String uploadId, List<CompletedChunk> completedChunks) {
    CompletedMultipartUpload completedMultipartUpload =
        CompletedMultipartUpload.builder().parts(buildCompetedParts(completedChunks)).build();

    CompleteMultipartUploadRequest completeMultipartUploadRequest =
        CompleteMultipartUploadRequest.builder()
            .bucket(s3configuration.getUploadBucket().getName())
            .key(documentId)
            .uploadId(uploadId)
            .multipartUpload(completedMultipartUpload)
            .build();
    try {
      s3.completeMultipartUpload(completeMultipartUploadRequest);
    } catch (NoSuchUploadException e) {
      throw new IgsServiceException(
          INVALID_UPLOAD, "Upload with ID " + uploadId + " does not exist");
    } catch (S3Exception e) {
      throw new IgsServiceException(INVALID_UPLOAD, "E-Tag of the upload is invalid");
    }
    updateMetaData(documentId, List.of(pair(UPLOAD_STATUS, UPLOAD_STATUS_DONE)));
  }

  @Override
  public InputStream getBlobFromValidBucket(String documentId) {
    try {
      checkIfDocumentExistsAndNotEmpty(documentId, s3configuration.getValidatedBucket().getName());
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder()
              .bucket(s3configuration.getValidatedBucket().getName())
              .key(documentId)
              .build();
      return s3.getObject(getObjectRequest);
    } catch (Exception ex) {
      handleBucketError(ex);
    }
    return null;
  }

  private void createLifeCycleRule(String id, Integer days, String bucketName) {
    LifecycleRule expirationRule =
        LifecycleRule.builder()
            .id(id)
            .filter(ruleFilter -> ruleFilter.prefix("").build())
            .status(ExpirationStatus.ENABLED)
            .expiration(exp -> exp.days(days).build())
            .build();

    PutBucketLifecycleConfigurationRequest request =
        PutBucketLifecycleConfigurationRequest.builder()
            .bucket(bucketName)
            .lifecycleConfiguration(config -> config.rules(List.of(expirationRule)))
            .build();
    try {
      s3.putBucketLifecycleConfiguration(request);
    } catch (Exception ex) {
      log.error(
          "Lifecycle rule could not be applied. RuleName: '{}' bucketName: '{}'",
          id,
          bucketName,
          ex);
      return;
    }
    log.info("Lifecycle configuration set to " + days + " days for bucket: " + bucketName);
  }

  private void moveFileToValidBucket(String documentId) {
    try {
      Map<String, String> metaData = getMetadata(documentId);
      if (isNull(metaData.get(VALIDATION_STATUS))
          || !metaData.get(VALIDATION_STATUS).equals(VALID.name())) {
        log.error("Document {} is not valid, skipping transfer", documentId);
        return;
      }
      ensureBucket(s3configuration.getValidatedBucket().getName());
      CopyObjectRequest copyRequest =
          CopyObjectRequest.builder()
              .sourceBucket(s3configuration.getUploadBucket().getName())
              .sourceKey(documentId)
              .destinationBucket(s3configuration.getValidatedBucket().getName())
              .destinationKey(documentId)
              .metadata(metaData) // Preserve metadata
              .metadataDirective(REPLACE)
              .build();
      s3.copyObject(copyRequest);
      emptyFile(documentId);
      log.debug("Document {} successfully transferred to valid bucket", documentId);
    } catch (Exception ex) {
      handleBucketError(ex);
    }
  }

  private void ensureBucket() {
    ensureBucket(s3configuration.getUploadBucket().getName());
  }

  private void ensureBucket(String bucketName) {
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
      log.debug("Bucket " + bucketName + " already exists");
    } catch (Exception e) {
      if (e instanceof S3Exception ex && ex.statusCode() == 404) {
        log.debug("Bucket " + bucketName + " does not exist. Creating it...");
        s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
      } else {
        log.error("Error while creating bucket {}", bucketName, e);
      }
    }
  }

  private void handleBucketError(Exception exception) {
    String message = "Unknown error occurred:";
    if (exception instanceof S3Exception ex) {
      if (ex.statusCode() == 404) {
        throw new IgsServiceException(FILE_NOT_FOUND, RESOURCE_NOT_FOUND_ERROR_MSG, ex);
      } else {
        message = "Storage related exception";
      }
    } else if (exception instanceof IgsServiceException ex) {
      throw ex;
    }
    log.error(message);
    log.error(exception.getClass().getSimpleName());
    throw new IgsServiceException(INTERNAL_SERVER_ERROR, message, exception);
  }

  private void checkIfDocumentExistsAndNotEmpty(String documentId) {
    checkIfDocumentExistsAndNotEmpty(documentId, s3configuration.getUploadBucket().getName());
  }

  private void checkIfDocumentExistsAndNotEmpty(String documentId, String bucketName) {
    HeadObjectResponse headObject = getHeadObjectResponse(documentId, bucketName);
    if (headObject.contentLength() == 0) {
      throw new IgsServiceException(FILE_NOT_FOUND, RESOURCE_NOT_FOUND_ERROR_MSG);
    }
  }

  private HeadObjectResponse getHeadObjectResponse(String documentId) {
    return getHeadObjectResponse(documentId, s3configuration.getUploadBucket().getName());
  }

  private HeadObjectResponse getHeadObjectResponse(String documentId, String bucketName) {
    HeadObjectRequest headRequest =
        HeadObjectRequest.builder().bucket(bucketName).key(documentId).build();
    try {
      return s3.headObject(headRequest);
    } catch (Exception ex) {
      handleBucketError(ex);
    }
    return null;
  }

  private int getInputStreamLength(InputStream inputStream) throws IOException {
    byte[] buffer = new byte[4096];
    int bytesRead;
    int totalBytes = 0;

    while ((bytesRead = inputStream.read(buffer)) != -1) {
      totalBytes += bytesRead;
    }

    return totalBytes;
  }

  private List<CompletedPart> buildCompetedParts(List<CompletedChunk> completedChunks) {
    return completedChunks.stream()
        .map(
            chunk ->
                CompletedPart.builder().partNumber(chunk.partNumber()).eTag(chunk.eTag()).build())
        .toList();
  }
}
