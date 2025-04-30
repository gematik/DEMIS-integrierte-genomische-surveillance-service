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

import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_DOCUMENT;
import static de.gematik.demis.igs.service.service.validation.SequenceValidatorService.asciiPattern;
import static de.gematik.demis.igs.service.service.validation.SequenceValidatorService.validCharsPattern;
import static java.lang.String.format;
import static java.math.BigInteger.ZERO;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.validation.FastAValidationSpecifications.FastAConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;

/** Validator for FastA files */
@RequiredArgsConstructor
public class FastAValidator implements SequenceValidator {

  public static final String LAST_LINE_HEADER_ERROR_MSG =
      "Invalide Sequenz -> Letzte Zeile darf keine Headerzeile sein";
  public static final String DOUBLE_HEADER_ERROR_MESSAGE =
      "Invalide Sequenz -> Doppelte Headerzeile gefunden";
  public static final String INVALID_CHAR_ERROR_MSG =
      "Invalide Sequenz -> Invalides Zeichen %s in Zeile %s gefunden";
  public static final String INVALID_FASTA_CONFIG_ERROR_MSG =
      "Invalide Sequenz -> Pathogencode muss 4 Zeichen ohne Whitespace lang sein";
  public static final String INVALID_FASTA_LINE_WRONG_LENGTH_ERROR_MSG =
      "Invalide Sequenz -> Block vor Zeile %s: Block eines Pathogens '%s' muss zwischen %s und %s Zeichen haben";
  public static final String INVALID_FASTA_TO_MANY_N =
      "Invalide Sequenz -> Block vor Zeile %s: Block eines Pathogens '%s' darf nicht mehr als %s Prozent N enthalten";
  public static final String MISSING_PATHOGEN_CODE_ERROR_MSG =
      "Invalide Sequenz -> Block vor Zeile %s: Fehlender Pathogencode";
  public static final String PATHOGEN_KEYWORD = "pathogen=";
  private final FastAValidationSpecifications fastAValidationSpecifications;
  private final boolean isFastASender;
  private boolean disallowHeaderLine = false;
  private long lineNumber = 0;
  private FastAConfig currentFastAConfig;
  private BigInteger amountOfCharsInBlock = ZERO;
  private BigInteger amountNInBlock = ZERO;
  private boolean isFirstHeader = true;

  @Override
  public void validate(String firstLine, BufferedReader bufferedReader) throws IOException {
    validateFastA(firstLine);
    String line = bufferedReader.readLine();
    while (line != null) {
      validateFastA(line);
      line = bufferedReader.readLine();
    }
    executeExtendedValidation();
    if (disallowHeaderLine) {
      throw new IgsServiceException(INVALID_DOCUMENT, LAST_LINE_HEADER_ERROR_MSG);
    }
  }

  private void validateFastA(String line) {
    lineNumber++;
    if (line.startsWith(">")) {
      executeExtendedValidation();
      setCurrentFastAConfig(line);

      validateHeader(line);
    } else {
      validateSequenceLine(line);
    }
  }

  private void executeExtendedValidation() {
    if (currentFastAConfig == null) {
      if (isFastASender && !isFirstHeader) {
        throw new IgsServiceException(
            INVALID_DOCUMENT, MISSING_PATHOGEN_CODE_ERROR_MSG.formatted(lineNumber));
      }
      isFirstHeader = false;
      return;
    }
    checkLengthConstraints();
    checkPercentageConstraint();
    resetStatistic();
  }

  private void checkLengthConstraints() {
    if (amountOfCharsInBlock.compareTo(BigInteger.valueOf(currentFastAConfig.getShortest())) < 0
        || amountOfCharsInBlock.compareTo(BigInteger.valueOf(currentFastAConfig.getLongest()))
            > 0) {
      throw new IgsServiceException(
          INVALID_DOCUMENT,
          format(
              INVALID_FASTA_LINE_WRONG_LENGTH_ERROR_MSG,
              lineNumber,
              currentFastAConfig.getName(),
              currentFastAConfig.getShortest(),
              currentFastAConfig.getLongest()));
    }
  }

  private void checkPercentageConstraint() {
    double percentageN = amountNInBlock.doubleValue() / amountOfCharsInBlock.doubleValue();
    if (percentageN > currentFastAConfig.getPercentageN()) {
      throw new IgsServiceException(
          INVALID_DOCUMENT,
          format(
              INVALID_FASTA_TO_MANY_N,
              lineNumber,
              currentFastAConfig.getName(),
              currentFastAConfig.getPercentageN()));
    }
  }

  private void resetStatistic() {
    amountOfCharsInBlock = ZERO;
    amountNInBlock = ZERO;
  }

  private void setCurrentFastAConfig(String line) {
    int index = line.toLowerCase().indexOf(PATHOGEN_KEYWORD);
    if (index == -1) {
      currentFastAConfig = null;
      return;
    }
    if (index + PATHOGEN_KEYWORD.length() + 4 > line.length()) {
      throw new IgsServiceException(INVALID_DOCUMENT, INVALID_FASTA_CONFIG_ERROR_MSG);
    }
    String code =
        line.substring(index + PATHOGEN_KEYWORD.length(), index + PATHOGEN_KEYWORD.length() + 4);
    if (!code.matches("[a-zA-Z]{4}")) {
      throw new IgsServiceException(INVALID_DOCUMENT, INVALID_FASTA_CONFIG_ERROR_MSG);
    }
    currentFastAConfig = fastAValidationSpecifications.findConfigByName(code.toLowerCase());
  }

  private void validateHeader(String line) {
    if (disallowHeaderLine) {
      throw new IgsServiceException(INVALID_DOCUMENT, DOUBLE_HEADER_ERROR_MESSAGE);
    }
    if (!asciiPattern.matcher(line).matches()) {
      throw new IgsServiceException(
          INVALID_DOCUMENT, format(INVALID_CHAR_ERROR_MSG, "header", lineNumber));
    }
    disallowHeaderLine = true;
  }

  private void validateSequenceLine(String line) {
    if (!validCharsPattern.matcher(line).matches()) {
      throw new IgsServiceException(
          INVALID_DOCUMENT, format(INVALID_CHAR_ERROR_MSG, "sequence", lineNumber));
    }
    collectStatisticsForExtendedValidation(line);
    disallowHeaderLine = false;
  }

  private void collectStatisticsForExtendedValidation(String line) {
    if (currentFastAConfig == null) {
      return;
    }
    amountOfCharsInBlock = amountOfCharsInBlock.add(BigInteger.valueOf(line.length()));
    amountNInBlock =
        amountNInBlock.add(BigInteger.valueOf(line.chars().filter(c -> c == 'N').count()));
  }
}
