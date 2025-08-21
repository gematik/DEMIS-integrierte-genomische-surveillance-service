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

import static java.util.Objects.isNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import de.gematik.demis.igs.service.parser.FhirContentTypeMapper;
import de.gematik.demis.igs.service.parser.FhirParser;
import de.gematik.demis.igs.service.service.NotificationService;
import lombok.AllArgsConstructor;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class NotificationController {

  static final String FHIR_BUNDLE_BASE = "$process-notification-sequence";
  public final NotificationService service;

  /**
   * Generates a notification bundle based on the provided content and media type, enriches it with
   * generated DEMIS Sequence ID
   *
   * @param content serialized notification bundle
   * @param mediaType mimetype of the serialized bundle
   * @return a ResponseEntity containing the generated notification bundle
   */
  @PostMapping(
      path = "${igs.context-path}" + FHIR_BUNDLE_BASE,
      consumes = {
        APPLICATION_JSON_VALUE,
        APPLICATION_XML_VALUE,
        "application/json+fhir",
        "application/fhir+json"
      },
      produces = {
        APPLICATION_JSON_VALUE,
        APPLICATION_XML_VALUE,
        "application/json+fhir",
        "application/fhir+json"
      })
  public ResponseEntity<String> saveNotificationBundle(
      @RequestBody String content,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) MediaType mediaType,
      @RequestHeader(value = HttpHeaders.ACCEPT, required = false) MediaType acceptType,
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization) {
    MediaType fhirMediaType = FhirContentTypeMapper.map(mediaType.toString());
    Parameters savedNotification = service.process(content, fhirMediaType, authorization);

    MediaType responseMediaType = isNull(acceptType) ? mediaType : acceptType;
    return ResponseEntity.ok()
        .body(
            FhirParser.serializeResource(
                savedNotification, FhirContentTypeMapper.map(responseMediaType.toString())));
  }
}
