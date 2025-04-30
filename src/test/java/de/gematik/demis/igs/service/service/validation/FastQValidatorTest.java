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

import static de.gematik.demis.igs.service.service.validation.FastQValidator.FIRST_LINE_WRONG_START_MSG;
import static de.gematik.demis.igs.service.service.validation.FastQValidator.INVALID_CHAR_FOUND_MSG;
import static de.gematik.demis.igs.service.service.validation.FastQValidator.LINE_LENGTH_DIFFER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.service.validation.FastQValidator.NO_ASCII_CHAR_FOUND_MSG;
import static de.gematik.demis.igs.service.service.validation.FastQValidator.THIRD_LINE_WRONG_START_MSG;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class FastQValidatorTest {

  private FastQValidator underTest = new FastQValidator();
  private String VALID_FIRST_LINE = "@";

  @Test
  @SneakyThrows
  void shouldThrowExceptionBecauseNoPlusInLineThree() {
    try (BufferedReader br = createBufferedReader("AAAA\nno+InBeginning\nWhat")) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(VALID_FIRST_LINE, br));
      assertThat(ex.getMessage()).isEqualTo(format(THIRD_LINE_WRONG_START_MSG, 3));
    }
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionBecauseNoAtInLineFive() {
    try (BufferedReader br =
        createBufferedReader(
            "AAAA\n+InBeginning\nWhat\nno@InBeginning\nAAAA\n+InBeginning\nWhat")) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(VALID_FIRST_LINE, br));
      assertThat(ex.getMessage()).isEqualTo(format(FIRST_LINE_WRONG_START_MSG, 5));
    }
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({"a", "(", ",", "!", "1", "9"})
  void shouldThrowExceptionInvalidCharInLineTwo(String invalidChar) {
    try (BufferedReader br =
        createBufferedReader(format("A%sA\n+InBeginning\nWhat", invalidChar))) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(VALID_FIRST_LINE, br));
      assertThat(ex.getMessage()).contains(format(INVALID_CHAR_FOUND_MSG, 2));
    }
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfLineTwoAndFourNotTheSameLength() {
    try (BufferedReader br = createBufferedReader("AAA\n+InBeginning\nDiffer")) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(VALID_FIRST_LINE, br));
      assertThat(ex.getMessage()).isEqualTo(format(LINE_LENGTH_DIFFER_ERROR_MESSAGE, 2, 4));
    }
  }

  static Stream<Arguments> shouldThrowExceptionBecauseOneLineHaveInvalidChar() {
    List<Arguments> arguments = new ArrayList<>();
    List<String> lines = List.of("@", "RAET", "+", "/()");
    List<String> invalidChars = List.of("€", "†", "ä", "Ü", "ö");
    for (String invalidChar : invalidChars) {
      for (int i = 0; i < lines.size(); i++) {
        List<String> testLines = new ArrayList<>();
        for (int j = 0; j < lines.size(); j++) {
          if (i == j) {
            testLines.add(lines.get(j) + invalidChar);
          } else {
            testLines.add(lines.get(j));
          }
        }
        arguments.add(Arguments.of(i, invalidChar, testLines));
      }
    }
    return arguments.stream();
  }

  @ParameterizedTest(name = "{1} in line {0}")
  @MethodSource
  void shouldThrowExceptionBecauseOneLineHaveInvalidChar(
      int line, String invalidChar, List<String> lines) throws IOException {
    try (BufferedReader br = createBufferedReader(String.join("\n", lines.subList(1, 4)))) {
      String firstLine = lines.getFirst();
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
      assertThat(ex.getMessage()).isEqualTo(format(NO_ASCII_CHAR_FOUND_MSG, 1, 4));
    }
  }

  private BufferedReader createBufferedReader(String s) {
    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())));
  }
}
