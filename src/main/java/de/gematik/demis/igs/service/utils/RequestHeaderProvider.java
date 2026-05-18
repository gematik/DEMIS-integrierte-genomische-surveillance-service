package de.gematik.demis.igs.service.utils;

/*-
 * #%L
 * Integrierte-Genomische-Surveillance-Service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static java.util.Optional.ofNullable;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

@Service
@RequiredArgsConstructor
@RequestScope
public class RequestHeaderProvider {
  protected static final String HEADER_FHIR_API_VERSION_LEGACY = "x-fhir-api-version";
  protected static final String HEADER_FHIR_PACKAGE_VERSION = "x-fhir-package-version";
  protected static final String HEADER_FHIR_PROFILE_LEGACY = "x-fhir-profile";
  protected static final String HEADER_FHIR_PACKAGE = "x-fhir-package";

  private final HttpServletRequest httpServletRequest;

  public @Nullable List<String> receiveApiVersionsFromRequest() {
    final List<String> apiVersions = headersFromRequestByName(HEADER_FHIR_PACKAGE_VERSION);
    if (apiVersions != null && !apiVersions.isEmpty()) {
      return apiVersions;
    }
    return headersFromRequestByName(HEADER_FHIR_API_VERSION_LEGACY);
  }

  public @Nullable List<String> receiveFhirProfileFromRequest() {
    final List<String> apiVersions = headersFromRequestByName(HEADER_FHIR_PACKAGE);
    if (apiVersions != null && !apiVersions.isEmpty()) {
      return apiVersions;
    }
    return headersFromRequestByName(HEADER_FHIR_PROFILE_LEGACY);
  }

  private @Nullable List<String> headersFromRequestByName(@Nonnull String headerName) {
    return ofNullable(httpServletRequest.getHeader(headerName)).map(List::of).orElse(null);
  }
}
