package de.gematik.demis.igs.service.api;

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

import static de.gematik.demis.igs.service.api.DocumentReferenceController.FHIR_DOCUMENT_REFERENCE_BASE;
import static de.gematik.demis.igs.service.api.S3Controller.S3_UPLOAD_FINISH_UPLOAD;
import static de.gematik.demis.igs.service.api.S3Controller.S3_UPLOAD_INFO;
import static de.gematik.demis.igs.service.api.S3Controller.S3_UPLOAD_VALIDATE;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.DOUBLE_HEADER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.service.validation.FastQValidator.LINE_LENGTH_DIFFER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.utils.Constants.HASH_METADATA_NAME;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS_DONE;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_DESCRIPTION;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_NOT_INITIATED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.FILE_SIZE_TO_LARGE_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.HASH_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INVALID_COMPRESSED_FILE_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INVALID_DOCUMENT_TYPE_ERROR_MSG;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;
import static util.BaseUtil.PATH_TO_FASTA;
import static util.BaseUtil.PATH_TO_FASTA_GZIP;
import static util.BaseUtil.PATH_TO_FASTA_INVALID;
import static util.BaseUtil.PATH_TO_FASTA_ONLY_N;
import static util.BaseUtil.PATH_TO_FASTA_WITH_PATHOGEN_LONG;
import static util.BaseUtil.PATH_TO_FASTA_WITH_PATHOGEN_N51;
import static util.BaseUtil.PATH_TO_FASTA_WITH_PATHOGEN_SHORT;
import static util.BaseUtil.PATH_TO_FASTA_ZIP;
import static util.BaseUtil.PATH_TO_FASTQ;
import static util.BaseUtil.PATH_TO_FASTQ_GZIP;
import static util.BaseUtil.PATH_TO_FASTQ_INVALID;
import static util.BaseUtil.PATH_TO_GZIP_INVALID;
import static util.BaseUtil.TOKEN_NRZ;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.igs.service.api.model.CompletedChunk;
import de.gematik.demis.igs.service.api.model.MultipartUploadComplete;
import de.gematik.demis.igs.service.api.model.S3Info;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.storage.SimpleStorageService;
import de.gematik.demis.igs.service.service.storage.SimpleStorageServiceConfiguration;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import util.BaseUtil;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class S3ControllerIT {

  private static final String DOCUMENT_ID = "someId";
  private static final String NOT_EXISTING_DOCUMENT_ID = "NotExisting";
  private static final String MINIO_ROOT_USER = "MY_ACCESS_KEY";
  private static final String MINIO_ROOT_PASSWORD = "VERY_VERY_SECURE_PASSWORD";

  @Container
  private static final GenericContainer<?> minioContainer =
      new GenericContainer<>("minio/minio")
          .withExposedPorts(9000)
          .withEnv("MINIO_ROOT_USER", MINIO_ROOT_USER)
          .withEnv("MINIO_ROOT_PASSWORD", MINIO_ROOT_PASSWORD)
          .waitingFor(new HttpWaitStrategy().forPath("/minio/health/live"))
          .withCommand("server /mnt/data");

  private final BaseUtil testUtil = new BaseUtil();

  @Value("${igs.context-path}")
  private String contextPath;

  @Autowired private SimpleStorageService storageService;
  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void minioProperties(DynamicPropertyRegistry registry) {
    String storageUrl =
        "http://" + minioContainer.getHost() + ":" + minioContainer.getFirstMappedPort();
    registry.add("simple.storage.service.url", () -> storageUrl);
    registry.add("simple.storage.service.cluster-url", () -> storageUrl);
    registry.add("simple.storage.service.access-key", () -> MINIO_ROOT_USER);
    registry.add("simple.storage.service.secret-key", () -> MINIO_ROOT_PASSWORD);
  }

  @Nested
  class GetSignedUrlTests {

    public static final String MB400 = String.valueOf(400 * 1024 * 1024);
    @Autowired SimpleStorageService storageService;
    @Autowired private SimpleStorageServiceConfiguration config;

    @Test
    @SneakyThrows
    void shouldGetUrlSuccessfully() {
      storageService.putBlob(DOCUMENT_ID, Map.of(), InputStream.nullInputStream());
      mockMvc
          .perform(
              get(S3_UPLOAD_INFO.replace("{documentId}", DOCUMENT_ID))
                  .queryParam("fileSize", MB400))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.uploadId").isNotEmpty())
          .andExpect(jsonPath("$.presignedUrls").isArray())
          .andExpect(jsonPath("$.presignedUrls").value(hasSize(40)))
          .andExpect(jsonPath("$.presignedUrls[0]").value(startsWith(config.getUrl())))
          .andExpect(
              jsonPath("$.presignedUrls[0]")
                  .value(containsString(config.getUploadBucket().getName())))
          .andExpect(jsonPath("$.partSizeBytes").value(10 * 1024 * 1024));
    }

    @Test
    @SneakyThrows
    void shouldKeepMetaDataAfterGettingPresignedUrls() {
      Map<String, String> meta = new HashMap<>();
      meta.put(HASH_METADATA_NAME, "SomeHash");
      meta.put(VALIDATION_STATUS, VALIDATION_NOT_INITIATED.name());
      storageService.putBlob(DOCUMENT_ID, meta, InputStream.nullInputStream());
      mockMvc.perform(
          get(S3_UPLOAD_INFO.replace("{documentId}", DOCUMENT_ID)).queryParam("fileSize", MB400));
      Map<String, String> newMeta = storageService.getMetadata(DOCUMENT_ID);
      assertThat(newMeta).isEqualTo(meta);
    }

    @Test
    @SneakyThrows
    void shouldGet404IfDocumentNotExisting() {
      mockMvc
          .perform(
              get(S3_UPLOAD_INFO.replace("{documentId}", NOT_EXISTING_DOCUMENT_ID))
                  .queryParam("fileSize", "100"))
          .andExpect(status().isNotFound());
    }

    @Test
    @SneakyThrows
    void shouldGet400IfNoFileSizeDelivered() {
      mockMvc
          .perform(get(S3_UPLOAD_INFO.replace("{documentId}", NOT_EXISTING_DOCUMENT_ID)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @SneakyThrows
    void shouldAllowMaxFileSize() {
      storageService.putBlob(DOCUMENT_ID, Map.of(), InputStream.nullInputStream());
      mockMvc
          .perform(
              get(S3_UPLOAD_INFO.replace("{documentId}", DOCUMENT_ID))
                  .queryParam(
                      "fileSize", String.valueOf(config.getMultipartMaxUploadSizeInBytes())))
          .andExpect(status().isOk());
    }

    @Test
    @SneakyThrows
    void shouldReturn400IfFileSizeIsToLarge() {
      storageService.putBlob(DOCUMENT_ID, Map.of(), InputStream.nullInputStream());
      mockMvc
          .perform(
              get(S3_UPLOAD_INFO.replace("{documentId}", DOCUMENT_ID))
                  .queryParam(
                      "fileSize", String.valueOf(config.getMultipartMaxUploadSizeInBytes() + 1)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(FILE_SIZE_TO_LARGE_ERROR_MSG));
    }
  }

  @Nested
  class ValidateSequencesTests {

    @Autowired private SimpleStorageService storageService;

    static Stream<Arguments> shouldDeleteFileIfNotValidAndDeliverMessage() {
      return Stream.of(
          Arguments.of(
              "Invalid fastQ",
              PATH_TO_FASTQ_INVALID,
              PATH_TO_FASTQ_INVALID,
              format(LINE_LENGTH_DIFFER_ERROR_MESSAGE, 2, 4)),
          Arguments.of(
              "Invalid fastA",
              PATH_TO_FASTA_INVALID,
              PATH_TO_FASTA_INVALID,
              DOUBLE_HEADER_ERROR_MESSAGE),
          Arguments.of(
              "Invalid format",
              PATH_TO_FASTA_ZIP,
              PATH_TO_FASTA_ZIP,
              INVALID_DOCUMENT_TYPE_ERROR_MSG),
          Arguments.of(
              "Invalid gzip",
              PATH_TO_GZIP_INVALID,
              PATH_TO_GZIP_INVALID,
              INVALID_COMPRESSED_FILE_ERROR_MSG),
          Arguments.of("Invalid hash", PATH_TO_FASTA, PATH_TO_FASTA_GZIP, HASH_ERROR_MSG));
    }

    @Test
    @SneakyThrows
    void shouldReturn404IfDocumentIdNotExisting() {
      startValidation(NOT_EXISTING_DOCUMENT_ID).andExpect(status().isNotFound());
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource
    void shouldDeleteFileIfNotValidAndDeliverMessage(
        String name, String path, String calcHashPath, String errorMessage) {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(
              UPLOAD_STATUS,
              UPLOAD_STATUS_DONE,
              HASH_METADATA_NAME,
              testUtil.calcHashOnFile(calcHashPath)),
          testUtil.readFileToInputStream(path));
      startValidation(documentId).andExpect(status().isNoContent());
      await()
          .atMost(ofSeconds(120))
          .pollInterval(ofSeconds(1))
          .until(() -> storageService.getMetadata(documentId).containsKey(VALIDATION_DESCRIPTION));
      Map<String, String> metaData = storageService.getMetadata(documentId);
      assertThat(metaData).containsEntry(VALIDATION_DESCRIPTION, errorMessage);
      IgsServiceException ex =
          assertThrows(
              "File should be empty and therefore throw an exception",
              IgsServiceException.class,
              () -> storageService.getBlob(documentId));
      assertThat(ex.getMessage()).contains("Requested resource not found");
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({
      PATH_TO_FASTA,
      PATH_TO_FASTQ,
      PATH_TO_FASTA_GZIP,
      PATH_TO_FASTQ_GZIP,
      PATH_TO_FASTA_ONLY_N,
      PATH_TO_FASTA_WITH_PATHOGEN_SHORT,
      PATH_TO_FASTA_WITH_PATHOGEN_LONG,
      PATH_TO_FASTA_WITH_PATHOGEN_N51
    })
    void shouldValidateSuccessfully(String path) {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(
              UPLOAD_STATUS, UPLOAD_STATUS_DONE, HASH_METADATA_NAME, testUtil.calcHashOnFile(path)),
          testUtil.readFileToInputStream(path));
      startValidation(documentId).andExpect(status().isNoContent());
      await()
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(1, TimeUnit.MINUTES)
          .failFast(
              "Wrong final status",
              () ->
                  storageService
                      .getMetadata(documentId)
                      .get(VALIDATION_STATUS)
                      .equals(VALIDATION_FAILED.name()))
          .until(
              () ->
                  storageService
                      .getMetadata(documentId)
                      .get(VALIDATION_STATUS)
                      .equals(VALID.name()));
    }

    @SneakyThrows
    @Test
    void shouldReturnError400IfUploadNotFinished() {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(HASH_METADATA_NAME, testUtil.calcHashOnFile(PATH_TO_FASTA)),
          testUtil.readFileToInputStream(PATH_TO_FASTA));
      startValidation(documentId)
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.detail")
                  .value("Der Upload des angefragen Dokuments ist noch nicht abgeschlossen."));
    }

    private ResultActions startValidation(String documentId) throws Exception {
      return mockMvc.perform(
          post(S3_UPLOAD_VALIDATE.replace("{documentId}", documentId))
              .header("Authorization", TOKEN_NRZ));
    }
  }

  @Nested
  class InformMultipartUploadCompleteTests {

    private static final String LOCATION_PREFIX = "/fhir/DocumentReference/";
    private static final String PATH_TO_SAMPLE_DATA =
        "src/test/resources/sampleSequenceData/Sample.fa";

    @Test
    @SneakyThrows
    void shouldInformMultipartUploadComplete() {
      File sampleData = new File(PATH_TO_SAMPLE_DATA);
      String documentId = createDocumentReference();
      S3Info s3Info = createS3Info(documentId, (int) sampleData.length());
      List<CompletedChunk> completedChunks = performMultipartUpload(s3Info, PATH_TO_SAMPLE_DATA);
      MultipartUploadComplete multipartUploadComplete =
          new MultipartUploadComplete(s3Info.uploadId(), completedChunks);
      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = objectMapper.writeValueAsString(multipartUploadComplete);

      mockMvc
          .perform(
              post(S3_UPLOAD_FINISH_UPLOAD.replace("{documentId}", documentId))
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isNoContent());
    }

    @Test
    @SneakyThrows
    void shouldReturnBadRequestOnNoUploadId() {
      storageService.putBlob(DOCUMENT_ID, Map.of(), InputStream.nullInputStream());
      MultipartUploadComplete multipartUploadComplete =
          new MultipartUploadComplete("", determineCompletedChunks());
      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = objectMapper.writeValueAsString(multipartUploadComplete);
      mockMvc
          .perform(
              post(S3_UPLOAD_FINISH_UPLOAD.replace("{documentId}", DOCUMENT_ID))
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest());
    }

    @Test
    @SneakyThrows
    void shouldReturnBadRequestOnNoCompletedChunks() {
      storageService.putBlob(DOCUMENT_ID, Map.of(), InputStream.nullInputStream());
      MultipartUploadComplete multipartUploadComplete =
          new MultipartUploadComplete("uploadId", null);
      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = objectMapper.writeValueAsString(multipartUploadComplete);
      mockMvc
          .perform(
              post(S3_UPLOAD_FINISH_UPLOAD.replace("{documentId}", DOCUMENT_ID))
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest());
    }

    @Test
    @SneakyThrows
    void shouldReturnBadRequestOnInvalidDocumentId() {
      File sampleData = new File(PATH_TO_SAMPLE_DATA);
      String documentId = createDocumentReference();
      S3Info s3Info = createS3Info(documentId, (int) sampleData.length());
      List<CompletedChunk> completedChunks = performMultipartUpload(s3Info, PATH_TO_SAMPLE_DATA);
      MultipartUploadComplete multipartUploadComplete =
          new MultipartUploadComplete(s3Info.uploadId(), completedChunks);
      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = objectMapper.writeValueAsString(multipartUploadComplete);

      mockMvc
          .perform(
              post(S3_UPLOAD_FINISH_UPLOAD.replace("{documentId}", "invalidId"))
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isNotFound());
    }

    @Test
    @SneakyThrows
    void shouldReturnBadRequestOnFailure() {
      File sampleData = new File(PATH_TO_SAMPLE_DATA);
      String documentId = createDocumentReference();
      S3Info s3Info = createS3Info(documentId, (int) sampleData.length());
      performMultipartUpload(s3Info, PATH_TO_SAMPLE_DATA);
      MultipartUploadComplete multipartUploadComplete =
          new MultipartUploadComplete(s3Info.uploadId(), determineCompletedChunks());
      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = objectMapper.writeValueAsString(multipartUploadComplete);

      mockMvc
          .perform(
              post(S3_UPLOAD_FINISH_UPLOAD.replace("{documentId}", documentId))
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("E-Tag of the upload is invalid"))
          .andExpect(jsonPath("$.errorCode").value("INVALID_UPLOAD"));
    }

    @SneakyThrows
    private String createDocumentReference() {
      String documentReference = testUtil.generateDocumentReferenceJsonForFile(PATH_TO_FASTA);
      MvcResult result =
          mockMvc
              .perform(
                  post(contextPath + FHIR_DOCUMENT_REFERENCE_BASE)
                      .contentType(APPLICATION_JSON)
                      .content(documentReference))
              .andReturn();
      String locationHeader = result.getResponse().getHeader("location");
      return locationHeader.substring(LOCATION_PREFIX.length());
    }

    @SneakyThrows
    private S3Info createS3Info(String documentId, int filesSize) {
      MvcResult result =
          mockMvc
              .perform(
                  get(S3_UPLOAD_INFO.replace("{documentId}", documentId))
                      .queryParam("fileSize", "" + filesSize))
              .andReturn();
      String s3Info = result.getResponse().getContentAsString();
      ObjectMapper objectMapper = new ObjectMapper();
      return objectMapper.readValue(s3Info, S3Info.class);
    }

    @SneakyThrows
    private List<CompletedChunk> performMultipartUpload(S3Info s3Info, String pathToFile) {
      List<CompletedChunk> completedChunks;

      File filePart = new File(pathToFile);
      // Our test data is smaller than 10 MB so we only have one chunk
      URL presignedUrl = new URL(s3Info.presignedUrls().getFirst());

      try (FileInputStream inputStream = new FileInputStream(filePart)) {
        HttpURLConnection connection = openHttpConnection(presignedUrl, (int) filePart.length());
        try (OutputStream outputStream = connection.getOutputStream()) {
          byte[] buffer = new byte[(int) filePart.length()];
          int bytesRead;
          while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
          }
        }
        completedChunks = handleResponse(connection);
      }
      return completedChunks;
    }

    private HttpURLConnection openHttpConnection(URL presignedUrl, int fileSize)
        throws IOException {
      // the proxy is used to make sure that fhir-test.localhost will relove to the right host
      Proxy proxy =
          new Proxy(
              Proxy.Type.HTTP,
              new InetSocketAddress(minioContainer.getHost(), minioContainer.getFirstMappedPort()));
      HttpURLConnection connection = (HttpURLConnection) presignedUrl.openConnection(proxy);
      connection.setDoOutput(true);
      connection.setRequestMethod("PUT");
      connection.addRequestProperty("Content-Type", "application/octet-stream");
      connection.addRequestProperty("Content-Length", "" + fileSize);
      return connection;
    }

    private List<CompletedChunk> handleResponse(HttpURLConnection connection) throws IOException {
      List<CompletedChunk> completedChunks = new ArrayList<>();
      int responseCode = connection.getResponseCode();
      if (responseCode == HttpStatus.OK.value()) {
        String eTag = connection.getHeaderField("ETag");
        completedChunks.add(new CompletedChunk(1, eTag));
      } else {
        throw new RuntimeException("Failed to upload part. Response code: " + responseCode);
      }
      return completedChunks;
    }

    private List<CompletedChunk> determineCompletedChunks() {
      return List.of(
          new CompletedChunk(1, "eTag1"),
          new CompletedChunk(2, "eTag2"),
          new CompletedChunk(3, "eTag3"));
    }
  }
}
