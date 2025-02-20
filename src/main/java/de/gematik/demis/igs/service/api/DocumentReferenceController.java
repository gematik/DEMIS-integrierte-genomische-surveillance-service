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
 * #L%
 */

import static de.gematik.demis.igs.service.utils.ErrorMessages.WRONG_PATH_DELIVERED_ERROR_MSG;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import de.gematik.demis.igs.service.parser.FhirContentTypeMapper;
import de.gematik.demis.igs.service.parser.FhirParser;
import de.gematik.demis.igs.service.service.DocumentReferenceService;
import java.net.URI;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DocumentReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller class for DocumentReference and its binary attachment operations */
@RestController
@Slf4j
@AllArgsConstructor
public class DocumentReferenceController {

  public static final String DOCUMENT_REFERENCE_PATH = "DocumentReference.content.attachment";
  public static final String DOCUMENT_ID_PATH_VARIABLE = "documentId";
  public static final String FHIR_DOCUMENT_REFERENCE_BASE = "/fhir/DocumentReference";
  private static final String PATH_DOCUMENT_ID = "/{" + DOCUMENT_ID_PATH_VARIABLE + "}";
  static final String FHIR_DOCUMENT_REFERENCE = FHIR_DOCUMENT_REFERENCE_BASE + PATH_DOCUMENT_ID;
  static final String FHIR_DOCUMENT_REFERENCE_BINARY_READ =
      FHIR_DOCUMENT_REFERENCE + "/$binary-access-read";

  private final DocumentReferenceService documentReferenceService;

  /**
   * Generates a DocumentReference based on the provided content and media type
   *
   * @param content serialized DocumentReference
   * @param mediaType mimetype of the serialized DocumentReference
   * @return a ResponseEntity containing the generated DocumentReference
   */
  @PostMapping(
      path = FHIR_DOCUMENT_REFERENCE_BASE,
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
  public ResponseEntity<String> generateDocumentReference(
      @RequestBody String content, @RequestHeader(HttpHeaders.CONTENT_TYPE) MediaType mediaType) {
    MediaType fhirMediaType = FhirContentTypeMapper.map(mediaType.toString());
    DocumentReference documentReference =
        documentReferenceService.generateDocumentReference(content, fhirMediaType);

    return ResponseEntity.created(
            URI.create(FHIR_DOCUMENT_REFERENCE_BASE + "/" + documentReference.getId()))
        .header(CONTENT_TYPE, mediaType.toString())
        .body(FhirParser.serializeResource(documentReference, fhirMediaType));
  }

  @GetMapping(
      path = FHIR_DOCUMENT_REFERENCE_BINARY_READ,
      produces = {MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE})
  public ResponseEntity<InputStreamResource> getBinary(
      @PathVariable(name = DOCUMENT_ID_PATH_VARIABLE) String documentId,
      @RequestParam String path) {
    if (!path.equals(DOCUMENT_REFERENCE_PATH)) {
      log.error(WRONG_PATH_DELIVERED_ERROR_MSG);
      return ResponseEntity.badRequest().build();
    }

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + documentId + ".bin\"");
    return new ResponseEntity<>(
        new InputStreamResource(documentReferenceService.getBinary(documentId)),
        headers,
        HttpStatus.OK);
  }
}
