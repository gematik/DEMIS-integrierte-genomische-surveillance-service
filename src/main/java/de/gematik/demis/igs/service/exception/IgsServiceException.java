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

import de.gematik.demis.service.base.error.ServiceException;
import lombok.Getter;
import org.hl7.fhir.r4.model.OperationOutcome;

@Getter
public class IgsServiceException extends ServiceException {

  private final OperationOutcome operationOutcome;

  public IgsServiceException(final ErrorCode errorCode, final OperationOutcome operationOutcome) {
    super(errorCode.getHttpStatus(), errorCode.getCode(), null);
    this.operationOutcome = operationOutcome;
  }

  public IgsServiceException(final ErrorCode errorCode, final String message) {
    super(errorCode.getHttpStatus(), errorCode.getCode(), message);
    operationOutcome = null;
  }

  public IgsServiceException(
      final ErrorCode errorCode, final String message, final Throwable cause) {
    super(errorCode.getHttpStatus(), errorCode.getCode(), message, cause);
    operationOutcome = null;
  }
}
