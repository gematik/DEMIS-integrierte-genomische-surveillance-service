package de.gematik.demis.igs.service.service.igs;

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

import static de.gematik.demis.igs.service.utils.Constants.LABORATORY_ID_URL;
import static java.lang.String.format;

import de.gematik.demis.igs.service.exception.ErrorCode;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.fhir.FhirBundleOperationService;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.stereotype.Service;

/** Generate the TransactionId out of a bundle */
@Slf4j
@Service
@RequiredArgsConstructor
public class IgsTransactionIdGeneratorService {

  public static final String DEFAULT_LAB_ID = "99999";
  private static final String ID_PREFIX = "IGS";
  private static final String CODE_URL =
      "https://demis.rki.de/fhir/CodeSystem/notificationCategory";
  private final FhirBundleOperationService fhirBundleOperationService;
  private Pattern sequencingLabIdPattern = Pattern.compile("\\d{5}");

  /**
   * Generates <TransactionId> out of given <Bundle>. The <TransactionId> is build with schema:
   * <IGS_PREFIX><DEMIS_USERNAME><PATHOGEN_CODE><UUID> If no <DEMIS_USERNAME> is available the
   * default 99999 is added
   *
   * @param bundle the <Bundle> the data got extracted from
   * @return build <TransactionId>
   */
  public String generateTransactionId(Bundle bundle) {
    final String demisUsername = getLaboratoryId(bundle);
    final String pathogenCode = getPathogenCode(bundle);

    return format("%s-%s-%s-%s", ID_PREFIX, demisUsername, pathogenCode, UUID.randomUUID())
        .toUpperCase();
  }

  private String getLaboratoryId(Bundle bundle) {

    Optional<Organization> orgOptional =
        fhirBundleOperationService.getLaboratoryOrganization(bundle);
    if (orgOptional.isEmpty()) {
      return DEFAULT_LAB_ID;
    }

    String sequencingLabId =
        orgOptional.get().getIdentifier().stream()
            .filter(identifierEntry -> identifierEntry.getSystem().equals(LABORATORY_ID_URL))
            .findFirst()
            .orElseThrow()
            .getValue();
    if (!sequencingLabIdPattern.matcher(sequencingLabId).matches()) {
      throw new IgsServiceException(
          ErrorCode.INVALID_LAB_ID,
          format("The lab sequence ID must be a 5-digit number, but found: %s", sequencingLabId));
    }
    return sequencingLabId;
  }

  private String getPathogenCode(Bundle bundle) {
    final DiagnosticReport diagnosticReport =
        fhirBundleOperationService
            .getDiagnosticReport(bundle)
            .orElseThrow(
                () ->
                    new IgsServiceException(
                        ErrorCode.MISSING_RESOURCE,
                        format(
                            "Expected resource %s needed for pathogen code in Bundle, but not found",
                            DiagnosticReport.class)));

    return diagnosticReport.getCode().getCoding().stream()
        .filter(codingEntry -> codingEntry.getSystem().equals(CODE_URL))
        .findFirst()
        .orElseThrow()
        .getCode();
  }
}
