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
 * #L%
 */

import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INVALID_COMPRESSED_FILE_ERROR_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static util.BaseUtil.PATH_TO_FASTA;
import static util.BaseUtil.PATH_TO_FASTA_GZIP;
import static util.BaseUtil.PATH_TO_FASTQ;
import static util.BaseUtil.PATH_TO_FASTQ_GZIP;
import static util.BaseUtil.PATH_TO_GZIP_INVALID;

import de.gematik.demis.igs.service.service.validation.ValidationTracker;
import de.gematik.demis.igs.service.utils.Constants.ValidationStatus;
import de.gematik.demis.igs.service.utils.Pair;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import util.BaseUtil;

@ExtendWith(MockitoExtension.class)
class GzipDecompressionFunctionTest {

  public static final String EXAMPLE_ID = "SomeId";
  BaseUtil testUtil = new BaseUtil();
  @Mock ValidationTracker tracker;
  @Captor ArgumentCaptor<ValidationStatus> statusCaptor;
  @Captor ArgumentCaptor<String> msgCaptor;

  static Stream<Arguments> shouldWriteCorrectToOutputStream() {
    return Stream.of(
        Arguments.of("FastA", PATH_TO_FASTA, PATH_TO_FASTA),
        Arguments.of("FastAGzip", PATH_TO_FASTA_GZIP, PATH_TO_FASTA),
        Arguments.of("FastQ", PATH_TO_FASTQ, PATH_TO_FASTQ),
        Arguments.of("FastQGZip", PATH_TO_FASTQ_GZIP, PATH_TO_FASTQ));
  }

  @ParameterizedTest(name = "{0}")
  @SneakyThrows
  @MethodSource
  void shouldWriteCorrectToOutputStream(String name, String path, String expectedPath) {
    try (InputStream input = testUtil.readFileToInputStream(path);
        OutputStream out = new ByteArrayOutputStream()) {
      Pair pair = testUtil.getFirstBytesOfFile(path);
      new GzipDecompressionFunction(
              Integer.parseInt(pair.first()), Integer.parseInt(pair.second()), EXAMPLE_ID, tracker)
          .apply(input, out);
      assertTrue(
          testUtil.streamCompare(
              testUtil.readFileToInputStream(expectedPath),
              new ByteArrayInputStream(out.toString().getBytes())));
    }
  }

  @Test
  @SneakyThrows
  void shouldCallValidationTrackerAccordinglyWhenGZipValid() {
    try (InputStream input = testUtil.readFileToInputStream(PATH_TO_FASTA_GZIP);
        OutputStream out = new ByteArrayOutputStream()) {
      Pair pair = testUtil.getFirstBytesOfFile(PATH_TO_FASTA_GZIP);
      new GzipDecompressionFunction(
              Integer.parseInt(pair.first()), Integer.parseInt(pair.second()), EXAMPLE_ID, tracker)
          .apply(input, out);
      verify(tracker, times(2)).updateGzipStatus(eq(EXAMPLE_ID), statusCaptor.capture());
      assertThat(statusCaptor.getAllValues()).containsExactly(VALIDATING, VALID);
    }
  }

  @Test
  @SneakyThrows
  void shouldCallValidationTrackerAccordinglyWhenGZipCorrupt() {
    try (InputStream firstBytes = testUtil.readFileToInputStream(PATH_TO_GZIP_INVALID);
        InputStream input = testUtil.readFileToInputStream(PATH_TO_GZIP_INVALID);
        OutputStream out = new ByteArrayOutputStream()) {
      new GzipDecompressionFunction(firstBytes.read(), firstBytes.read(), EXAMPLE_ID, tracker)
          .apply(input, out);
    }
    assertAll(
        () -> verify(tracker, times(1)).updateGzipStatus(eq(EXAMPLE_ID), statusCaptor.capture()),
        () ->
            verify(tracker, times(1))
                .updateGzipStatus(eq(EXAMPLE_ID), statusCaptor.capture(), msgCaptor.capture()),
        () ->
            assertThat(statusCaptor.getAllValues()).containsExactly(VALIDATING, VALIDATION_FAILED),
        () ->
            assertThat(msgCaptor.getAllValues())
                .containsExactly(INVALID_COMPRESSED_FILE_ERROR_MSG));
  }
}
