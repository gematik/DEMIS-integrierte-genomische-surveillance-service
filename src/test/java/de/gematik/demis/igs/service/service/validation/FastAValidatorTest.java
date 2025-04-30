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
import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_FASTA_CONFIG_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_FASTA_LINE_WRONG_LENGTH_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.INVALID_FASTA_TO_MANY_N;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.LAST_LINE_HEADER_ERROR_MSG;
import static de.gematik.demis.igs.service.service.validation.FastAValidator.MISSING_PATHOGEN_CODE_ERROR_MSG;
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

  private static final String PATHOGEN_NAME_1 = "asdf";
  private static final String PATHOGEN_NAME_2 = "qwer";
  private static final FastAValidationSpecifications specifications =
      FastAValidationSpecifications.builder()
          .fastAConfigs(
              List.of(
                  new FastAConfig(PATHOGEN_NAME_1, 100, 10, 0.1),
                  new FastAConfig(PATHOGEN_NAME_2, 10, 2, 0.5)))
          .build();
  private FastAValidator underTest = new FastAValidator(specifications, false);
  private FastAValidator underTestFastAOnly = new FastAValidator(specifications, true);

  private static BufferedReader createBufferedReader(String s) {
    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())));
  }

  @Nested
  class ValidationFastA {

    @Test
    @SneakyThrows
    void shouldValidateSuccessfully() {
      String firstLine = ">HeresomeComment with spaces";
      try (BufferedReader br = createBufferedReader("AAAA\nAAAA\nAAAA\nAAAA\n>AnotherHeader\nA")) {
        assertDoesNotThrow(() -> underTest.validate(firstLine, br));
      }
    }

    @Test
    @SneakyThrows
    void shouldThrowExceptionIfTwoFollowingHeader() {
      String firstLine = ">HeresomeComment with spaces";
      try (BufferedReader br =
          createBufferedReader("AAAA\nAAAA\nAAAA\nAAAA\n>Header\n>AnotherHeader")) {
        IgsServiceException ex =
            assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
        assertThat(ex.getMessage()).contains(DOUBLE_HEADER_ERROR_MESSAGE);
      }
    }

    @Test
    @SneakyThrows
    void shouldThrowExceptionIfEndsWithHeader() {
      String firstLine = ">HeresomeComment with spaces";
      try (BufferedReader br = createBufferedReader("AAAA\nAAAA\nAAAA\nAAAA\n>Header")) {
        IgsServiceException ex =
            assertThrows(IgsServiceException.class, () -> underTest.validate(firstLine, br));
        assertThat(ex.getMessage()).contains(LAST_LINE_HEADER_ERROR_MSG);
      }
    }

    @ParameterizedTest
    @SneakyThrows
    @CsvSource({"X", "a", "(", ",", "!", "1", "9"})
    void shouldThrowExceptionIfInvalidCharInSequence(String invalidChar) {
      String firstLine = ">HeresomeComment with spaces";
      try (BufferedReader br =
          createBufferedReader(format("AAAA\nA%sAA\nAAAA\nAAAA", invalidChar))) {
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
        assertThat(ex.getMessage()).contains(LAST_LINE_HEADER_ERROR_MSG);
      }
    }
  }

  @Nested
  class StrictValidationFastA {

    static Stream<Arguments> violatesPathogenRuleExamples() {
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
              errorForLineLength(5, PATHOGEN_NAME_2)),
          Arguments.of(
              "ToShortInFirstBlock",
              ">pathogen=qwer",
              createBufferedReader("AAAA\nAAA\nA\nAAAAA"),
              errorForLineLength(5, PATHOGEN_NAME_2)),
          Arguments.of(
              "ToLongInSecondBlock",
              ">pathogen=" + PATHOGEN_NAME_2,
              createBufferedReader("AAAA\n>pathogen=" + PATHOGEN_NAME_2 + "\nAAAAAAAAAAA"),
              errorForLineLength(4, PATHOGEN_NAME_2)),
          Arguments.of(
              "ToShortInSecondBlock",
              ">pathogen=" + PATHOGEN_NAME_1,
              createBufferedReader("AAAA\nAAA\nA\nAAAAA\n>pathogen=" + PATHOGEN_NAME_2 + "\nA"),
              errorForLineLength(7, PATHOGEN_NAME_2)),
          Arguments.of(
              "ToManyN",
              ">pathogen=" + PATHOGEN_NAME_2,
              createBufferedReader("AA\nAA\nNNN\nNN"),
              format(INVALID_FASTA_TO_MANY_N, 5, PATHOGEN_NAME_2, 0.5)),
          Arguments.of(
              "ToManyNInSecondBlock",
              ">pathogen=" + PATHOGEN_NAME_2,
              createBufferedReader(
                  "AAA\n>pathogen="
                      + PATHOGEN_NAME_1
                      + "\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAANNNNNNNNNNN"),
              format(INVALID_FASTA_TO_MANY_N, 4, PATHOGEN_NAME_1, 0.1)));
    }

    private static String errorForLineLength(int lineNumber, String code) {
      return format(
          INVALID_FASTA_LINE_WRONG_LENGTH_ERROR_MSG,
          lineNumber,
          code,
          specifications.findConfigByName(code).getShortest(),
          specifications.findConfigByName(code).getLongest());
    }

    @Nested
    class StrictValidationFastAWithoutFastAOnlyRole {

      static Stream<Arguments> shouldNotThrowException() {
        return Stream.of(
            Arguments.of(
                "IfNoPathogenFound",
                ">pathogen=notfound",
                createBufferedReader("AAAA\nAAA\nAAAA\nAAAA")),
            Arguments.of(
                "IfLineHaveExactAllowedN",
                ">pathogen=qwer",
                createBufferedReader("AA\nAA\nNN\nNN")),
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
        return StrictValidationFastA.violatesPathogenRuleExamples();
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

    @Nested
    class StrictValidationFastAWithFastAOnlyRole {

      static Stream<Arguments> shouldThrowExceptionForSequencesOkByNotFastAOnly() {
        return Stream.of(
            Arguments.of(
                "PathogenMissingInFirstBlock",
                ">NoPathogen",
                createBufferedReader(
                    "AAAA\nAAA\nAAAA\nAAAAAA\n>pathogen="
                        + PATHOGEN_NAME_1
                        + "\nAAAAA\nAAAAA\nAAAAA"),
                MISSING_PATHOGEN_CODE_ERROR_MSG.formatted(6)),
            Arguments.of(
                "PathogenMissingInSecondBlock",
                ">pathogen=" + PATHOGEN_NAME_1,
                createBufferedReader("AAAA\nAAA\nAAAA\nAAAAAA\n>NoPathogen\nAAAAA\nAAAAA\nAAAAA"),
                MISSING_PATHOGEN_CODE_ERROR_MSG.formatted(9)),
            Arguments.of(
                "IfPathogenUnknown",
                ">pathogen=" + PATHOGEN_NAME_1,
                createBufferedReader(
                    "AAAA\nAAA\nAAAA\nAAAAAA\n>NoPathogen\nAAAAA\nAAAAA\nAAAAA\n>pathogen=notfound\nAAAA\nAAA\nAAAA\nAAAA"),
                MISSING_PATHOGEN_CODE_ERROR_MSG.formatted(10)));
      }

      static Stream<Arguments> shouldThrowExceptionWithFastAOnly() {
        return StrictValidationFastA.violatesPathogenRuleExamples();
      }

      @Test
      @SneakyThrows
      void shouldValidateCorrectlyWithFastAOnly() {
        String firstLine = ">HeresomeComment with spaces pathogen=" + PATHOGEN_NAME_1;
        try (BufferedReader br =
            createBufferedReader(
                "AAAA\nAAAA\nAAAA\nAAAA\n>asdfpathogen=" + PATHOGEN_NAME_2 + "\nAAAAA\nAAA\nAA")) {
          assertDoesNotThrow(() -> underTestFastAOnly.validate(firstLine, br));
        }
      }

      @SneakyThrows
      @MethodSource
      @ParameterizedTest(name = "{0}")
      void shouldThrowExceptionForSequencesOkByNotFastAOnly(
          String testName, String firstLine, BufferedReader br, String expectedMessage) {
        IgsServiceException ex =
            assertThrows(
                IgsServiceException.class, () -> underTestFastAOnly.validate(firstLine, br));
        assertThat(ex.getMessage()).isEqualTo(expectedMessage);
      }

      @SneakyThrows
      @MethodSource
      @ParameterizedTest(name = "{0}")
      void shouldThrowExceptionWithFastAOnly(
          String testName, String firstLine, BufferedReader br, String expectedMessage) {
        IgsServiceException ex =
            assertThrows(
                IgsServiceException.class, () -> underTestFastAOnly.validate(firstLine, br));
        assertThat(ex.getMessage()).isEqualTo(expectedMessage);
      }
    }
  }
}
