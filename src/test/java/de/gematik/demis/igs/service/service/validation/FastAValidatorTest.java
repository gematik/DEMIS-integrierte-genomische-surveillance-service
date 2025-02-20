/*
 * Copyright [2024], gematik GmbH
 *
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
 */

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

import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_CHAR_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_FASTA_CONFIG_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_FASTA_LINE_WRONG_LENGTH_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_FASTA_TO_MANY_N;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.validation.FastAValidationSpecifications.FastAConfig;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class FastAValidatorTest {

  private static final FastAValidationSpecifications specifications =
      FastAValidationSpecifications.builder()
          .configs(
              List.of(new FastAConfig("asdf", 100, 10, 0.1), new FastAConfig("qwer", 10, 2, 0.5)))
          .build();
  private FastAValidator underTest = new FastAValidator(specifications);
  ;

  private static BufferedReader createBufferedReader(String s) {
    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())));
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfTwoFollowingHeader() {
    String firstLine = ">HeresomeComment with spaces";
    try (BufferedReader br =
        createBufferedReader("AAAA\nAAAA\nAAAA\nAAAA\n>Header\n>AnotherHeader")) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
      assertThat(ex.getMessage()).contains("double header line in fasta detected");
    }
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfEndsWithHeader() {
    String firstLine = ">HeresomeComment with spaces";
    try (BufferedReader br = createBufferedReader("AAAA\nAAAA\nAAAA\nAAAA\n>Header")) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
      assertThat(ex.getMessage()).contains("Invalid sequence -> last line is header line");
    }
  }

  @ParameterizedTest
  @SneakyThrows
  @CsvSource({"X", "a", "(", ",", "!", "1", "9"})
  void shouldThrowExceptionIfInvalidCharInSequence(String invalidChar) {
    String firstLine = ">HeresomeComment with spaces";
    try (BufferedReader br = createBufferedReader(format("AAAA\nA%sAA\nAAAA\nAAAA", invalidChar))) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
      assertThat(ex.getMessage()).contains(format(INVALID_CHAR_ERROR_MSG, "sequence", 3));
    }
  }

  @SneakyThrows
  @ParameterizedTest(name = "{0}")
  @CsvSource({"€", "†", "ä", "Ü", "ö"})
  void shouldThrowExceptionIfInvalidCharInHeader(String invalidChar) {
    String firstLine = format(">HeresomeComment %swith spaces", invalidChar);
    try (BufferedReader br = createBufferedReader("AAAA\nAAA\nAAAA\nAAAA")) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
      assertThat(ex.getMessage()).contains(format(INVALID_CHAR_ERROR_MSG, "header", 1));
    }
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfOnlyHeader() {
    String firstLine = ">HeresomeComment with spaces";
    try (BufferedReader br = createBufferedReader("")) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
      assertThat(ex.getMessage()).contains("last line is header line");
    }
  }

  @Nested
  class StrictValidationFastA {

    static Stream<Arguments> shouldNotThrowException() {
      return Stream.of(
          Arguments.of(
              "IfNoPathogenFound",
              ">pathogen=notfound",
              createBufferedReader("AAAA\nAAA\nAAAA\nAAAA")),
          Arguments.of(
              "IfLineHaveExactAllowedN", ">pathogen=qwer", createBufferedReader("AA\nAA\nNN\nNN")),
          Arguments.of(
              "IfManyNInNotDefined",
              ">pathogen=qwer",
              createBufferedReader("AA\nAA\nNN\nNN\n>pathogen=notExisting\nNNNNNN\nN")),
          Arguments.of(
              "IgnoreLengthIfPathogenOnlyInFirstBlock",
              ">pathogen=qwer",
              createBufferedReader("AAAAA\nAA\nAA\nA\n>pathogen=notExisting\nA\nAAAAAA")));
    }

    static Stream<Arguments> shouldThrowException() {
      return Stream.of(
          Arguments.of(
              "PathogenToShort",
              ">pathogen=qer",
              createBufferedReader("AAAA\nAAA\nAAAA\nAAAAAA"),
              INVALID_FASTA_CONFIG_ERROR_MSG),
          Arguments.of(
              "PathogenContainsSpace",
              ">pathogen=q er",
              createBufferedReader("AAAA\nAAA\nAAAA\nAAAAAA"),
              INVALID_FASTA_CONFIG_ERROR_MSG),
          Arguments.of(
              "ToLongInFirstBlock",
              ">pathogen=qwer",
              createBufferedReader("AAAA\nAAA\nAAAA\nAAAAAA"),
              errorForLineLength(5, "qwer")),
          Arguments.of(
              "ToShortInFirstBlock",
              ">pathogen=qwer",
              createBufferedReader("AAAA\nAAA\nA\nAAAAA"),
              errorForLineLength(5, "qwer")),
          Arguments.of(
              "ToLongInSecondBlock",
              ">nothing",
              createBufferedReader("AAAA\n>pathogen=qwer\nAAAAAAAAAAA"),
              errorForLineLength(4, "qwer")),
          Arguments.of(
              "ToShortInSecondBlock",
              ">nothing",
              createBufferedReader("AAAA\nAAA\nA\nAAAAA\n>pathogen=qwer\nA"),
              errorForLineLength(7, "qwer")),
          Arguments.of(
              "OtherPathogen",
              ">nothing",
              createBufferedReader("AAAA\nAAA\nA\nAAAAA\n>pathogen=asdf\nA"),
              errorForLineLength(7, "asdf")),
          Arguments.of(
              "ToManyN",
              ">pathogen=qwer",
              createBufferedReader("AA\nAA\nNNN\nNN"),
              format(INVALID_FASTA_TO_MANY_N, 5, "qwer", 0.5)),
          Arguments.of(
              "ToManyNInSecondBlock",
              ">pathogen=qwer",
              createBufferedReader(
                  "AAA\n>pathogen=asdf\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAANNNNNNNNNNN"),
              format(INVALID_FASTA_TO_MANY_N, 4, "asdf", 0.1)));
    }

    private static String errorForLineLength(int lineNumber, String code) {
      return format(
          INVALID_FASTA_LINE_WRONG_LENGTH_ERROR_MSG,
          lineNumber,
          code,
          specifications.findConfigByName(code).getShortest(),
          specifications.findConfigByName(code).getLongest());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @SneakyThrows
    void shouldNotThrowException(String testName, String firstLine, BufferedReader br) {
      assertDoesNotThrow(() -> underTest.validate(firstLine, br));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @SneakyThrows
    void shouldThrowException(
        String testName, String firstLine, BufferedReader br, String expectedMessage) {
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
      assertThat(ex.getMessage()).isEqualTo(expectedMessage);
    }
  }
}
