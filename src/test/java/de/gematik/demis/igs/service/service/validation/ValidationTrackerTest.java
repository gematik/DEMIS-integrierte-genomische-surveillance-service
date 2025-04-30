package de.gematik.demis.igs.service.service.validation;

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

import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_DESCRIPTION;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import de.gematik.demis.igs.service.utils.Pair;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationTrackerTest {

  public static final String DOCUMENT_ID = "documentId";
  private ValidationTracker underTest;

  @BeforeEach
  void setUp() {
    underTest = new ValidationTracker();
    underTest.init(DOCUMENT_ID);
  }

  @Test
  @SneakyThrows
  void shouldReturnFinishedOnlyIfAllDone() {
    assertThat(underTest.isFinished(DOCUMENT_ID)).isFalse();
    underTest.updateValidationStatus(DOCUMENT_ID, VALIDATION_FAILED);
    assertThat(underTest.isFinished(DOCUMENT_ID)).isFalse();
    underTest.updateGzipStatus(DOCUMENT_ID, VALIDATION_FAILED);
    assertThat(underTest.isFinished(DOCUMENT_ID)).isFalse();
    underTest.updateHashStatus(DOCUMENT_ID, VALIDATION_FAILED);
    assertThat(underTest.isFinished(DOCUMENT_ID)).isTrue();
  }

  @Test
  @SneakyThrows
  void shouldNotThrowAnyExceptionIfIdNotExisting() {
    assertDoesNotThrow(() -> underTest.updateGzipStatus("notExisting", VALIDATION_FAILED));
    assertDoesNotThrow(() -> underTest.updateValidationStatus("notExisting", VALIDATION_FAILED));
    assertDoesNotThrow(() -> underTest.updateHashStatus("notExisting", VALIDATION_FAILED));
    assertDoesNotThrow(() -> underTest.updateGzipStatus("notExisting", VALIDATION_FAILED, "Error"));
    assertDoesNotThrow(
        () -> underTest.updateValidationStatus("notExisting", VALIDATION_FAILED, "Error"));
    assertDoesNotThrow(() -> underTest.updateHashStatus("notExisting", VALIDATION_FAILED, "Error"));
  }

  @Test
  @SneakyThrows
  void shouldReturnFalseIfDocumentIdNotExist() {
    assertThat(underTest.isFinished("notExisting")).isFalse();
  }

  @Test
  @SneakyThrows
  void shouldReturnEmptyListWhenDocumentIdNotExist() {
    List<Pair> metaData = underTest.calculateMetaData("notExisting");
    assertThat(metaData).isEmpty();
  }

  @Test
  @SneakyThrows
  void shouldSetErrorOnlyOnce() {
    underTest.updateGzipStatus(DOCUMENT_ID, VALIDATION_FAILED, "error");
    underTest.updateValidationStatus(DOCUMENT_ID, VALIDATION_FAILED, "otherError");
    List<Pair> metaData = underTest.calculateMetaData(DOCUMENT_ID);
    assertThat(metaData).hasSize(2);
    assertThat(
            metaData.stream()
                .filter(p -> p.first().equals(VALIDATION_DESCRIPTION))
                .findFirst()
                .orElseThrow()
                .second())
        .isEqualTo("error");
  }

  @Test
  @SneakyThrows
  void shouldContainValidationStatusFailedIfOneValidationIsOnFail() {
    underTest.updateGzipStatus(DOCUMENT_ID, VALIDATION_FAILED, "error");
    underTest.updateValidationStatus(DOCUMENT_ID, VALID);
    underTest.updateHashStatus(DOCUMENT_ID, VALID);

    List<Pair> metaData = underTest.calculateMetaData(DOCUMENT_ID);
    assertThat(metaData).hasSize(2);
    assertThat(
            metaData.stream()
                .filter(p -> p.first().equals(VALIDATION_STATUS))
                .findFirst()
                .orElseThrow()
                .second())
        .isEqualTo("VALIDATION_FAILED");
    assertThat(
            metaData.stream()
                .filter(p -> p.first().equals(VALIDATION_DESCRIPTION))
                .findFirst()
                .orElseThrow()
                .second())
        .isEqualTo("error");
  }
}
