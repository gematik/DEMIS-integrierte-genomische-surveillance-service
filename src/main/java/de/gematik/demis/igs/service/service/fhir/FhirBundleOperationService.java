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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static de.gematik.demis.igs.service.exception.ErrorCode.MISSING_RESOURCE;
import static de.gematik.demis.igs.service.utils.Constants.EXTENSION_URL;
import static de.gematik.demis.igs.service.utils.Constants.EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE;
import static de.gematik.demis.igs.service.utils.Constants.LABORATORY_ID_URL;
import static de.gematik.demis.igs.service.utils.Constants.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM;
import static java.lang.String.format;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.CompositionRelatesToComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MolecularSequence;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Specimen.SpecimenProcessingComponent;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * Helper Service to handle all operation on a bundle. For example finding specific needed resources
 */
@Service
@RequiredArgsConstructor
public class FhirBundleOperationService {

  private static final String URL_SEQUENCE_DOCUMENT_REFERENCE =
      "https://demis.rki.de/fhir/igs/StructureDefinition/SequenceDocumentReference";

  private final FhirParser fhirParser;

  /**
   * Takes a Fhir parameters resource as string, parse it and returns the contained <Bundle>. It`s
   * expecting that there is only one <Bundle> in <Parameters>, else <IgsRequestException> is thrown
   *
   * @param content string representation of the Fhir parameters
   * @param mediaType the type of the content
   * @return <Bundle> resource
   */
  public Bundle parseBundleFromNotification(String content, MediaType mediaType) {
    return fhirParser.parseBundleOrParameter(content, mediaType.toString());
  }

  /**
   * Get the composition id from a <Bundle>
   *
   * @param bundle the <Bundle> to search in
   * @return the composition id
   */
  public String getCompositionId(Bundle bundle) {
    return getEntryOfType(bundle, Composition.class).orElseThrow().getIdPart();
  }

  /**
   * Parses the <Composition> resource from a <Bundle> and returns the relates to section
   *
   * @param bundle the <Bundle> to search in
   * @return an optional of the requested resource
   */
  public CompositionRelatesToComponent getRelatesToComponentFromComposition(Bundle bundle) {
    List<CompositionRelatesToComponent> relatesToList =
        getEntryOfType(bundle, Composition.class)
            .orElseThrow(
                () ->
                    new IgsServiceException(
                        MISSING_RESOURCE,
                        format("Expected resource %s in Bundle but not found", Composition.class)))
            .getRelatesTo();
    if (relatesToList.isEmpty()) {
      throw new IgsServiceException(
          MISSING_RESOURCE, "Notification have no related to in Composition");
    }
    return relatesToList.getFirst();
  }

  /**
   * Searches for the first <MolecularSequence> in given bundle and returns it
   *
   * @param bundle the bundle to search in
   * @return an optional of the requested resource
   */
  public Optional<MolecularSequence> getMolecularSequence(Bundle bundle) {
    return getEntryOfType(bundle, MolecularSequence.class);
  }

  /**
   * Searches for a <Organization> resource with <LABORATORY_ID_URL> as system.
   *
   * @param bundle the <Bundle> to search in
   * @return an optional of the requested resource
   */
  public Optional<Organization> getLaboratoryOrganization(Bundle bundle) {
    return getEntryOfType(
        bundle,
        Organization.class,
        org ->
            org.getIdentifier().stream()
                .anyMatch(identifier -> identifier.getSystem().equals(LABORATORY_ID_URL)));
  }

  /**
   * Searches for the first <DiagnosticReport> in given bundle and returns it
   *
   * @param bundle the <Bundle> to search in
   * @return an optional of the requested resource
   */
  public Optional<DiagnosticReport> getDiagnosticReport(Bundle bundle) {
    return getEntryOfType(bundle, DiagnosticReport.class);
  }

  /**
   * Searches for the first <Specimen> in given bundle and returns it
   *
   * @param bundle the <Bundle> to search in
   * @return an optional of the requested resource
   */
  public Optional<Specimen> getSpecimen(Bundle bundle) {
    return getEntryOfType(bundle, Specimen.class);
  }

  /**
   * Extracts the first resource of a specific type from a <Bundle> out of its entries. Optional a
   * filter can be given. If filter is null the filter doesn't have any effect. This is needed if
   * the resource could appear multiple times in the <Bundle> and the correct one needs to be
   * selected.
   *
   * @param bundle <Bundle> to extract the resource from
   * @param resource Type of the resource to extract
   * @param filter Filter to apply to the resource. If null than it have no effect
   * @return The first resource of the specified type that matches the filter and given type
   * @param <T> Type of the resource to extract
   */
  @SuppressWarnings("unchecked")
  private <T extends Resource> Optional<T> getEntryOfType(
      Bundle bundle, Class<T> resource, Predicate<T> filter) {

    if (filter == null) {
      filter = res -> true;
    }
    List<Bundle.BundleEntryComponent> entryList = bundle.getEntry();

    return entryList.stream()
        .map(BundleEntryComponent::getResource)
        .filter(resource::isInstance)
        .map(e -> (T) e)
        .filter(filter)
        .findFirst();
  }

  private <T extends Resource> Optional<T> getEntryOfType(Bundle bundle, Class<T> resource) {
    return getEntryOfType(bundle, resource, null);
  }

  /**
   * Adds the igs generated transactionId to the bundle at right place
   *
   * @param bundle the bundle to enrich
   * @param transactionId the transactionId that should be added
   */
  public void enrichBundleWithTransactionId(Bundle bundle, String transactionId) {
    Specimen specimen =
        getSpecimen(bundle)
            .orElseThrow(
                () ->
                    new IgsServiceException(
                        MISSING_RESOURCE,
                        "Tried to enrich bundle with transactionId in Specimen resource, but could not found needed resource"));
    final Extension extension = new Extension(EXTENSION_URL, new StringType(transactionId));
    SpecimenProcessingComponent component = new SpecimenProcessingComponent();
    component.addExtension(extension);

    specimen.getProcessing().add(component);
  }

  /**
   * Adds an entry to the bundle
   *
   * @param bundle the <Bundle> to add the entry to
   * @param entry the <BundleEntryComponent> to add
   */
  public void addEntry(Bundle bundle, BundleEntryComponent entry) {
    bundle.addEntry(entry);
    updated(bundle);
  }

  private void updated(final IBaseResource... resources) {
    for (final var resource : resources) {
      resource.getMeta().setLastUpdated(new Date());
    }
  }

  /**
   * Updates the composition reception timestamp
   *
   * @param bundle the bundle to update
   */
  public void updateCompositionReceptionTimeStamp(Bundle bundle) {
    Composition composition =
        getEntryOfType(bundle, Composition.class)
            .orElseThrow(
                () ->
                    new IgsServiceException(
                        MISSING_RESOURCE,
                        "Tried to update composition reception timestamp, but could not found needed resource"));
    Extension ex = new Extension();
    ex.setUrl(EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE);
    ex.setValue(new DateTimeType(new Date()));
    composition.addExtension(ex);
  }

  /**
   * Enriches the notification bundle with a new identifier and updates the composition reception
   * time stamp
   *
   * @param bundle the notification bundle
   */
  public void enrichNotification(Bundle bundle) {
    updateBundleId(bundle);
    updateCompositionReceptionTimeStamp(bundle);
    updated(bundle);
  }

  private void updateBundleId(final Bundle bundle) {
    bundle.setIdentifier(
        new Identifier()
            .setSystem(NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM)
            .setValue(UUID.randomUUID().toString()));
  }

  /**
   * Extracts the URLs of the DocumentReference resources in a FHIR bundle.
   *
   * @param bundle The FHIR bundle.
   * @return The extracted document references.
   */
  public List<String> determineDocumentReferenceUrls(final Bundle bundle) {
    List<String> documentReferenceUrls = null;
    Optional<MolecularSequence> molecularSequenceEntry =
        bundle.getEntry().stream()
            .filter(entry -> entry.getResource() instanceof MolecularSequence)
            .map(entry -> (MolecularSequence) entry.getResource())
            .findFirst();
    if (molecularSequenceEntry.isPresent()) {
      documentReferenceUrls =
          molecularSequenceEntry.get().getExtensionsByUrl(URL_SEQUENCE_DOCUMENT_REFERENCE).stream()
              .map(extension -> (Reference) extension.getValue())
              .map(Reference::getReference)
              .toList();
    }
    return documentReferenceUrls;
  }
}
