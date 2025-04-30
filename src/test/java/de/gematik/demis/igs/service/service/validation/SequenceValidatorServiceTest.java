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

import static de.gematik.demis.igs.service.service.validation.FastAValidator.DOUBLE_HEADER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_CHAR_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.LAST_LINE_HEADER_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastQValidator.NO_MULTIPLE_OF_4_MSG;
import static de.gematik.demis.igs.service.service.validation.SequenceValidatorService.ERROR_MESSAGE_FASTQ_SEND_BY_FASTA_USER;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.EMPTY_DOCUMENT_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INVALID_DOCUMENT_TYPE_ERROR_MSG;
import static java.lang.String.format;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static util.BaseUtil.PATH_TO_FASTA;
import static util.BaseUtil.PATH_TO_FASTQ;

import de.gematik.demis.igs.service.utils.Constants.ValidationStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import util.BaseUtil;

@ExtendWith(MockitoExtension.class)
class SequenceValidatorServiceTest {

  public static final String EXAMPLE_ID = "ExampleId";
  private final BaseUtil testUtils = new BaseUtil();
  private final SequenceValidatorService underTest = new SequenceValidatorService();
  @Mock ValidationTracker tracker;
  @Captor ArgumentCaptor<ValidationStatus> statusCaptor;
  @Captor ArgumentCaptor<String> msgCaptor;

  static Stream<Arguments> shouldCallTrackerAccordinglyForValidationError() {
    return Stream.of(
        Arguments.of(
            "One line FASTQ",
            new ByteArrayInputStream("@Eine\nZeile".getBytes(StandardCharsets.UTF_8)),
            NO_MULTIPLE_OF_4_MSG),
        Arguments.of(
            "Two lines FASTQ",
            new ByteArrayInputStream("@Zwei\nZeilen".getBytes(StandardCharsets.UTF_8)),
            NO_MULTIPLE_OF_4_MSG),
        Arguments.of(
            "Three lines FASTQ",
            new ByteArrayInputStream("@Drei\nganze\nZeilen".getBytes(StandardCharsets.UTF_8)),
            NO_MULTIPLE_OF_4_MSG),
        Arguments.of(
            "Fife lines FASTQ",
            new ByteArrayInputStream("@\nAAAA\n+\nqwer\n@\n".getBytes(StandardCharsets.UTF_8)),
            NO_MULTIPLE_OF_4_MSG),
        Arguments.of(
            "Two header FASTA",
            new ByteArrayInputStream(">\nAAAA\n>\n>\nAAA".getBytes(StandardCharsets.UTF_8)),
            DOUBLE_HEADER_ERROR_MESSAGE),
        Arguments.of(
            "Invalid char FASTA",
            new ByteArrayInputStream(">\nA@ERTZUI§%&/A\n>\nAAA".getBytes(StandardCharsets.UTF_8)),
            format(INVALID_CHAR_ERROR_MSG, "sequence", 2)),
        Arguments.of(
            "Last line header FASTA",
            new ByteArrayInputStream(">\nAAAAA\n>\nAAA\n>".getBytes(StandardCharsets.UTF_8)),
            LAST_LINE_HEADER_ERROR_MSG),
        Arguments.of(
            "Empty stream", new ByteArrayInputStream(new byte[0]), EMPTY_DOCUMENT_ERROR_MSG),
        Arguments.of(
            "InvalidDocumentType",
            new ByteArrayInputStream("Inv\nalid\ndocu\nment".getBytes(StandardCharsets.UTF_8)),
            INVALID_DOCUMENT_TYPE_ERROR_MSG));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  void shouldCallTrackerAccordinglyForValidationError(
      String testcaseName, InputStream doc, String msg) throws IOException {
    underTest.validateSequence(doc, EXAMPLE_ID, tracker, false);
    verify(tracker, times(1)).updateValidationStatus(eq(EXAMPLE_ID), statusCaptor.capture());
    verify(tracker, times(1))
        .updateValidationStatus(eq(EXAMPLE_ID), statusCaptor.capture(), msgCaptor.capture());
    assertThat(statusCaptor.getAllValues()).containsExactly(VALIDATING, VALIDATION_FAILED);
    assertThat(msgCaptor.getAllValues()).containsExactly(msg);
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({PATH_TO_FASTA, PATH_TO_FASTQ})
  void shouldCallTrackerAccordinglyOnSuccess(String path) {
    underTest.validateSequence(testUtils.readFileToInputStream(path), EXAMPLE_ID, tracker, false);
    verify(tracker, times(2)).updateValidationStatus(eq(EXAMPLE_ID), statusCaptor.capture());
    assertThat(statusCaptor.getAllValues()).containsExactly(VALIDATING, VALID);
  }

  @Test
  @SneakyThrows
  void shouldUpdateMetadataCorrectIfFastATriesToValidateFastQ() {
    InputStream file = testUtils.readFileToInputStream(PATH_TO_FASTQ);
    underTest.validateSequence(file, EXAMPLE_ID, tracker, true);
    verify(tracker, times(1))
        .updateValidationStatus(
            eq(EXAMPLE_ID), statusCaptor.capture(), eq(ERROR_MESSAGE_FASTQ_SEND_BY_FASTA_USER));
    assertThat(statusCaptor.getAllValues()).containsExactly(VALIDATION_FAILED);
  }
}
