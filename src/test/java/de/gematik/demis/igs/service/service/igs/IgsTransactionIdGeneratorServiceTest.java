package de.gematik.demis.igs.service.service.igs;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.logging.LoggingContext;
import de.gematik.demis.igs.service.service.fhir.FhirBundleOperationService;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import util.BaseUtil;

class IgsTransactionIdGeneratorServiceTest {

  private static final Pattern ID_PATTERN =
      Pattern.compile(
          "IGS-10285-CVDP-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}");
  private static final String CODE_URL =
      "https://demis.rki.de/fhir/CodeSystem/notificationCategory";
  private final BaseUtil testUtil = new BaseUtil();
  private final FhirBundleOperationService fhirBundleOperationService =
      mock(FhirBundleOperationService.class);

  IgsTransactionIdGeneratorService underTest =
      new IgsTransactionIdGeneratorService(fhirBundleOperationService);

  LoggingContext context = new LoggingContext();

  @BeforeEach
  void setUp() {
    lenient()
        .when(fhirBundleOperationService.getSpecimen(any()))
        .thenReturn(Optional.of(testUtil.defaultSpecimen()));
    lenient()
        .when(fhirBundleOperationService.getDiagnosticReport(any()))
        .thenReturn(Optional.of(testUtil.defaultDiagnosticReport()));
    lenient()
        .when(fhirBundleOperationService.determineNotifierFacility(any()))
        .thenReturn(Optional.of(testUtil.defaultOrganization()));
    lenient()
        .when(fhirBundleOperationService.getMolecularSequence(any()))
        .thenReturn(Optional.of(testUtil.defaultMolecularSequence()));
    lenient()
        .when(fhirBundleOperationService.getRelatesToComponentFromComposition(any()))
        .thenReturn(testUtil.defaultRelatesToComponent());

    context = new LoggingContext();
    ReflectionTestUtils.setField(underTest, "context", context);
  }

  @Test
  void givenValidNotificationShouldEnrichWithDemisSequenceId() {
    String transactionId = underTest.generateTransactionId(testUtil.getDefaultBundle());
    assertThat(transactionId).matches(ID_PATTERN);
  }

  @Test
  void shouldThrowMissingIdentifierForNotifierFacility() {
    Organization organizationWithoutIdentifier =
        testUtil.defaultOrganization().setIdentifier(List.of());

    when(fhirBundleOperationService.determineNotifierFacility(any()))
        .thenReturn(Optional.of(organizationWithoutIdentifier));

    assertThatThrownBy(() -> underTest.generateTransactionId(testUtil.getDefaultBundle()))
        .isInstanceOf(IgsServiceException.class)
        .hasMessage("Identifier missing for notifier facility.");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "asdfg", "1234", "123456", "1234a", "a2345", "12c45",
      })
  public void shouldThrowExceptionIfSequencingLabIdInWrongFormat(String invalidValue) {
    Organization organizationWithInvalidLabId =
        testUtil
            .defaultOrganization()
            .setIdentifier(
                List.of(
                    testUtil
                        .defaultOrganization()
                        .getIdentifier()
                        .getFirst()
                        .setValue(invalidValue)));
    when(fhirBundleOperationService.determineNotifierFacility(any()))
        .thenReturn(Optional.of(organizationWithInvalidLabId));
    assertThatThrownBy(() -> underTest.generateTransactionId(testUtil.getDefaultBundle()))
        .isInstanceOf(IgsServiceException.class)
        .hasMessage(
            "The DEMIS Participant ID must be a 5-digit number, but found: " + invalidValue);
  }

  @Test
  void shouldWritePathogenCodeToLoggingContext() {
    DiagnosticReport diagnosticReport = new DiagnosticReport();
    diagnosticReport.setCode(
        new CodeableConcept().addCoding(new Coding().setSystem(CODE_URL).setCode("cvdp")));

    when(fhirBundleOperationService.getDiagnosticReport(any()))
        .thenReturn(Optional.of(diagnosticReport));

    underTest.generateTransactionId(testUtil.getDefaultBundle());

    assertThat(context.getPathogenCode()).isEqualTo("cvdp");
  }

  @Test
  void shouldThrowMissingResourceWhenDiagnosticReportIsMissing() {
    when(fhirBundleOperationService.getDiagnosticReport(any())).thenReturn(Optional.empty());
    Assertions.assertThatThrownBy(
            () -> underTest.generateTransactionId(testUtil.getDefaultBundle()))
        .isInstanceOf(IgsServiceException.class);
  }
}
