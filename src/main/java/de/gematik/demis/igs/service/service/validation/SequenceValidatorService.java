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

import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.EMPTY_DOCUMENT_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INVALID_DOCUMENT_TYPE_ERROR_MSG;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for validating sequences */
@Component
public class SequenceValidatorService {

  private static final String ASCII_PATTERN = "[\\u0021-\\u007E\\s]+";
  private static final String VALID_SEQUENCE_CHARS_PATTERN = "[ACGTNUKSYMWRBDHV-]+";
  @Autowired private FastAValidationSpecifications fastAValidationSpecifications;
  public static final Pattern asciiPattern = Pattern.compile(ASCII_PATTERN);
  public static final Pattern validCharsPattern = Pattern.compile(VALID_SEQUENCE_CHARS_PATTERN);

  /**
   * Finds the correct validator for the given InputStream and hands it over to the validator to
   * validate the InputStream. For evaluating which validator to use, the first line of the
   * InputStream is read.
   *
   * @param input InputStream to validate
   */
  public void validateSequence(
      InputStream input, String documentId, ValidationTracker validationTracker)
      throws IOException {
    try (input;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input)); ) {
      validationTracker.updateValidationStatus(documentId, VALIDATING);

      SequenceValidator validator;
      String firstLine = bufferedReader.readLine();
      if (firstLine == null) {
        validationTracker.updateValidationStatus(
            documentId, VALIDATION_FAILED, EMPTY_DOCUMENT_ERROR_MSG);
        return;
      }
      if (firstLine.startsWith("@")) {
        validator = new FastQValidator();
      } else if (firstLine.startsWith(">")) {
        validator = new FastAValidator(fastAValidationSpecifications);
      } else {
        validationTracker.updateValidationStatus(
            documentId, VALIDATION_FAILED, INVALID_DOCUMENT_TYPE_ERROR_MSG);
        return;
      }
      try {
        validator.validate(firstLine, bufferedReader);
      } catch (IgsServiceException ex) {
        validationTracker.updateValidationStatus(documentId, VALIDATION_FAILED, ex.getMessage());
        return;
      }
      validationTracker.updateValidationStatus(documentId, VALID);
    }
  }
}
