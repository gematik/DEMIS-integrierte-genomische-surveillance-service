package de.gematik.demis.igs.service.service.contextenrichment;

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

import static de.gematik.demis.igs.service.parser.FhirParser.deserializeResource;
import static org.apache.commons.lang3.StringUtils.isBlank;

import de.gematik.demis.igs.service.service.fhir.FhirBundleOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Provenance;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * Service used to connect to the context enrichment service and enrich the bundle with context
 * information
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextEnrichmentService {

  private static final String PROFILE_BASE_URL = "https://demis.rki.de/fhir/";

  private final ContextEnrichmentServiceClient contextEnrichmentServiceClient;
  private final FhirBundleOperationService fhirBundleOperationService;

  /**
   * Enriches the bundle with context information
   *
   * @param bundle <Bundle> to enrich
   * @param authorization the authorization header
   */
  public void enrichBundleWithContextInformation(final Bundle bundle, final String authorization) {
    if (isBlank(authorization)) {
      log.warn("Authorization is null but required by CES. No enrichment with CES!");
      return;
    }
    try {
      final String resp =
          contextEnrichmentServiceClient.enrichBundleWithContextInformation(
              authorization, fhirBundleOperationService.getCompositionId(bundle));
      final Provenance provenance =
          deserializeResource(resp, MediaType.APPLICATION_JSON, Provenance.class);
      final BundleEntryComponent entry =
          new BundleEntryComponent()
              .setResource(provenance)
              .setFullUrl(PROFILE_BASE_URL + provenance.getId());
      fhirBundleOperationService.addEntry(bundle, entry);
    } catch (Exception e) {
      log.error("Error while enrich bundle: ", e);
    }
  }
}
