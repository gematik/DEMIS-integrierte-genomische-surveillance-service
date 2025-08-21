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

import static de.gematik.demis.igs.service.api.S3Controller.S3_UPLOAD_VALIDATE;
import static de.gematik.demis.igs.service.service.validation.SequenceValidatorService.ERROR_MESSAGE_FASTQ_SEND_BY_FASTA_USER;
import static de.gematik.demis.igs.service.utils.Constants.HASH_METADATA_NAME;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS_DONE;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_DESCRIPTION;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;
import static util.BaseUtil.PATH_TO_FASTA;
import static util.BaseUtil.PATH_TO_FASTA_GZIP;
import static util.BaseUtil.PATH_TO_FASTA_WITH_PATHOGEN_LONG;
import static util.BaseUtil.PATH_TO_FASTA_WITH_PATHOGEN_N51;
import static util.BaseUtil.PATH_TO_FASTA_WITH_PATHOGEN_SHORT;
import static util.BaseUtil.PATH_TO_FASTA_WITH_PATHOGEN_STRICT_VALID;
import static util.BaseUtil.PATH_TO_FASTQ;
import static util.BaseUtil.PATH_TO_FASTQ_GZIP;
import static util.BaseUtil.TOKEN_FAST_A;
import static util.BaseUtil.TOKEN_NRZ;

import de.gematik.demis.igs.service.service.storage.SimpleStorageService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import util.BaseUtil;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("pathogen")
class S3ControllerPandemicIT {

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

  private void startValidation(String documentId) throws Exception {
    startValidation(documentId, false);
  }

  private void startValidation(String documentId, boolean fastAOnlyRule) throws Exception {
    mockMvc
        .perform(
            post(S3_UPLOAD_VALIDATE.replace("{documentId}", documentId))
                .header("Authorization", fastAOnlyRule ? TOKEN_FAST_A : TOKEN_NRZ))
        .andExpect(status().isNoContent());
  }

  @Nested
  class NotFastAOnly {

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({
      PATH_TO_FASTA_WITH_PATHOGEN_SHORT,
      PATH_TO_FASTA_WITH_PATHOGEN_LONG,
      PATH_TO_FASTA_WITH_PATHOGEN_N51
    })
    void shouldReturnError400IfPathogenRulesViolated(String path) {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(
              UPLOAD_STATUS, UPLOAD_STATUS_DONE, HASH_METADATA_NAME, testUtil.calcHashOnFile(path)),
          testUtil.readFileToInputStream(path));
      startValidation(documentId);
      await()
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(5, TimeUnit.SECONDS)
          .failFast(
              "Wrong final status",
              () ->
                  storageService
                      .getMetadata(documentId)
                      .get(VALIDATION_STATUS)
                      .equals(VALID.name()))
          .until(
              () ->
                  storageService
                      .getMetadata(documentId)
                      .get(VALIDATION_STATUS)
                      .equals(VALIDATION_FAILED.name()));
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({PATH_TO_FASTA, PATH_TO_FASTQ, PATH_TO_FASTA_GZIP, PATH_TO_FASTQ_GZIP})
    void shouldValidateSuccessfully(String path) {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(
              UPLOAD_STATUS, UPLOAD_STATUS_DONE, HASH_METADATA_NAME, testUtil.calcHashOnFile(path)),
          testUtil.readFileToInputStream(path));
      startValidation(documentId);
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
  }

  @Nested
  class FastAOnly {

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({
      PATH_TO_FASTA_WITH_PATHOGEN_SHORT,
      PATH_TO_FASTA_WITH_PATHOGEN_LONG,
      PATH_TO_FASTA_WITH_PATHOGEN_N51,
      PATH_TO_FASTQ,
      PATH_TO_FASTA
    })
    void shouldReturnError400IfPathogenRulesViolatedOnStrict(String path) {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(
              UPLOAD_STATUS, UPLOAD_STATUS_DONE, HASH_METADATA_NAME, testUtil.calcHashOnFile(path)),
          testUtil.readFileToInputStream(path));
      startValidation(documentId, true);
      await()
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(5, TimeUnit.SECONDS)
          .failFast(
              "Wrong final status",
              () ->
                  storageService
                      .getMetadata(documentId)
                      .get(VALIDATION_STATUS)
                      .equals(VALID.name()))
          .until(
              () ->
                  storageService
                      .getMetadata(documentId)
                      .get(VALIDATION_STATUS)
                      .equals(VALIDATION_FAILED.name()));
    }

    @Test
    @SneakyThrows
    void shouldValidateSuccessfullyStrict() {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(
              UPLOAD_STATUS,
              UPLOAD_STATUS_DONE,
              HASH_METADATA_NAME,
              testUtil.calcHashOnFile(PATH_TO_FASTA_WITH_PATHOGEN_STRICT_VALID)),
          testUtil.readFileToInputStream(PATH_TO_FASTA_WITH_PATHOGEN_STRICT_VALID));
      startValidation(documentId, true);
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

    @Test
    @SneakyThrows
    void shouldReturn400IfFastQUpload() {
      String documentId = UUID.randomUUID().toString();
      storageService.putBlob(
          documentId,
          Map.of(
              UPLOAD_STATUS,
              UPLOAD_STATUS_DONE,
              HASH_METADATA_NAME,
              testUtil.calcHashOnFile(PATH_TO_FASTQ)),
          testUtil.readFileToInputStream(PATH_TO_FASTQ));
      startValidation(documentId, true);
      await()
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(1, TimeUnit.MINUTES)
          .failFast(
              "Wrong final status",
              () ->
                  storageService
                      .getMetadata(documentId)
                      .get(VALIDATION_STATUS)
                      .equals(VALID.name()))
          .until(
              () -> {
                Map<String, String> metadata = storageService.getMetadata(documentId);
                return metadata.get(VALIDATION_STATUS).equals(VALIDATION_FAILED.name())
                    && metadata
                        .get(VALIDATION_DESCRIPTION)
                        .contains(ERROR_MESSAGE_FASTQ_SEND_BY_FASTA_USER);
              });
    }
  }
}
