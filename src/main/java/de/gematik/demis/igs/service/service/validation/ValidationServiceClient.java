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

import static de.gematik.demis.igs.service.exception.ServiceCallErrorCode.VS;

import de.gematik.demis.service.base.feign.annotations.ErrorCode;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "validation-service", url = "${igs.validation.url}")
public interface ValidationServiceClient {
  String HEADER_FHIR_API_VERSION = "x-fhir-api-version";
  // Can be removed with FEATURE_FLAG_NEW_API_ENDPOINTS
  String HEADER_FHIR_PROFILE_OLD = "fhirProfile";
  String HEADER_FHIR_PROFILE = "x-fhir-profile";

  @PostMapping(
      value = "/$validate",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ErrorCode(VS)
  Response validateJsonBundle(@RequestHeader HttpHeaders headers, String bundleAsJson);

  @PostMapping(
      value = "/$validate",
      consumes = MediaType.APPLICATION_XML_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ErrorCode(VS)
  Response validateXmlBundle(@RequestHeader HttpHeaders headers, String bundleAsXml);
}
