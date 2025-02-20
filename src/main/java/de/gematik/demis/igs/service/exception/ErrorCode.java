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
package de.gematik.demis.igs.service.exception;

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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {
  FHIR_VALIDATION_FATAL(HttpStatus.BAD_REQUEST),
  INVALID_FILE_SIZE(HttpStatus.BAD_REQUEST),
  INVALID_DOCUMENT_ID(HttpStatus.BAD_REQUEST),
  INVALID_LAB_ID(HttpStatus.BAD_REQUEST),
  INVALID_DOCUMENT_HASH(HttpStatus.BAD_REQUEST),
  INVALID_DOCUMENT(HttpStatus.BAD_REQUEST),
  INVALID_DOCUMENT_REFERENCE(HttpStatus.BAD_REQUEST),
  INVALID_DOCUMENT_VALIDATION(HttpStatus.BAD_REQUEST),
  UPLOAD_DOCUMENT_ONGOING(HttpStatus.BAD_REQUEST),
  MISSING_RESOURCE(HttpStatus.BAD_REQUEST),
  NOTIFICATION_ERROR(HttpStatus.BAD_REQUEST),
  SEQUENCE_DATA_NOT_VALID(HttpStatus.BAD_REQUEST),
  FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
  FHIR_VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
  PROFILE_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY),
  NOTIFICATION_SAVE_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  MULTIPART_UPLOAD_COMPLETE_ERROR(HttpStatus.BAD_REQUEST);

  private final HttpStatus httpStatus;

  public String getCode() {
    return name();
  }
}
