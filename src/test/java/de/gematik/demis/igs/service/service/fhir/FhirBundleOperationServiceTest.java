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

package de.gematik.demis.igs.service.service.fhir;

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

import static de.gematik.demis.igs.service.utils.Constants.EXTENSION_URL;
import static de.gematik.demis.igs.service.utils.Constants.EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION_BUNDLE;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION_WITHOUT_COMPOSITION;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION_WITHOUT_DIAGNOSTIC_REPORT;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION_WITHOUT_MOLECULAR_SEQUENCE;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION_WITHOUT_RELATES_TO;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION_WITHOUT_SPECIMEN;
import static util.BaseUtil.PATH_TO_INVALID_IGS_NOTIFICATION;
import static util.BaseUtil.PATH_TO_ORGANIZATION_RESSOURCE;
import static util.BaseUtil.PROVENANCE_RESOURCE;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.ParsingException;
import de.gematik.demis.igs.service.exception.ErrorCode;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.CompositionRelatesToComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.MolecularSequence;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Specimen.SpecimenProcessingComponent;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import util.BaseUtil;

class FhirBundleOperationServiceTest {

  public static final String EXAMPLE_TRANSACTION_ID = "AnyTransactionId";
  BaseUtil testUtils = new BaseUtil();
  FhirBundleOperationService underTest =
      new FhirBundleOperationService(new FhirParser(FhirContext.forR4Cached()));

  @Test
  @SneakyThrows
  void shouldExtractBundleCorrectly() {
    Bundle bundle =
        underTest.parseBundleFromNotification(
            testUtils.readFileToString(PATH_TO_IGS_NOTIFICATION), APPLICATION_XML);
    assertThat(bundle).isNotNull();
  }

  @Test
  @SneakyThrows
  void shouldBundleIfResourceIsBundle() {
    Bundle bundle =
        underTest.parseBundleFromNotification(
            testUtils.readFileToString(PATH_TO_IGS_NOTIFICATION_BUNDLE), APPLICATION_XML);
    assertThat(bundle).isNotNull();
  }

  @Test
  @SneakyThrows
  void shouldThrowErrorIfInputInvalid() {
    String content = testUtils.readFileToString(PATH_TO_INVALID_IGS_NOTIFICATION);
    assertThrows(
        DataFormatException.class,
        () -> underTest.parseBundleFromNotification(content, APPLICATION_XML));
  }

  @Test
  void shouldExtractRelatedToCorrectly() {
    CompositionRelatesToComponent relatedTo =
        underTest.getRelatesToComponentFromComposition(testUtils.getDefaultBundle());
    assertThat(relatedTo)
        .usingRecursiveComparison()
        .isEqualTo(testUtils.defaultRelatesToComponent());
  }

  @Test
  void shouldThrowExceptionIfCompositionNotExist() {
    Bundle bundle = testUtils.getBundleFromFile(PATH_TO_IGS_NOTIFICATION_WITHOUT_COMPOSITION);
    IgsServiceException ex =
        assertThrows(
            IgsServiceException.class,
            () -> underTest.getRelatesToComponentFromComposition(bundle));
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MISSING_RESOURCE.toString());
  }

  @Test
  void shouldThrowExceptionIfRelatedToNotExist() {
    Bundle bundle = testUtils.getBundleFromFile(PATH_TO_IGS_NOTIFICATION_WITHOUT_RELATES_TO);
    IgsServiceException ex =
        assertThrows(
            IgsServiceException.class,
            () -> underTest.getRelatesToComponentFromComposition(bundle));
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MISSING_RESOURCE.toString());
  }

  @Test
  void shouldReturnMolecularSequenceCorrectly() {
    Optional<MolecularSequence> molecularSequence =
        underTest.getMolecularSequence(testUtils.getDefaultBundle());
    assertThat(molecularSequence).isPresent();
  }

  @Test
  void shouldReturnEmptyOptionalIfMolecularSequenceIsMissing() {
    Optional<MolecularSequence> molecularSequence =
        underTest.getMolecularSequence(
            testUtils.getBundleFromFile(PATH_TO_IGS_NOTIFICATION_WITHOUT_MOLECULAR_SEQUENCE));
    assertThat(molecularSequence).isEmpty();
  }

  @Test
  void shouldReturnDiagnosticReportCorrectly() {
    Optional<DiagnosticReport> molecularSequence =
        underTest.getDiagnosticReport(testUtils.getDefaultBundle());
    assertThat(molecularSequence).isPresent();
  }

  @Test
  void shouldReturnEmptyOptionalDiagnosticReportIsMissing() {
    Optional<DiagnosticReport> diagnosticReport =
        underTest.getDiagnosticReport(
            testUtils.getBundleFromFile(PATH_TO_IGS_NOTIFICATION_WITHOUT_DIAGNOSTIC_REPORT));
    assertThat(diagnosticReport).isEmpty();
  }

  @Test
  void shouldReturnSpecimenCorrectly() {
    Optional<Specimen> specimen = underTest.getSpecimen(testUtils.getDefaultBundle());
    assertThat(specimen).isPresent();
  }

  @Test
  void shouldReturnEmptyOptionalSpecimenIsMissing() {
    Optional<Specimen> specimen =
        underTest.getSpecimen(
            testUtils.getBundleFromFile(PATH_TO_IGS_NOTIFICATION_WITHOUT_SPECIMEN));
    assertThat(specimen).isEmpty();
  }

  @Test
  @SneakyThrows
  void shouldSetTransactionCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.enrichBundleWithTransactionId(bundle, EXAMPLE_TRANSACTION_ID);
    Specimen specimen =
        bundle.getEntry().stream()
            .map(BundleEntryComponent::getResource)
            .filter(resource -> resource instanceof Specimen)
            .map(Specimen.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(specimen.getProcessing())
        .extracting(SpecimenProcessingComponent::getExtension)
        .filteredOn(e -> !e.isEmpty())
        .map(List::getFirst)
        .extracting(Extension::getUrl)
        .contains(EXTENSION_URL);

    assertThat(specimen.getProcessing())
        .extracting(SpecimenProcessingComponent::getExtension)
        .filteredOn(e -> !e.isEmpty())
        .map(List::getFirst)
        .extracting(Extension::getValueAsPrimitive)
        .map(StringType.class::cast)
        .map(StringType::getValue)
        .contains(EXAMPLE_TRANSACTION_ID);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfSpecimenNotAvailableAndTransactionIdCouldNotAppend() {
    Bundle bundle = testUtils.getBundleFromFile(PATH_TO_IGS_NOTIFICATION_WITHOUT_SPECIMEN);
    IgsServiceException ex =
        assertThrows(
            IgsServiceException.class,
            () -> underTest.enrichBundleWithTransactionId(bundle, EXAMPLE_TRANSACTION_ID));
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MISSING_RESOURCE.toString());
  }

  @Test
  @SneakyThrows
  void addEntryCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    Provenance provenance = testUtils.readFileToResource(PROVENANCE_RESOURCE, Provenance.class);
    BundleEntryComponent entryComponent = new BundleEntryComponent();
    entryComponent.setResource(provenance);
    int countOfEntries = bundle.getEntry().size();
    underTest.addEntry(bundle, entryComponent);
    assertThat(bundle.getEntry())
        .as("The bundle does not get the enriched entry added")
        .hasSize(countOfEntries + 1);
    assertThat(bundle.getEntry())
        .extracting("resource")
        .extracting("resourceType")
        .map(Object::toString)
        .contains("Provenance");
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfNeitherBundleOrParameterGotParsed() {
    String organizationResourceString = testUtils.readFileToString(PATH_TO_ORGANIZATION_RESSOURCE);
    assertThrows(
        ParsingException.class,
        () -> underTest.parseBundleFromNotification(organizationResourceString, APPLICATION_XML));
  }

  @Test
  @SneakyThrows
  void shouldUpdateIdentifierOfBundleWhileEnrichment() {
    Bundle bundle = testUtils.getDefaultBundle();
    String before = bundle.getIdentifier().getValue();
    underTest.enrichNotification(bundle);
    assertThat(bundle.getIdentifier().getValue()).isNotEqualTo(before);
    assertThat(bundle.getIdentifier().getValue())
        .matches("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$");
  }

  @Test
  @SneakyThrows
  void shouldUpdateLastUpdatedBundleWhileEnrichment() {
    Bundle bundle = testUtils.getDefaultBundle();
    Date before = bundle.getMeta().getLastUpdated();
    underTest.enrichNotification(bundle);
    assertThat(bundle.getMeta().getLastUpdated()).isNotEqualTo(before);
  }

  @Test
  @SneakyThrows
  void shouldUpdateCompositionsLastUpdateWhileEnrichment() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.enrichNotification(bundle);
    Composition composition = (Composition) bundle.getEntry().getFirst().getResource();
    Extension extension =
        composition.getExtension().stream()
            .filter(e -> e.getUrl().equals(EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE))
            .findFirst()
            .orElseThrow();

    DateTimeType type = (DateTimeType) extension.getValue();
    assertThat(type.getValue()).isCloseTo(new Date(), 2000L);
  }

  @Test
  void shouldThrowExceptionIfCompositionNotPresent() {
    Bundle bundle = testUtils.getDefaultBundle();
    bundle.getEntry().removeIf(e -> e.getResource().getResourceType().name().equals("Composition"));

    IgsServiceException ex =
        assertThrows(IgsServiceException.class, () -> underTest.enrichNotification(bundle));
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MISSING_RESOURCE.toString());
  }

  @Test
  void shouldDetermineDocumentReferenceUrls() {
    Bundle bundle = testUtils.getDefaultBundle();

    List<String> documentReferenceUrls = underTest.determineDocumentReferenceUrls(bundle);

    assertThat(documentReferenceUrls)
        .isNotNull()
        .hasSize(2)
        .containsExactly(
            "https://ingress.local/surveillance/notification-sequence/fhir/DocumentReference/ecd3f1f0-b6b6-46e0-b721-2d9869ab8195",
            "https://ingress.local/surveillance/notification-sequence/fhir/DocumentReference/fde4g2g1-b6b6-46e0-b721-2d9869ab8195");
  }
}
