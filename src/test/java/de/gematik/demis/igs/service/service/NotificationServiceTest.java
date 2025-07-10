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

import static de.gematik.demis.igs.service.parser.FhirParser.serializeResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.igs.service.exception.ErrorCode;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.igs.service.service.fhir.FhirBundleOperationService;
import de.gematik.demis.igs.service.service.fhirstorage.FhirStorageService;
import de.gematik.demis.igs.service.service.igs.IgsTransactionIdGeneratorService;
import de.gematik.demis.igs.service.service.validation.NotificationValidatorService;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import util.BaseUtil;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private static final String token = "token";
  private final MediaType mediaType = MediaType.APPLICATION_XML;
  private static final String TEST_TRANSACTION_ID = "TEST_TRANSACTION_ID";
  private final BaseUtil testUtil = new BaseUtil();
  public final String DEFAULT_BUNDLE = testUtil.getDefaultBundleAsString();
  private final FhirBundleOperationService fhirBundleOperationService =
      mock(FhirBundleOperationService.class);
  private final IgsTransactionIdGeneratorService igsTransactionIdGeneratorService =
      mock(IgsTransactionIdGeneratorService.class);
  private final FhirStorageService fhirStorageService = mock(FhirStorageService.class);
  private final ContextEnrichmentService contextEnrichmentService =
      mock(ContextEnrichmentService.class);
  private final NotificationValidatorService notificationValidatorService =
      mock(NotificationValidatorService.class);
  private final NotificationService service =
      new NotificationService(
          notificationValidatorService,
          fhirBundleOperationService,
          igsTransactionIdGeneratorService,
          fhirStorageService,
          contextEnrichmentService);
  @Captor ArgumentCaptor<Bundle> bundleCaptor;

  @BeforeEach
  void setUp() {
    lenient()
        .when(fhirBundleOperationService.parseBundleFromNotification(DEFAULT_BUNDLE, mediaType))
        .thenReturn(testUtil.getDefaultBundle());
    lenient()
        .when(igsTransactionIdGeneratorService.generateTransactionId(any()))
        .thenReturn(TEST_TRANSACTION_ID);
    lenient()
        .when(fhirBundleOperationService.getSpecimen(any()))
        .thenReturn(Optional.of(testUtil.defaultSpecimen()));
    lenient()
        .when(fhirBundleOperationService.getDiagnosticReport(any()))
        .thenReturn(Optional.of(testUtil.defaultDiagnosticReport()));
    lenient()
        .when(fhirBundleOperationService.getLaboratoryOrganization(any()))
        .thenReturn(Optional.of(testUtil.defaultOrganization()));
    lenient()
        .when(fhirBundleOperationService.getMolecularSequence(any()))
        .thenReturn(Optional.of(testUtil.defaultMolecularSequence()));
    lenient()
        .when(fhirBundleOperationService.getRelatesToComponentFromComposition(any()))
        .thenReturn(testUtil.defaultRelatesToComponent());
    service.init();
  }

  @Test
  void shouldSetDeliveredTransactionIdToCorrectIdentifier() {
    when(fhirBundleOperationService.getLaboratoryOrganization(any())).thenReturn(Optional.empty());

    Parameters response = service.process(DEFAULT_BUNDLE, mediaType, token);

    Optional<ParametersParameterComponent> transactionId =
        response.getParameter().stream()
            .filter(e -> e.getName().equals("transactionID"))
            .findFirst();
    assertThat(transactionId).isPresent();
    assertThat(((Identifier) transactionId.orElseThrow().getValue()).getValue())
        .isEqualTo(TEST_TRANSACTION_ID);
  }

  @Test
  void shouldThrowIgsServiceExceptionIfRelatesToComponentMissingInBundle() {
    when(fhirBundleOperationService.getRelatesToComponentFromComposition(any()))
        .thenThrow(new IgsServiceException(ErrorCode.MISSING_RESOURCE, "No Composition found"));
    assertThrows(
        IgsServiceException.class, () -> service.process(DEFAULT_BUNDLE, mediaType, token));
  }

  @Test
  void shouldThrowIgsServiceExceptionIfMolecularSequenceIsMissingInBundle() {
    when(fhirBundleOperationService.getMolecularSequence(any())).thenReturn(Optional.empty());
    assertThrows(
        IgsServiceException.class, () -> service.process(DEFAULT_BUNDLE, mediaType, token));
  }

  @Test
  void shouldThrowIgsServiceExceptionIfProfileNotSetCorrectly() {
    Bundle bundle = testUtil.getDefaultBundle();
    bundle.getMeta().setProfile(List.of(new CanonicalType("This_is_a_wrong_profile")));
    String changedBundle = serializeResource(bundle, mediaType);
    assertThrows(IgsServiceException.class, () -> service.process(changedBundle, mediaType, token));
  }

  @Test
  void shouldCallEnrichment() {
    service.process(DEFAULT_BUNDLE, mediaType, token);
    verify(fhirBundleOperationService, times(1)).enrichNotification(bundleCaptor.capture());
    assertThat(bundleCaptor.getValue().getId()).isEqualTo(testUtil.getDefaultBundle().getId());
  }
}
