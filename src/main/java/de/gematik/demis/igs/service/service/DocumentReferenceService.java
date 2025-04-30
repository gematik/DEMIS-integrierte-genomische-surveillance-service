package de.gematik.demis.igs.service.service;

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

import static de.gematik.demis.igs.service.exception.ErrorCode.INTERNAL_SERVER_ERROR;
import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_DOCUMENT;
import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_DOCUMENT_VALIDATION;
import static de.gematik.demis.igs.service.exception.ErrorCode.MULTIPART_UPLOAD_COMPLETE_ERROR;
import static de.gematik.demis.igs.service.exception.ErrorCode.UPLOAD_DOCUMENT_ONGOING;
import static de.gematik.demis.igs.service.parser.FhirParser.deserializeResource;
import static de.gematik.demis.igs.service.utils.Constants.HASH_METADATA_NAME;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS_DONE;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_NOT_INITIATED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INTERNAL_SERVER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INVALID_COMPRESSED_FILE_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.JwtUtils.hasRole;
import static java.io.InputStream.nullInputStream;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;

import de.gematik.demis.igs.service.api.model.CompletedChunk;
import de.gematik.demis.igs.service.api.model.MultipartUploadComplete;
import de.gematik.demis.igs.service.api.model.S3Info;
import de.gematik.demis.igs.service.api.model.ValidationInfo;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.storage.SimpleStorageService;
import de.gematik.demis.igs.service.service.validation.HashValidatorFunction;
import de.gematik.demis.igs.service.service.validation.SequenceValidatorService;
import de.gematik.demis.igs.service.service.validation.ValidationTracker;
import de.gematik.demis.igs.service.utils.Pair;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceContentComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Service class for handling DocumentReference operations */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentReferenceService {

  public static final String FASTA_ONLY_ROLE = "igs-sequence-data-sender-fasta-only";
  private final SimpleStorageService storageService;
  private final SequenceValidatorService sequenceValidatorService;
  private final ProxyInputStreamService proxy;
  private final ValidationTracker validationTracker;

  @Setter
  @Value("${igs.long-polling-timeout-secs}")
  private int longPollingTimeoutSecs;

  @Setter
  @Value("${igs.long-polling-interval-secs}")
  private int longPollingIntervalSecs;

  /**
   * Creates a DocumentReference placeholder in a S3 compatible storage, which then will contain the
   * fasta/fastq data
   *
   * @param content serialized DocumentReference
   * @param mediaType mime type used for the deserialization
   * @return documentReference representation with a generated UUID
   */
  public DocumentReference generateDocumentReference(String content, MediaType mediaType) {
    DocumentReference documentReference =
        deserializeResource(content, mediaType, DocumentReference.class);
    HashMap<String, String> metadata = getAttachmentMetadata(documentReference);
    metadata.put(VALIDATION_STATUS, VALIDATION_NOT_INITIATED.name());
    String documentReferenceId = UUID.randomUUID().toString();
    documentReference.setId(documentReferenceId);

    storageService.putBlob(documentReferenceId, metadata, nullInputStream());

    return documentReference;
  }

  /**
   * Checks if the validation status in metadata is set or set to VALIDATION_NOT_INITIATED. If the
   * key is present and have another status than VALIDATION_NOT_INITIATED, an exception is thrown.
   *
   * @param documentId the id of the document to validate
   */
  public void prepareValidation(String documentId) {
    Map<String, String> metaData = new HashMap<>(storageService.getMetadata(documentId));
    String uploadStatus = metaData.get(UPLOAD_STATUS);
    if (uploadStatus == null || !uploadStatus.equals(UPLOAD_STATUS_DONE)) {
      throw new IgsServiceException(
          UPLOAD_DOCUMENT_ONGOING,
          "Der Upload des angefragen Dokuments ist noch nicht abgeschlossen.");
    }
    String validationStatus = metaData.get(VALIDATION_STATUS);
    if (validationStatus != null && !validationStatus.equals(VALIDATION_NOT_INITIATED.name())) {
      throw new IgsServiceException(
          INVALID_DOCUMENT_VALIDATION,
          format("Document with id %s is already validating", documentId));
    }
    storageService.setValidatingStatusToPending(documentId);
  }

  /**
   * Returns the binary data of the attachment for a given DocumentReference
   *
   * @param documentId the id of the existing document
   * @return InputStreamResource of the attachment
   */
  public InputStream getBinary(String documentId) {
    return storageService.getBlobFromValidBucket(documentId);
  }

  /**
   * Returns the signed URL for a given document
   *
   * @param documentId the id of the existing document
   * @param fileSize the size of the file to upload in bytes
   * @return String signed-url
   */
  public S3Info determineUploadInfo(String documentId, double fileSize) {
    storageService.checkIfDocumentExists(documentId);
    return storageService.createSignedUrls(documentId, fileSize);
  }

  /**
   * Loads the binary data from the storage and validates it. Is async so the client has to poll for
   * the result.
   *
   * @param documentId the id of the existing document
   */
  @Async
  public void validateBinary(String documentId, String authorization) {
    validationTracker.init(documentId);
    InputStream stream = storageService.getBlob(documentId);
    Map<String, String> metaData = storageService.getMetadata(documentId);
    Pair pair = storageService.getFirstBytesOf(documentId);
    InputStream hashValidated =
        proxy.run(
            stream,
            new HashValidatorFunction(
                metaData.get(HASH_METADATA_NAME), documentId, validationTracker));
    InputStream decompressed =
        proxy.run(
            hashValidated,
            new GzipDecompressionFunction(
                Integer.parseInt(pair.first()),
                Integer.parseInt(pair.second()),
                documentId,
                validationTracker));
    try (decompressed) {
      sequenceValidatorService.validateSequence(
          decompressed, documentId, validationTracker, hasRole(authorization, FASTA_ONLY_ROLE));
    } catch (Exception ex) {
      handleException(documentId, ex);
    } finally {
      storageService.finalizeValidation(documentId);
      validationTracker.drop(documentId);
    }
  }

  /**
   * Polls the storage service for the validation status of the given document IDs.
   *
   * @param documentId the list of document IDs to check the validation status for
   * @return a list of ValidationInfo objects containing the validation status for each document ID
   */
  public ValidationInfo getValidationStatus(String documentId) {
    AtomicReference<ValidationInfo> result =
        new AtomicReference<>(ValidationInfo.builder().documentId(documentId).build());
    try {
      await()
          .atMost(ofSeconds(longPollingTimeoutSecs))
          .pollDelay(ofSeconds(0))
          .pollInterval(ofSeconds(longPollingIntervalSecs))
          .ignoreExceptions()
          .pollInSameThread()
          .until(
              () -> {
                result.set(storageService.getStatusOfDocument(result.get()));
                return result.get().isDone();
              });
    } catch (ConditionTimeoutException ex) {
      log.debug("Document is not validated yet {}", result.get());
    }
    return result.get();
  }

  /** Informs the S3 that a multipart upload has been completed. */
  public void finishUpload(String documentId, MultipartUploadComplete multipartUploadComplete) {
    storageService.checkIfDocumentExists(documentId);
    String uploadId = multipartUploadComplete.uploadId();
    if (uploadId == null || uploadId.isEmpty()) {
      throw new IgsServiceException(MULTIPART_UPLOAD_COMPLETE_ERROR, "uploadId cannot be empty.");
    }
    List<CompletedChunk> completedChunks = multipartUploadComplete.completedChunks();
    if (completedChunks == null || completedChunks.isEmpty()) {
      throw new IgsServiceException(
          MULTIPART_UPLOAD_COMPLETE_ERROR, "completedChunks cannot be empty.");
    }
    storageService.informUploadComplete(documentId, uploadId, completedChunks);
  }

  private HashMap<String, String> getAttachmentMetadata(DocumentReference documentReference) {
    List<DocumentReferenceContentComponent> documentReferenceContent =
        documentReference.getContent();
    if (documentReferenceContent.isEmpty()) {
      throw new IgsServiceException(INVALID_DOCUMENT, "DocumentReference content is not present");
    }
    String hash =
        documentReferenceContent.getFirst().getAttachment().getHashElement().getValueAsString();
    if (hash == null) {
      throw new IgsServiceException(
          INVALID_DOCUMENT, "DocumentReference does not contain a hash value");
    }
    HashMap<String, String> metadata = new HashMap<>();
    metadata.put(HASH_METADATA_NAME, hash);
    return metadata;
  }

  private void handleException(String documentId, Exception ex) {
    log.error("Error while validating document", ex);
    if (ex instanceof ZipException e) {
      validationTracker.updateGzipStatus(
          documentId, VALIDATION_FAILED, INVALID_COMPRESSED_FILE_ERROR_MSG);
    } else {
      validationTracker.updateValidationStatus(
          documentId, VALIDATION_FAILED, INTERNAL_SERVER_ERROR_MESSAGE);
    }
    throw new IgsServiceException(INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
  }
}
