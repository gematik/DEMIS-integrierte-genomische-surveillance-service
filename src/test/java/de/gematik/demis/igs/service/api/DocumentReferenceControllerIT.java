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
import static de.gematik.demis.igs.service.api.DocumentReferenceController.FHIR_DOCUMENT_REFERENCE_BINARY_READ;
import static de.gematik.demis.igs.service.utils.Constants.HASH_METADATA_NAME;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_NOT_INITIATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static util.BaseUtil.PATH_TO_DOCUMENT_REFERENCE_JSON;
import static util.BaseUtil.PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_CONTENT;
import static util.BaseUtil.PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_INVALID_DATE;
import static util.BaseUtil.PATH_TO_DOCUMENT_REFERENCE_XML;
import static util.BaseUtil.PATH_TO_FASTQ;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.igs.service.service.storage.SimpleStorageService;
import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.hamcrest.core.StringStartsWith;
import org.hamcrest.text.MatchesPattern;
import org.hl7.fhir.r4.model.DocumentReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import util.BaseUtil;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DocumentReferenceControllerIT {

  private static final String LOCATION_HEADER = "location";

  private static final Pattern LOCATION_PATTERN =
      Pattern.compile(
          "/fhir/DocumentReference/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private static final int CHARACTERS_IN_LOCATION_BEFORE_ID = 24;

  private static final FhirContext contextR4 = FhirContext.forR4Cached();
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
  @Autowired private MockMvc mockMvc;
  @Autowired private SimpleStorageService storageService;

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
  class CreateDocumentReferenceTests {

    @Test
    @SneakyThrows
    void shouldCreateEmptyDocumentWithHashAndStatusMeta() {
      String documentReferenceRequest =
          testUtil.generateDocumentReferenceJsonForFile(PATH_TO_DOCUMENT_REFERENCE_JSON);

      String responseBody =
          mockMvc
              .perform(
                  post(FHIR_DOCUMENT_REFERENCE_BASE)
                      .contentType(APPLICATION_JSON_VALUE)
                      .content(documentReferenceRequest))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      IParser parser = contextR4.newJsonParser();
      DocumentReference documentReference =
          parser.parseResource(DocumentReference.class, responseBody);
      Map<String, String> meta = storageService.getMetadata(documentReference.getIdPart());
      assertAll(
          () -> assertThat(meta).containsKey(HASH_METADATA_NAME),
          () ->
              assertThat(meta.get(HASH_METADATA_NAME))
                  .isEqualTo(
                      documentReference
                          .getContent()
                          .getFirst()
                          .getAttachment()
                          .getHashElement()
                          .getValueAsString()),
          () -> assertThat(meta).containsKey(VALIDATION_STATUS),
          () -> assertThat(meta.get(VALIDATION_STATUS)).isEqualTo(VALIDATION_NOT_INITIATED.name()));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {APPLICATION_JSON_VALUE, "application/json+fhir", "application/fhir+json"})
    void shouldCreateDocumentReferenceJson(String contentType) throws Exception {
      String documentReferenceRequest =
          testUtil.generateDocumentReferenceJsonForFile(PATH_TO_DOCUMENT_REFERENCE_JSON);
      MockHttpServletResponse response =
          mockMvc
              .perform(
                  post(FHIR_DOCUMENT_REFERENCE_BASE)
                      .contentType(contentType)
                      .content(documentReferenceRequest))
              .andExpect(status().isCreated())
              .andExpect(header().string(LOCATION_HEADER, new MatchesPattern(LOCATION_PATTERN)))
              .andExpect(header().string(CONTENT_TYPE, new StringStartsWith(contentType)))
              .andReturn()
              .getResponse();

      String locationHeader = response.getHeader(DocumentReferenceControllerIT.LOCATION_HEADER);
      String locationHeaderDocumentReferenceId =
          locationHeader.substring(CHARACTERS_IN_LOCATION_BEFORE_ID);

      String responseBody = response.getContentAsString();
      IParser parser = contextR4.newJsonParser();
      DocumentReference documentReference =
          parser.parseResource(DocumentReference.class, responseBody);
      String responseBodyDocumentReferenceId = documentReference.getIdPart();

      assertThat(responseBodyDocumentReferenceId)
          .isNotNull()
          .isEqualTo(locationHeaderDocumentReferenceId);
    }

    @ParameterizedTest
    @ValueSource(strings = {APPLICATION_OCTET_STREAM_VALUE, APPLICATION_PDF_VALUE, ""})
    void shouldReturn415IfWrongContentType(String contentType) throws Exception {
      String documentReferenceRequest = testUtil.readFileToString(PATH_TO_DOCUMENT_REFERENCE_JSON);
      mockMvc
          .perform(
              post(FHIR_DOCUMENT_REFERENCE_BASE)
                  .contentType(contentType)
                  .content(documentReferenceRequest))
          .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldCreateDocumentReferenceXml() throws Exception {
      String documentReferenceRequest =
          testUtil.generateFastQDocumentReferenceXmlString(PATH_TO_DOCUMENT_REFERENCE_XML);
      MockHttpServletResponse response =
          mockMvc
              .perform(
                  post(FHIR_DOCUMENT_REFERENCE_BASE)
                      .contentType(APPLICATION_XML_VALUE)
                      .content(documentReferenceRequest))
              .andExpect(status().isCreated())
              .andExpect(header().string(LOCATION_HEADER, new MatchesPattern(LOCATION_PATTERN)))
              .andExpect(header().string(CONTENT_TYPE, new StringStartsWith(APPLICATION_XML_VALUE)))
              .andReturn()
              .getResponse();

      String locationHeader = response.getHeader(DocumentReferenceControllerIT.LOCATION_HEADER);
      String locationHeaderDocumentReferenceId =
          locationHeader.substring(CHARACTERS_IN_LOCATION_BEFORE_ID);

      String responseBody = response.getContentAsString();
      IParser parser = contextR4.newXmlParser();
      DocumentReference documentReference =
          parser.parseResource(DocumentReference.class, responseBody);
      String responseBodyDocumentReferenceId = documentReference.getIdPart();

      assertThat(responseBodyDocumentReferenceId)
          .isNotNull()
          .isEqualTo(locationHeaderDocumentReferenceId);
    }

    @Test
    void shouldReturnBadRequestForNoContentDocumentReference() throws Exception {
      String documentReferenceRequest =
          testUtil.readFileToString(PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_CONTENT);

      mockMvc
          .perform(
              post(FHIR_DOCUMENT_REFERENCE_BASE)
                  .contentType(APPLICATION_JSON)
                  .content(documentReferenceRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("DocumentReference content is not present"));
    }

    @Test
    void shouldReturnBadRequestForBadDateDocumentReference() throws Exception {
      String documentReferenceRequest =
          testUtil.readFileToString(PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_INVALID_DATE);

      mockMvc
          .perform(
              post(FHIR_DOCUMENT_REFERENCE_BASE)
                  .contentType(APPLICATION_JSON)
                  .content(documentReferenceRequest))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class DownloadBinaryTests {

    @Autowired S3Client s3;

    @SneakyThrows
    void uploadFileAtDocumentIdWithStatusToBucket(
        String filePath, String documentId, String status, String bucketName) {
      try {
        s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
      } catch (S3Exception e) {
        if (e.statusCode() == 404) {
          s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } else {
          throw e;
        }
      }
      s3.putObject(
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(documentId)
              .metadata(
                  Map.of(
                      HASH_METADATA_NAME,
                      testUtil.calcHashOnFile(PATH_TO_FASTQ),
                      UPLOAD_STATUS,
                      status))
              .build(),
          RequestBody.fromFile(new File(testUtil.getFilePath(filePath))));
    }

    @Test
    @SneakyThrows
    void shouldBeAbleToDownloadBinary() {
      storageService.putBlob(
          DOCUMENT_ID,
          Map.of(HASH_METADATA_NAME, testUtil.calcHashOnFile(PATH_TO_FASTQ)),
          testUtil.readFileToInputStream(PATH_TO_FASTQ));
      uploadFileAtDocumentIdWithStatusToBucket(
          PATH_TO_FASTQ, DOCUMENT_ID, VALID.name(), "sequence-data-valid");
      mockMvc
          .perform(
              get(FHIR_DOCUMENT_REFERENCE_BINARY_READ.replace("{documentId}", DOCUMENT_ID))
                  .param("path", "DocumentReference.content.attachment"))
          .andExpect(status().isOk())
          .andExpect(content().bytes(testUtil.readFileToByteArray(PATH_TO_FASTQ)))
          .andExpect(
              header()
                  .string(
                      "Content-Disposition", "attachment; filename=\"" + DOCUMENT_ID + ".bin\""))
          .andReturn();
    }

    @Test
    @SneakyThrows
    void shouldGetNotFoundResponseIfDocumentNotExist() {
      uploadFileAtDocumentIdWithStatusToBucket(
          PATH_TO_FASTQ, DOCUMENT_ID, VALID.name(), "sequence-data");
      mockMvc
          .perform(
              get(FHIR_DOCUMENT_REFERENCE_BINARY_READ.replace(
                      "{documentId}", NOT_EXISTING_DOCUMENT_ID))
                  .param("path", "DocumentReference.content.attachment")
                  .contentType(APPLICATION_OCTET_STREAM_VALUE)
                  .content(testUtil.readFileToByteArray(PATH_TO_FASTQ)))
          .andExpect(status().isNotFound());
    }

    @Test
    @SneakyThrows
    void shouldReturn400IfPathIsWrong() {
      mockMvc
          .perform(
              get(FHIR_DOCUMENT_REFERENCE_BINARY_READ.replace("{documentId}", DOCUMENT_ID))
                  .param("path", "WRONG PATH!"))
          .andExpect(status().isBadRequest());
    }
  }
}
