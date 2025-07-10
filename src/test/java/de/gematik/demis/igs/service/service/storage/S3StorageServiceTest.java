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
import static de.gematik.demis.igs.service.service.storage.S3StorageService.LIFECYCLE_RULE_ID_TO_VALIDATE;
import static de.gematik.demis.igs.service.service.storage.S3StorageService.LIFECYCLE_RULE_ID_VALID;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS_DONE;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_DESCRIPTION;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_NOT_INITIATED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.FILE_SIZE_TO_LARGE_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INTERNAL_SERVER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.utils.ErrorMessages.RESOURCE_NOT_FOUND_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.Pair.pair;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.services.s3.model.MetadataDirective.REPLACE;
import static util.BaseUtil.PATH_TO_FASTQ;

import de.gematik.demis.igs.service.api.model.CompletedChunk;
import de.gematik.demis.igs.service.api.model.S3Info;
import de.gematik.demis.igs.service.api.model.ValidationInfo;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.storage.SimpleStorageServiceConfiguration.Bucket;
import de.gematik.demis.igs.service.service.validation.ValidationTracker;
import de.gematik.demis.igs.service.utils.Constants.ValidationStatus;
import de.gematik.demis.igs.service.utils.Pair;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import util.BaseUtil;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

  public static final String EXAMPLE_ID = "SomeId";
  private final BaseUtil baseUtil = new BaseUtil();
  @Mock private S3Client client;
  @Mock private S3Presigner presigner;
  @Mock private ValidationTracker tracker;
  private S3StorageService underTest;
  private SimpleStorageServiceConfiguration config;
  @Captor private ArgumentCaptor<RequestBody> requestBodyCaptor;
  @Captor private ArgumentCaptor<PutObjectRequest> putObjectCaptor;
  @Captor private ArgumentCaptor<GetObjectRequest> getObjectRequestCaptor;
  @Captor private ArgumentCaptor<HeadObjectRequest> headObjectRequestCaptor;
  @Captor private ArgumentCaptor<CopyObjectRequest> copyObjectRequestCaptor;

  @Captor
  private ArgumentCaptor<CompleteMultipartUploadRequest> completeMultipartUploadRequestCaptor;

  @BeforeEach
  void init() {
    config =
        SimpleStorageServiceConfiguration.builder()
            .secretKey("someSecret")
            .accessKey("someAccessKey")
            .uploadBucket(
                Bucket.builder().name("invalidatedBucket").deletionDeadlineInDays(2).build())
            .validatedBucket(
                Bucket.builder().name("validBucket").deletionDeadlineInDays(32).build())
            .url("http://localhost:9000")
            .multipartUploadChunkSizeInBytes(10 * 1024 * 1024)
            .multipartMaxUploadSizeInBytes(1024 * 1024 * 1024)
            .signedUrlExpirationInMinutes(1440)
            .build();
    underTest = spy(new S3StorageService(config, tracker, client, presigner));
  }

  @Nested
  class PutAndGetBlobTests {

    static Stream<Arguments> getExceptionWithDependingMessage() {
      return Stream.of(
          of(mock(ArrayIndexOutOfBoundsException.class), "Unknown error occurred:"),
          of(
              S3Exception.builder().message("Some Error").statusCode(400).build(),
              "Storage related exception"),
          of(
              S3Exception.builder().message("NotFound").statusCode(404).build(),
              "Requested resource not found"));
    }

    @ParameterizedTest
    @MethodSource(value = "getExceptionWithDependingMessage")
    @SneakyThrows
    void putBlobCheckRightMessageIfErrorThrown(Exception ex, String message) {
      when(client.headObject((HeadObjectRequest) any())).thenThrow(ex);
      IgsServiceException res =
          assertThrows(IgsServiceException.class, () -> underTest.getBlob(EXAMPLE_ID));
      assertThat(res.getMessage()).isEqualTo(message);
    }

    @Test
    @SneakyThrows
    void shouldReturnResourceSuccessfully() {
      when(client.headObject((HeadObjectRequest) any()))
          .thenReturn(HeadObjectResponse.builder().contentLength(100L).build());

      when(client.getObject(getObjectRequestCaptor.capture()))
          .thenReturn(
              new ResponseInputStream<>(
                  GetObjectResponse.builder().build(),
                  baseUtil.readFileToInputStream(PATH_TO_FASTQ)));
      InputStream resp = underTest.getBlob(EXAMPLE_ID);
      assertAll(
          () ->
              assertTrue(
                  baseUtil.streamCompare(resp, baseUtil.readFileToInputStream(PATH_TO_FASTQ))),
          () ->
              assertThat(getObjectRequestCaptor.getValue().bucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () -> assertThat(getObjectRequestCaptor.getValue().key()).isEqualTo(EXAMPLE_ID));
    }

    @Test
    @SneakyThrows
    void shouldCallClientCorrectlyOnPutObject() {
      Map<String, String> metaData = Map.of("hash", "SomeHash");
      InputStream stream = baseUtil.readFileToInputStream(PATH_TO_FASTQ);
      long fileSize = baseUtil.getFileSize(PATH_TO_FASTQ);
      underTest.putBlob(EXAMPLE_ID, metaData, stream);
      verify(client).putObject(putObjectCaptor.capture(), requestBodyCaptor.capture());
      assertAll(
          () ->
              assertThat(putObjectCaptor.getValue().bucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () -> assertThat(putObjectCaptor.getValue().key()).isEqualTo(EXAMPLE_ID),
          () -> assertThat(putObjectCaptor.getValue().metadata()).containsEntry("hash", "SomeHash"),
          () -> assertThat(requestBodyCaptor.getValue().optionalContentLength()).isPresent(),
          () ->
              assertThat(requestBodyCaptor.getValue().optionalContentLength().get())
                  .isEqualTo(fileSize));
    }
  }

  @Nested
  class MetadataTests {

    @Test
    @SneakyThrows
    void getMetadataThrowIgsExceptionIfFileNotFound() {
      when(client.headObject((HeadObjectRequest) any()))
          .thenThrow(S3Exception.builder().statusCode(404).build());
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.getMetadata("NotExisting"));
      assertThat(ex.getMessage()).isEqualTo("Requested resource not found");
      assertThat(ex.getErrorCode()).isEqualTo(FILE_NOT_FOUND.toString());
    }

    @Test
    @SneakyThrows
    void shouldThrowNotFoundExceptionIfHeaderSayContentIsZero() {
      when(client.headObject((HeadObjectRequest) any()))
          .thenReturn(HeadObjectResponse.builder().contentLength(0L).build());
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.getBlob("NotExisting"));
      assertThat(ex.getErrorCode()).isEqualTo(FILE_NOT_FOUND.toString());
    }

    @Test
    @SneakyThrows
    void shouldTransformMetaDataCorrectly() {
      when(client.headObject(headObjectRequestCaptor.capture()))
          .thenReturn(
              HeadObjectResponse.builder()
                  .metadata(Map.of("Test1", "ValueOld", "Test2", "ValueOld2"))
                  .contentLength(100L)
                  .build());
      underTest.updateMetaDataValues(
          EXAMPLE_ID, pair("Test1", "ValueNew"), pair("Test3", "ValueCompleteNew"));
      verify(client).copyObject(copyObjectRequestCaptor.capture());
      assertAll(
          () ->
              assertThat(headObjectRequestCaptor.getValue().bucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () -> assertThat(headObjectRequestCaptor.getValue().key()).isEqualTo(EXAMPLE_ID),
          () ->
              assertThat(copyObjectRequestCaptor.getValue().sourceBucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () -> assertThat(copyObjectRequestCaptor.getValue().sourceKey()).isEqualTo(EXAMPLE_ID),
          () ->
              assertThat(copyObjectRequestCaptor.getValue().destinationBucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () ->
              assertThat(copyObjectRequestCaptor.getValue().destinationKey()).isEqualTo(EXAMPLE_ID),
          () ->
              assertThat(copyObjectRequestCaptor.getValue().metadata())
                  .containsEntry("Test1", "ValueNew"),
          () ->
              assertThat(copyObjectRequestCaptor.getValue().metadata())
                  .containsEntry("Test2", "ValueOld2"),
          () ->
              assertThat(copyObjectRequestCaptor.getValue().metadata())
                  .containsEntry("Test3", "ValueCompleteNew"),
          () ->
              assertThat(copyObjectRequestCaptor.getValue().metadataDirective())
                  .isEqualTo(REPLACE));
    }

    @Test
    @SneakyThrows
    void shouldFilterValidationStatus() {
      when(client.headObject(headObjectRequestCaptor.capture()))
          .thenReturn(
              HeadObjectResponse.builder()
                  .metadata(Map.of("Test1", "ValueOld", "Test2", "ValueOld2"))
                  .contentLength(100L)
                  .build());
      underTest.updateMetaDataValues(
          EXAMPLE_ID,
          pair(VALIDATION_STATUS, VALID.name()),
          pair(VALIDATION_DESCRIPTION, "SomeDescription"));
      verify(client, times(1)).copyObject(copyObjectRequestCaptor.capture());
      assertThat(copyObjectRequestCaptor.getValue().metadata())
          .doesNotContainKey(VALIDATION_STATUS);
      assertThat(copyObjectRequestCaptor.getValue().metadata()).containsKey(VALIDATION_DESCRIPTION);
    }
  }

  @Nested
  class SignedUrlsTests {

    static Stream<Arguments> shouldCreateCorrectAmountOfSignedUrls() {
      return Stream.of(of(400 * 1024 * 1024, 40), of(10 * 1024 * 1024, 1), of(21 * 1024 * 1024, 3));
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource
    void shouldCreateCorrectAmountOfSignedUrls(double fileSize, int expectedAmount) {
      when(client.headObject((HeadObjectRequest) any()))
          .thenReturn(HeadObjectResponse.builder().contentLength(100L).build());
      PresignedUploadPartRequest request = mock(PresignedUploadPartRequest.class);
      PresignedUploadPartRequest.Builder builder = mock(PresignedUploadPartRequest.Builder.class);
      CreateMultipartUploadResponse multiUploadResponse = mock(CreateMultipartUploadResponse.class);
      when(client.createMultipartUpload((CreateMultipartUploadRequest) any()))
          .thenReturn(multiUploadResponse);
      when(multiUploadResponse.uploadId()).thenReturn(EXAMPLE_ID);
      when(presigner.presignUploadPart((UploadPartPresignRequest) any())).thenReturn(request);
      when(request.toBuilder()).thenReturn(builder);
      when(builder.isBrowserExecutable(true)).thenReturn(builder);
      when(builder.build()).thenReturn(request);
      when(request.url()).thenReturn(new URL("http://localhost:9000"));

      S3Info res = underTest.createSignedUrls(EXAMPLE_ID, fileSize);
      assertThat(res.presignedUrls()).hasSize(expectedAmount);
    }

    @Test
    @SneakyThrows
    void shouldCreateUrlsWithMaxSizeCorrectly() {
      when(client.headObject((HeadObjectRequest) any()))
          .thenReturn(HeadObjectResponse.builder().contentLength(100L).build());
      PresignedUploadPartRequest request = mock(PresignedUploadPartRequest.class);
      PresignedUploadPartRequest.Builder builder = mock(PresignedUploadPartRequest.Builder.class);
      CreateMultipartUploadResponse multiUploadResponse = mock(CreateMultipartUploadResponse.class);
      when(client.createMultipartUpload((CreateMultipartUploadRequest) any()))
          .thenReturn(multiUploadResponse);
      when(multiUploadResponse.uploadId()).thenReturn(EXAMPLE_ID);
      when(presigner.presignUploadPart((UploadPartPresignRequest) any())).thenReturn(request);
      when(request.toBuilder()).thenReturn(builder);
      when(builder.isBrowserExecutable(true)).thenReturn(builder);
      when(builder.build()).thenReturn(request);
      when(request.url()).thenReturn(new URL("http://localhost:9000"));

      S3Info res =
          underTest.createSignedUrls(EXAMPLE_ID, config.getMultipartMaxUploadSizeInBytes());
      assertThat(res.presignedUrls()).hasSize(103);
    }

    @Test
    @SneakyThrows
    void shouldThrowExceptionIfDocumentNotFound() {
      when(client.headObject((HeadObjectRequest) any()))
          .thenThrow(S3Exception.builder().statusCode(404).build());
      IgsServiceException ex =
          assertThrows(
              IgsServiceException.class, () -> underTest.createSignedUrls(EXAMPLE_ID, 100L));
      assertThat(ex.getMessage()).isEqualTo(RESOURCE_NOT_FOUND_ERROR_MSG);
    }

    @Test
    @SneakyThrows
    void shouldThrowExceptionFileSizeToLarge() {
      double fileSizeToLarge = config.getMultipartMaxUploadSizeInBytes() + 1;
      IgsServiceException ex =
          assertThrows(
              IgsServiceException.class,
              () -> underTest.createSignedUrls(EXAMPLE_ID, fileSizeToLarge));
      assertThat(ex.getMessage()).isEqualTo(FILE_SIZE_TO_LARGE_ERROR_MSG);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ValidationTests {

    @Captor ArgumentCaptor<List<Pair>> metaDataCaptor;

    static Stream<Arguments> shouldReturnCorrectValidationInformation() {
      return Stream.of(
          of(Map.of(VALIDATION_STATUS, VALIDATING.name(), VALIDATION_DESCRIPTION, "Ongoing")),
          of(Map.of(VALIDATION_STATUS, VALID.name(), VALIDATION_DESCRIPTION, "IsValid")),
          of(
              Map.of(
                  VALIDATION_STATUS,
                  VALIDATION_NOT_INITIATED.name(),
                  VALIDATION_DESCRIPTION,
                  "NotEvenStarted")),
          of(
              Map.of(
                  VALIDATION_STATUS,
                  VALIDATION_FAILED.name(),
                  VALIDATION_DESCRIPTION,
                  "SomeError")));
    }

    static Stream<Arguments> shouldScipIfAlreadyDone() {
      return Stream.of(
          of(Map.of(VALIDATION_STATUS, VALID.name(), VALIDATION_DESCRIPTION, "IsValid")),
          of(
              Map.of(
                  VALIDATION_STATUS,
                  VALIDATION_FAILED.name(),
                  VALIDATION_DESCRIPTION,
                  "SomeError")));
    }

    @SneakyThrows
    @MethodSource
    @ParameterizedTest
    void shouldScipIfAlreadyDone(Map<String, String> metadata) {
      ValidationInfo res =
          underTest.getStatusOfDocument(
              ValidationInfo.builder()
                  .documentId(EXAMPLE_ID)
                  .status(metadata.get(VALIDATION_STATUS))
                  .message(metadata.get(VALIDATION_DESCRIPTION))
                  .build());
      assertThat(res.getStatus()).isEqualTo(metadata.get(VALIDATION_STATUS));
      assertThat(res.getMessage()).isEqualTo(metadata.get(VALIDATION_DESCRIPTION));
      verify(client, times(0)).headObject(any(HeadObjectRequest.class));
    }

    @SneakyThrows
    @MethodSource
    @ParameterizedTest
    void shouldReturnCorrectValidationInformation(Map<String, String> metadata) {
      when(client.headObject(headObjectRequestCaptor.capture()))
          .thenReturn(HeadObjectResponse.builder().metadata(metadata).build());
      ValidationInfo res =
          underTest.getStatusOfDocument(ValidationInfo.builder().documentId(EXAMPLE_ID).build());
      assertThat(res.getStatus()).isEqualTo(metadata.get(VALIDATION_STATUS));
      assertThat(res.getMessage()).isEqualTo(metadata.get(VALIDATION_DESCRIPTION));
    }

    @Test
    @SneakyThrows
    void shouldRequestTrackerMultipleTimesOnFinalizeValidation() {
      when(tracker.isFinished(EXAMPLE_ID)).thenReturn(false).thenReturn(false).thenReturn(true);
      underTest.finalizeValidation(EXAMPLE_ID);
      verify(tracker, times(3)).isFinished(EXAMPLE_ID);
    }

    @Test
    void shouldAddValidationFailedIfOneValidationMissing() {
      when(tracker.isFinished(EXAMPLE_ID)).thenReturn(false);
      underTest.finalizeValidation(EXAMPLE_ID);
      verify(underTest).emptyFile(EXAMPLE_ID);
      verify(underTest).updateMetaData(eq(EXAMPLE_ID), metaDataCaptor.capture());
      assertThat(metaDataCaptor.getValue())
          .contains(
              pair(VALIDATION_STATUS, VALIDATION_FAILED.name()),
              pair(VALIDATION_DESCRIPTION, INTERNAL_SERVER_ERROR_MESSAGE));
    }

    @Test
    @SneakyThrows
    void shouldCallEmptyFileIfOneVerificationFailed() {
      List<Pair> pairs =
          List.of(
              pair(VALIDATION_STATUS, VALIDATION_FAILED.name()), pair(VALIDATION_DESCRIPTION, ""));
      when(tracker.isFinished(EXAMPLE_ID)).thenReturn(true);
      when(tracker.calculateMetaData(EXAMPLE_ID)).thenReturn(pairs);
      underTest.finalizeValidation(EXAMPLE_ID);
      verify(underTest).emptyFile(EXAMPLE_ID);
    }
  }

  @Nested
  class MultipartUploadCompleteTests {

    @Test
    void shouldCompleteMultipartUpload() {
      CompletedChunk completedChunkOne = new CompletedChunk(1, "eTag1");
      CompletedChunk completedChunkTwo = new CompletedChunk(2, "eTag2");
      CompletedChunk completedChunkThree = new CompletedChunk(3, "eTag3");
      List<CompletedChunk> completedChunks =
          List.of(completedChunkOne, completedChunkTwo, completedChunkThree);

      List<CompletedPart> completedParts =
          completedChunks.stream()
              .map(
                  chunk ->
                      CompletedPart.builder()
                          .partNumber(chunk.partNumber())
                          .eTag(chunk.eTag())
                          .build())
              .toList();

      CompletedMultipartUpload completedMultipartUpload =
          CompletedMultipartUpload.builder().parts(completedParts).build();

      underTest.informUploadComplete(EXAMPLE_ID, "uploadId", completedChunks);

      verify(client).completeMultipartUpload(completeMultipartUploadRequestCaptor.capture());
      assertAll(
          () ->
              assertThat(completeMultipartUploadRequestCaptor.getValue().bucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () ->
              assertThat(completeMultipartUploadRequestCaptor.getValue().key())
                  .isEqualTo(EXAMPLE_ID),
          () ->
              assertThat(completeMultipartUploadRequestCaptor.getValue().uploadId())
                  .isEqualTo("uploadId"),
          () ->
              assertThat(completeMultipartUploadRequestCaptor.getValue().multipartUpload())
                  .isEqualTo(completedMultipartUpload));
    }

    @Test
    void shouldSetMetaTagForUpdatingComplete() {
      List<CompletedChunk> completedChunks = List.of(new CompletedChunk(1, "eTag1"));
      underTest.informUploadComplete(EXAMPLE_ID, "uploadId", completedChunks);
      verify(client).copyObject(copyObjectRequestCaptor.capture());
      assertThat(copyObjectRequestCaptor.getValue().metadata())
          .containsEntry(UPLOAD_STATUS, UPLOAD_STATUS_DONE);
    }

    @Test
    void shouldThrowIgsExceptionIfUploadIdError() {
      String uploadId = "uploadId";
      List<CompletedChunk> completedChunks = List.of(new CompletedChunk(1, "eTag1"));
      when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
          .thenThrow(NoSuchUploadException.builder().statusCode(400).build());
      IgsServiceException exception =
          assertThrows(
              IgsServiceException.class,
              () -> underTest.informUploadComplete(EXAMPLE_ID, uploadId, completedChunks));
      assertThat(exception.getMessage())
          .isEqualTo("Upload with ID " + uploadId + " does not exist");
    }

    @Test
    void shouldThrowIgsExceptionEtagNotKnown() {
      List<CompletedChunk> completedChunks = List.of(new CompletedChunk(1, "eTag1"));
      when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
          .thenThrow(S3Exception.builder().statusCode(400).build());
      IgsServiceException exception =
          assertThrows(
              IgsServiceException.class,
              () -> underTest.informUploadComplete(EXAMPLE_ID, "uploadId", completedChunks));
      assertThat(exception.getMessage()).isEqualTo("E-Tag of the upload is invalid");
    }
  }

  @Nested
  class ValidBucketTests {

    @ParameterizedTest
    @SneakyThrows
    @EnumSource(value = ValidationStatus.class, names = "VALID", mode = EnumSource.Mode.EXCLUDE)
    void shouldNotMoveIfStatusIsNotValid(ValidationStatus status) {
      when(tracker.isFinished(EXAMPLE_ID)).thenReturn(true);
      when(tracker.calculateMetaData(EXAMPLE_ID))
          .thenReturn(
              List.of(
                  pair(VALIDATION_STATUS, status.name()),
                  pair(VALIDATION_DESCRIPTION, "SomeDescription")));
      underTest.finalizeValidation(EXAMPLE_ID);
      verify(client, times(1)).copyObject(copyObjectRequestCaptor.capture());
      assertThat(copyObjectRequestCaptor.getValue().destinationBucket())
          .isEqualTo(config.getUploadBucket().getName());
    }

    @Test
    void shouldMoveIfStatusIsValid() {
      when(tracker.isFinished(EXAMPLE_ID)).thenReturn(true);
      when(tracker.calculateMetaData(EXAMPLE_ID))
          .thenReturn(
              List.of(
                  pair(VALIDATION_STATUS, VALID.name()),
                  pair(VALIDATION_DESCRIPTION, "SomeDescription")));
      when(client.headObject(headObjectRequestCaptor.capture()))
          .thenReturn(
              HeadObjectResponse.builder()
                  .metadata(Map.of(VALIDATION_STATUS, VALID.name()))
                  .build());
      underTest.finalizeValidation(EXAMPLE_ID);
      verify(client, times(2)).copyObject(copyObjectRequestCaptor.capture());
      assertThat(copyObjectRequestCaptor.getAllValues().getLast().destinationBucket())
          .isEqualTo(config.getValidatedBucket().getName());
    }

    @Test
    @SneakyThrows
    void shouldGetBlobFromValidBucket() {

      when(client.headObject((HeadObjectRequest) any()))
          .thenReturn(HeadObjectResponse.builder().contentLength(100L).build());

      when(client.getObject(getObjectRequestCaptor.capture()))
          .thenReturn(
              new ResponseInputStream<>(
                  GetObjectResponse.builder().build(),
                  baseUtil.readFileToInputStream(PATH_TO_FASTQ)));
      InputStream resp = underTest.getBlobFromValidBucket(EXAMPLE_ID);
      assertAll(
          () ->
              assertTrue(
                  baseUtil.streamCompare(resp, baseUtil.readFileToInputStream(PATH_TO_FASTQ))),
          () ->
              assertThat(getObjectRequestCaptor.getValue().bucket())
                  .isEqualTo(config.getValidatedBucket().getName()),
          () -> assertThat(getObjectRequestCaptor.getValue().key()).isEqualTo(EXAMPLE_ID));
    }
  }

  @Nested
  class UtilsTests {

    @Test
    @SneakyThrows
    void shouldReturnFirstBytesCorrectly() {
      when(client.headObject(headObjectRequestCaptor.capture()))
          .thenReturn(HeadObjectResponse.builder().contentLength(100L).build());
      ResponseInputStream response = mock(ResponseInputStream.class);
      when(response.read()).thenReturn(18).thenReturn(34);
      when(client.getObject(getObjectRequestCaptor.capture())).thenReturn(response);
      Pair result = underTest.getFirstBytesOf(EXAMPLE_ID);
      assertAll(
          () -> assertThat(result.first()).isEqualTo("18"),
          () -> assertThat(result.second()).isEqualTo("34"),
          () ->
              assertThat(headObjectRequestCaptor.getValue().bucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () -> assertThat(headObjectRequestCaptor.getValue().key()).isEqualTo(EXAMPLE_ID),
          () -> assertThat(getObjectRequestCaptor.getValue().key()).isEqualTo(EXAMPLE_ID),
          () ->
              assertThat(getObjectRequestCaptor.getValue().bucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () -> assertThat(getObjectRequestCaptor.getValue().range()).isEqualTo("bytes=0-1"));
    }

    @Test
    @SneakyThrows
    void shouldCallEmptyBucketCorrectly() {
      underTest.emptyFile(EXAMPLE_ID);
      verify(client, times(1)).putObject(putObjectCaptor.capture(), requestBodyCaptor.capture());
      assertAll(
          () ->
              assertThat(putObjectCaptor.getValue().bucket())
                  .isEqualTo(config.getUploadBucket().getName()),
          () -> assertThat(putObjectCaptor.getValue().key()).isEqualTo(EXAMPLE_ID),
          () -> assertThat(putObjectCaptor.getValue().metadata()).isEmpty(),
          () ->
              assertThat(requestBodyCaptor.getValue().contentStreamProvider().newStream())
                  .isEmpty());
    }

    @Test
    @SneakyThrows
    void testApplicationReadyEvent() {
      underTest.handleApplicationReady(null);

      ArgumentCaptor<PutBucketLifecycleConfigurationRequest> argsCaptor =
          ArgumentCaptor.forClass(PutBucketLifecycleConfigurationRequest.class);
      verify(client, times(2)).putBucketLifecycleConfiguration(argsCaptor.capture());

      List<PutBucketLifecycleConfigurationRequest> capturedArgs = argsCaptor.getAllValues();
      assertThat(capturedArgs.getFirst()).isNotNull();
      assertThat(capturedArgs.getFirst().bucket()).isEqualTo(config.getUploadBucket().getName());
      assertThat(capturedArgs.getFirst().lifecycleConfiguration().rules()).hasSize(1);
      assertThat(capturedArgs.getFirst().lifecycleConfiguration().rules().getFirst().id())
          .isEqualTo(LIFECYCLE_RULE_ID_TO_VALIDATE);
      assertThat(
              capturedArgs
                  .getFirst()
                  .lifecycleConfiguration()
                  .rules()
                  .getFirst()
                  .expiration()
                  .days())
          .isEqualTo(config.getUploadBucket().getDeletionDeadlineInDays());
      assertThat(capturedArgs.getLast()).isNotNull();
      assertThat(capturedArgs.getLast().bucket()).isEqualTo(config.getValidatedBucket().getName());
      assertThat(capturedArgs.getLast().lifecycleConfiguration().rules()).hasSize(1);
      assertThat(capturedArgs.getLast().lifecycleConfiguration().rules().getLast().id())
          .isEqualTo(LIFECYCLE_RULE_ID_VALID);
      assertThat(
              capturedArgs
                  .getLast()
                  .lifecycleConfiguration()
                  .rules()
                  .getFirst()
                  .expiration()
                  .days())
          .isEqualTo(config.getValidatedBucket().getDeletionDeadlineInDays());
    }
  }
}
