package de.gematik.demis.igs.service.api;

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

import static de.gematik.demis.igs.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;

import de.gematik.demis.igs.service.exception.IgsValidationException;
import de.gematik.demis.igs.service.parser.FhirParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class DefaultExceptionHandler {

  @ExceptionHandler(IgsValidationException.class)
  public final ResponseEntity<Object> handleServiceException(
      final IgsValidationException ex, final WebRequest request) {
    if (!ex.getErrorCode().equals(FHIR_VALIDATION_ERROR.toString())) {
      throw ex;
    }
    MediaType contentType = determineOutputFormat(request);
    String operationOutcome = FhirParser.serializeResource(ex.getOperationOutcome(), contentType);
    return ResponseEntity.unprocessableEntity().contentType(contentType).body(operationOutcome);
  }

  private static MediaType determineOutputFormat(final WebRequest webRequest) {
    final String outputFormat;
    final String accept = webRequest.getHeader(HttpHeaders.ACCEPT);
    if (accept != null && !accept.contains("*")) {
      outputFormat = accept;
    } else {
      final String contentType = webRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      outputFormat = contentType != null ? contentType : MediaType.APPLICATION_JSON_VALUE;
    }

    return MediaType.parseMediaType(outputFormat);
  }
}
