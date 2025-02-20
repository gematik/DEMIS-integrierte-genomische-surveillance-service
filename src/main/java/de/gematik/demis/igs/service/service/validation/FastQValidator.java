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

import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_DOCUMENT;
import static de.gematik.demis.igs.service.service.validation.SequenceValidatorService.asciiPattern;
import static de.gematik.demis.igs.service.service.validation.SequenceValidatorService.validCharsPattern;
import static java.lang.String.format;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.experimental.Accessors;

/** Validator for FastQ files */
public class FastQValidator implements SequenceValidator {

  public static final String LINE_LENGTH_DIFFER_ERROR_MESSAGE =
      "Invalid sequence -> sequence and quality of line Nr. %s and Nr. %s have different length";
  public static final String FIRST_LINE_WRONG_START_MSG =
      "Invalid sequence -> line Nr. %s does not start with '@'";
  public static final String THIRD_LINE_WRONG_START_MSG =
      "Invalid sequence -> line Nr. %s does not start with '+'";
  public static final String NO_ASCII_CHAR_FOUND_MSG =
      "Invalid sequence -> found invalid character in line Nr. %s to Nr. %s";
  public static final String INVALID_CHAR_FOUND_MSG =
      "Invalid sequence -> found invalid char in sequence line Nr. %s";
  public static final String NO_MULTIPLE_OF_4_MSG =
      "Number of document lines is not a multiple of 4";
  private long linenumber = 0;

  @Override
  public void validate(String firstLine, BufferedReader bufferedReader) throws IOException {
    FourLines nextLines =
        getFourLines(bufferedReader, FourLines.builder().line1(firstLine).build());

    if (nextLines == null) {
      throw new IgsServiceException(INVALID_DOCUMENT, "Empty document detected");
    }

    do {
      validateFastQ(nextLines);
      nextLines = getFourLines(bufferedReader);
    } while (nextLines != null);
  }

  private void validateFastQ(FourLines lines) {
    if (!lines.line1.startsWith("@")) {
      throw new IgsServiceException(
          INVALID_DOCUMENT, format(FIRST_LINE_WRONG_START_MSG, linenumber - 3));
    }
    if (!lines.line3.startsWith("+")) {
      throw new IgsServiceException(
          INVALID_DOCUMENT, format(THIRD_LINE_WRONG_START_MSG, linenumber - 1));
    }
    for (String line : lines.getLines()) {
      if (!asciiPattern.matcher(line).matches()) {
        throw new IgsServiceException(
            INVALID_DOCUMENT, format(NO_ASCII_CHAR_FOUND_MSG, (linenumber - 3), linenumber));
      }
    }
    if (!validCharsPattern.matcher(lines.line2).matches()) {
      throw new IgsServiceException(
          INVALID_DOCUMENT, format(INVALID_CHAR_FOUND_MSG, linenumber - 2));
    }
    if (lines.line2.length() != lines.line4.length()) {
      throw new IgsServiceException(
          INVALID_DOCUMENT, format(LINE_LENGTH_DIFFER_ERROR_MESSAGE, linenumber - 2, linenumber));
    }
  }

  private FourLines getFourLines(BufferedReader bufferedReader) throws IOException {
    return getFourLines(bufferedReader, FourLines.builder().build());
  }

  private FourLines getFourLines(BufferedReader bufferedReader, FourLines fourLines)
      throws IOException {
    if (fourLines.line1 == null) {
      fourLines.line1 = bufferedReader.readLine();
    }
    if (fourLines.line1 == null) {
      return null;
    }

    fourLines
        .line2(bufferedReader.readLine())
        .line3(bufferedReader.readLine())
        .line4(bufferedReader.readLine());

    if (!fourLines.isValid()) {
      throw new IgsServiceException(INVALID_DOCUMENT, NO_MULTIPLE_OF_4_MSG);
    }
    linenumber += 4;
    return fourLines;
  }

  @Builder
  @Setter
  @Accessors(fluent = true)
  private static class FourLines {

    private String line1;
    private String line2;
    private String line3;
    private String line4;

    public boolean isValid() {
      return line1 != null && line2 != null && line3 != null && line4 != null;
    }

    public List<String> getLines() {
      return List.of(line1, line2, line3, line4);
    }
  }
}
