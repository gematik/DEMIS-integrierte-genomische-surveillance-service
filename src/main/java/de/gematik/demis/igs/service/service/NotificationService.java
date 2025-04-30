package de.gematik.demis.igs.service.service;

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

import static de.gematik.demis.igs.service.exception.ErrorCode.MISSING_RESOURCE;
import static de.gematik.demis.igs.service.exception.ErrorCode.PROFILE_NOT_SUPPORTED;
import static java.lang.String.format;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.igs.service.service.fhir.FhirBundleOperationService;
import de.gematik.demis.igs.service.service.igs.IgsTransactionIdGeneratorService;
import de.gematik.demis.igs.service.service.ncapi.NcapiService;
import de.gematik.demis.igs.service.service.validation.NotificationValidatorService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition.CompositionRelatesToComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MolecularSequence;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/** Handles IGS Notification operations, i.e. Sequence ID generation, communication with NCAPI */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  public static final String
      HTTPS_DEMIS_RKI_DE_FHIR_STRUCTURE_DEFINITION_NOTIFICATION_SEQUENCE_REQUEST =
          "https://demis.rki.de/fhir/igs/StructureDefinition/NotificationBundleSequence";
  private final NotificationValidatorService notificationValidatorService;
  private final FhirBundleOperationService fhirBundleOperationService;
  private final IgsTransactionIdGeneratorService igsTransactionIdGeneratorService;
  private final NcapiService ncapiService;
  private final ContextEnrichmentService contextEnrichmentService;
  private Pattern pattern;

  @PostConstruct
  void init() {
    pattern =
        Pattern.compile(
            "(?s)meta.*profile.*"
                + Pattern.quote(
                    HTTPS_DEMIS_RKI_DE_FHIR_STRUCTURE_DEFINITION_NOTIFICATION_SEQUENCE_REQUEST)
                + "(?!\\w|/)");
  }

  /**
   * Enriches incoming notification bundle with DEMIS Sequence ID and passes it down to NCAPI for
   * further operations
   *
   * @param content serialized notification bundle
   * @param mediaType mime type used for the deserialization
   * @return notification bundle representation with a generated DEMIS Sequence ID
   */
  public Parameters process(String content, MediaType mediaType, String token) {
    preCheckProfile(content);
    OperationOutcome validationOutcome =
        notificationValidatorService.validateFhir(content, mediaType);
    Bundle bundle = fhirBundleOperationService.parseBundleFromNotification(content, mediaType);
    notificationValidatorService.validateDocumentReferences(bundle);
    fhirBundleOperationService.enrichNotification(bundle);
    final String transactionId = igsTransactionIdGeneratorService.generateTransactionId(bundle);
    contextEnrichmentService.enrichBundleWithContextInformation(bundle, token);
    fhirBundleOperationService.enrichBundleWithTransactionId(bundle, transactionId);
    ncapiService.sendNotificationToNcapi(bundle);
    return buildResponse(transactionId, bundle, validationOutcome);
  }

  private void preCheckProfile(final String fhirNotification) {
    Matcher matcher = pattern.matcher(fhirNotification);
    if (!matcher.find()) {
      throw new IgsServiceException(
          PROFILE_NOT_SUPPORTED, "bundle profile not supported or missing(pre-check).");
    }
  }

  @NotNull
  private Parameters buildResponse(
      String transactionId, Bundle bundle, OperationOutcome validationOutcome) {
    // Build response
    Parameters response = new Parameters();
    ParametersParameterComponent transactionIdComponent = getTransactionComponent(transactionId);
    ParametersParameterComponent submitterGeneratedNotificationIdComponent =
        getSubmitterGeneratedNotificationID(bundle);
    ParametersParameterComponent labSequenceIdComponent = getLabSequenceId(bundle);
    ParametersParameterComponent operationOutcome = new ParametersParameterComponent();
    operationOutcome.setResource(validationOutcome);
    response.addParameter(transactionIdComponent);
    response.addParameter(submitterGeneratedNotificationIdComponent);
    response.addParameter(labSequenceIdComponent);
    response.addParameter(operationOutcome);

    return response;
  }

  private ParametersParameterComponent getTransactionComponent(String sequenceId) {
    return createComponent("transactionID", sequenceId);
  }

  private ParametersParameterComponent getSubmitterGeneratedNotificationID(Bundle bundle) {
    CompositionRelatesToComponent compositionRelatesToComponent =
        fhirBundleOperationService.getRelatesToComponentFromComposition(bundle);
    return createComponent(
        "submitterGeneratedNotificationID",
        compositionRelatesToComponent.getTargetReference().getIdentifier().getValue());
  }

  private ParametersParameterComponent getLabSequenceId(Bundle bundle) {
    MolecularSequence molecularSequence =
        fhirBundleOperationService
            .getMolecularSequence(bundle)
            .orElseThrow(
                () ->
                    new IgsServiceException(
                        MISSING_RESOURCE,
                        format(
                            "Expected resource %s needed for LabSequenceId in Bundle, but not found",
                            MolecularSequence.class)));
    return createComponent(
        "labSequenceID", molecularSequence.getIdentifier().getFirst().getValue());
  }

  private ParametersParameterComponent createComponent(String name, String value) {
    ParametersParameterComponent component = new ParametersParameterComponent();
    component.setName(name);
    Identifier identifier = new Identifier().setValue(value);
    component.setValue(identifier);

    return component;
  }
}
