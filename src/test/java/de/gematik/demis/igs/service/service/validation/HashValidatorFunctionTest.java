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
 * #L%
 */

import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.HASH_ERROR_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static util.BaseUtil.PATH_TO_FASTQ;

import de.gematik.demis.igs.service.utils.Constants.ValidationStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import util.BaseUtil;

@ExtendWith(MockitoExtension.class)
class HashValidatorFunctionTest {

  public static final String EXAMPLE_ID = "SomeId";
  BaseUtil testUtil = new BaseUtil();
  @Mock ValidationTracker tracker;
  @Captor ArgumentCaptor<ValidationStatus> statusCaptor;
  @Captor ArgumentCaptor<String> msgCaptor;

  @Test
  @SneakyThrows
  void shouldValidateCorrectlyAndWriteToOutput() {
    InputStream input = testUtil.readFileToInputStream(PATH_TO_FASTQ);
    String hash = testUtil.calcHashOnFile(PATH_TO_FASTQ);
    OutputStream out = new ByteArrayOutputStream();
    new HashValidatorFunction(hash, EXAMPLE_ID, tracker).apply(input, out);
    assertTrue(
        testUtil.streamCompare(
            testUtil.readFileToInputStream(PATH_TO_FASTQ),
            new ByteArrayInputStream(out.toString().getBytes())));
  }

  @Test
  @SneakyThrows
  void shouldCallValidationTrackerAccordinglyOnFail() {
    InputStream input = testUtil.readFileToInputStream(PATH_TO_FASTQ);
    String hash = "InvalidHash";
    OutputStream out = new ByteArrayOutputStream();
    new HashValidatorFunction(hash, EXAMPLE_ID, tracker).apply(input, out);
    assertAll(
        () -> verify(tracker, times(1)).updateHashStatus(eq(EXAMPLE_ID), statusCaptor.capture()),
        () ->
            verify(tracker, times(1))
                .updateHashStatus(eq(EXAMPLE_ID), statusCaptor.capture(), msgCaptor.capture()),
        () ->
            assertThat(statusCaptor.getAllValues()).containsExactly(VALIDATING, VALIDATION_FAILED),
        () -> assertThat(msgCaptor.getValue()).isEqualTo(HASH_ERROR_MSG));
  }

  @Test
  @SneakyThrows
  void shouldCallValidationTrackerAccordinglyOnSuccess() {
    InputStream input = testUtil.readFileToInputStream(PATH_TO_FASTQ);
    String hash = testUtil.calcHashOnFile(PATH_TO_FASTQ);
    OutputStream out = new ByteArrayOutputStream();
    new HashValidatorFunction(hash, EXAMPLE_ID, tracker).apply(input, out);
    assertAll(
        () -> verify(tracker, times(2)).updateHashStatus(eq(EXAMPLE_ID), statusCaptor.capture()),
        () -> assertThat(statusCaptor.getAllValues()).containsExactly(VALIDATING, VALID));
  }
}
