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

package util;

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

import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNull;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.igs.service.utils.Pair;
import feign.Request;
import feign.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.CompositionRelatesToComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.MolecularSequence;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

public class BaseUtil {

  private static final FhirContext fhirContext = FhirContext.forR4Cached();

  public static final String PATH_TO_DOCUMENT_REFERENCE_JSON =
      "createDocumentReference/createDocumentReference.json";
  public static final String PATH_TO_DOCUMENT_REFERENCE_XML =
      "createDocumentReference/createDocumentReference.xml";
  public static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_CONTENT =
      "createDocumentReference/documentReferenceWithoutContent.json";
  public static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_INVALID_DATE =
      "createDocumentReference/documentReferenceInvalidDate.json";
  public static final String PATH_TO_FASTA = "sampleSequenceData/Sample.fa";
  public static final String PATH_TO_FASTA_ONLY_N = "sampleSequenceData/Sample_only_n.fa";
  public static final String PATH_TO_FASTA_WITH_PATHOGEN_SHORT =
      "sampleSequenceData/SamplePathogenShort.fa";
  public static final String PATH_TO_FASTA_WITH_PATHOGEN_LONG =
      "sampleSequenceData/SamplePathogenLong.fa";
  public static final String PATH_TO_FASTA_WITH_PATHOGEN_N51 =
      "sampleSequenceData/SamplePathogenN.fa";
  public static final String PATH_TO_FASTA_GZIP = "sampleSequenceData/Sample.fa.gz";
  public static final String PATH_TO_FASTA_ZIP = "sampleSequenceData/Sample.fa.zip";
  public static final String PATH_TO_FASTQ = "sampleSequenceData/Sample12346_R2.fastq";
  public static final String PATH_TO_FASTQ_GZIP = "sampleSequenceData/Sample12346_R2.fastq.gz";
  public static final String PATH_TO_FASTA_INVALID = "sampleSequenceData/SampleInvalid.fa";
  public static final String PATH_TO_FASTQ_INVALID =
      "sampleSequenceData/Sample12346_R2Invalid.fastq";
  public static final String PATH_TO_GZIP_INVALID =
      "sampleSequenceData/Sample.fastq_corrupt_gzip.gzip";
  public static final String PATH_TO_IGS_NOTIFICATION = "igsNotification/notification.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WRONG_SEQUENCEING_LAB_ID =
      "igsNotification/notificationWithInvalidSequenceLabId.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_BUNDLE =
      "igsNotification/notificationAsBundle.xml";
  public static final String PATH_TO_INVALID_IGS_NOTIFICATION =
      "igsNotification/notificationInvalid.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_COMPOSITION =
      "igsNotification/notificationWithoutComposition.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_RELATES_TO =
      "igsNotification/notificationWithoutRelatesTo.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_MOLECULAR_SEQUENCE =
      "igsNotification/notificationWithoutMolecularSequence.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_DIAGNOSTIC_REPORT =
      "igsNotification/notificationWithoutDiagnosticReport.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_SPECIMEN =
      "igsNotification/notificationWithoutSpecimen.xml";
  public static final String PATH_TO_ORGANIZATION_RESSOURCE = "igsNotification/organization.xml";
  public static final String PROVENANCE_RESOURCE = "provenance/provenanceResource.json";

  private final Parameters parameter;

  @SneakyThrows
  public BaseUtil() {
    parameter =
        fhirContext
            .newXmlParser()
            .parseResource(Parameters.class, readFileToString(PATH_TO_IGS_NOTIFICATION));
  }

  public Bundle getBundleFromJsonString(String value) {
    return fhirContext.newJsonParser().parseResource(Bundle.class, value);
  }

  public Bundle getDefaultBundle() {
    return (Bundle) parameter.getParameter().getFirst().getResource();
  }

  public String getDefaultBundleAsString() {
    return fhirContext
        .newJsonParser()
        .encodeResourceToString(parameter.getParameter().getFirst().getResource());
  }

  public String getDefaultCompositionId() {
    return getDefaultBundle().getEntry().getFirst().getResource().getIdPart();
  }

  @SneakyThrows
  public Bundle getBundleFromFile(String path) {
    return (Bundle)
        ((Parameters) fhirContext.newXmlParser().parseResource(readFileToString(path)))
            .getParameter()
            .getFirst()
            .getResource();
  }

  @SneakyThrows
  public <T extends IBaseResource> T readFileToResource(String filePath, Class<T> clazz) {
    String resource = readFileToString(filePath);
    if (resource.startsWith("<")) {
      return fhirContext.newXmlParser().parseResource(clazz, resource);
    }
    return fhirContext.newJsonParser().parseResource(clazz, resource);
  }

  public String readFileToString(String relativePath) throws IOException, URISyntaxException {
    Path pathToDocumentReferenceRequest =
        Path.of(requireNonNull(getClass().getClassLoader().getResource(relativePath)).toURI());
    return readString(pathToDocumentReferenceRequest);
  }

  public byte[] readFileToByteArray(String relativePath) throws IOException, URISyntaxException {
    return readFileToString(relativePath).getBytes();
  }

  public InputStream readFileToInputStream(String relativePath) throws IOException {
    ClassPathResource resource = new ClassPathResource(relativePath);
    return resource.getInputStream();
  }

  public Pair getFirstBytesOfFile(String relativePath) throws IOException {
    InputStream stream = readFileToInputStream(relativePath);
    return new Pair(String.valueOf(stream.read()), String.valueOf(stream.read()));
  }

  public long getFileSize(String relativePath) throws URISyntaxException {
    return new File(requireNonNull(getClass().getClassLoader().getResource(relativePath)).toURI())
        .length();
  }

  public String getFilePath(String relativePath) throws URISyntaxException {
    return requireNonNull(getClass().getClassLoader().getResource(relativePath)).toURI().getPath();
  }

  public Specimen defaultSpecimen() {
    return (Specimen)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Specimen)
                .findFirst()
                .orElseThrow()
                .getResource();
  }

  public DiagnosticReport defaultDiagnosticReport() {
    return (DiagnosticReport)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.DiagnosticReport)
                .findFirst()
                .orElseThrow()
                .getResource();
  }

  public Organization defaultOrganization() {
    return (Organization)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Organization)
                .toList()
                .getLast()
                .getResource();
  }

  public MolecularSequence defaultMolecularSequence() {
    return (MolecularSequence)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.MolecularSequence)
                .findFirst()
                .orElseThrow()
                .getResource();
  }

  public CompositionRelatesToComponent defaultRelatesToComponent() {
    Bundle bundle = (Bundle) parameter.getParameter().getFirst().getResource();
    Composition composition =
        (Composition)
            bundle.getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Composition)
                .findFirst()
                .orElseThrow()
                .getResource();
    return composition.getRelatesTo().getFirst();
  }

  public Response createOutcomeResponse(IssueSeverity lvl) {
    String body =
        switch (lvl) {
          case INFORMATION -> getSuccessOutcome();
          case WARNING -> getWarningOutcome();
          case ERROR -> getErrorOutcome();
          case FATAL -> getFatalOutcome();
          case NULL -> null;
        };
    int status =
        switch (lvl) {
          case INFORMATION -> 200;
          case WARNING, ERROR, FATAL -> 422;
          case NULL -> 0;
        };
    return buildResponseWithDefaultRequest(status, body);
  }

  public Response createBadRequestResponse() {
    return buildResponseWithDefaultRequest(400, null);
  }

  public String translateDocumentReferenceToString(
      MediaType mediaType, DocumentReference documentReference) {
    if (mediaType == APPLICATION_JSON) {
      return fhirContext.newJsonParser().encodeResourceToString(documentReference);
    }
    return fhirContext.newXmlParser().encodeResourceToString(documentReference);
  }

  public String generateDocumentReferenceJsonForFile(String filePath)
      throws IOException, NoSuchAlgorithmException {
    return translateDocumentReferenceToString(
        APPLICATION_JSON, generateDocumentReference(filePath));
  }

  public String generateFastQDocumentReferenceXmlString(String filePath)
      throws IOException, NoSuchAlgorithmException {
    return translateDocumentReferenceToString(APPLICATION_XML, generateDocumentReference(filePath));
  }

  public DocumentReference generateDocumentReference(String path)
      throws IOException, NoSuchAlgorithmException {
    String hash = calcHashOnFile(path);

    Attachment attachment = new Attachment();
    attachment.setHashElement(new Base64BinaryType(hash));
    return new DocumentReference()
        .addContent(
            new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
  }

  public String calcHashOnFile(String path) throws IOException, NoSuchAlgorithmException {
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
    try (FileInputStream fis =
            new FileInputStream(getClass().getClassLoader().getResource(path).getPath());
        DigestInputStream dis = new DigestInputStream(fis, messageDigest)) {

      byte[] buffer = new byte[4096];
      while (dis.read(buffer) != -1) {
        // Just read buffer; DigestInputStream automatically updates digest
      }
    }
    byte[] hashBytes = messageDigest.digest();
    StringBuilder hexString = new StringBuilder();
    for (byte b : hashBytes) {
      hexString.append(String.format("%02x", b));
    }
    return hexString.toString();
  }

  private Response buildResponseWithDefaultRequest(int status, String body) {
    return Response.builder()
        .status(status)
        .request(
            Request.create(
                Request.HttpMethod.GET,
                "https://example.com",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8))
        .body(body, StandardCharsets.UTF_8)
        .build();
  }

  private String getSuccessOutcome() {
    return generateOutcomeAsJsonString(1, 0, 0, 0);
  }

  private String getFatalOutcome() {
    return generateOutcomeAsJsonString(0, 0, 0, 1);
  }

  private String getErrorOutcome() {
    return generateOutcomeAsJsonString(0, 0, 1, 0);
  }

  private String getWarningOutcome() {
    return generateOutcomeAsJsonString(0, 1, 0, 0);
  }

  public OperationOutcome generateOutcome(int info, int warning, int error, int fatal) {
    OperationOutcome outcome = generateOutcome();
    IntStream.range(0, info)
        .forEach(i -> outcome.addIssue().setSeverity(INFORMATION).getDetails().setText("All OK"));
    IntStream.range(0, warning)
        .forEach(
            i -> outcome.addIssue().setSeverity(WARNING).getDetails().setText("This is a warning"));
    IntStream.range(0, error)
        .forEach(
            i -> outcome.addIssue().setSeverity(ERROR).getDetails().setText("This is an error"));
    IntStream.range(0, fatal)
        .forEach(
            i -> outcome.addIssue().setSeverity(FATAL).getDetails().setText("This failed fatal"));
    return outcome;
  }

  private String generateOutcomeAsJsonString(int inform, int warning, int error, int fatal) {
    return fhirContext
        .newJsonParser()
        .encodeResourceToString(generateOutcome(inform, warning, error, fatal));
  }

  private static OperationOutcome generateOutcome() {
    OperationOutcome operationOutcome = new OperationOutcome();

    operationOutcome.setMeta(
        new Meta()
            .addProfile(
                "https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationResponse"));

    Narrative text = new Narrative();
    text.setStatus(Narrative.NarrativeStatus.GENERATED);
    XhtmlNode value = new XhtmlNode();
    value.setValue("http://www.w3.org/1999/xhtml");
    text.setDiv(value);
    operationOutcome.setText(text);
    return operationOutcome;
  }

  public boolean streamCompare(InputStream inputStream1, InputStream inputStream2)
      throws IOException {
    if (inputStream1 == inputStream2) {
      return true;
    }
    int byte1, byte2;
    do {
      byte1 = inputStream1.read();
      byte2 = inputStream2.read();
      if (byte1 != byte2) {
        return false;
      }
    } while (byte1 != -1);
    return true;
  }

  public Map<String, String> determineMetadataForValid() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("some_parameter", "some_value");
    metadata.put(VALIDATION_STATUS, VALID.toString());
    return metadata;
  }

  public Map<String, String> determineMetadataForInValid() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("some_parameter", "some_value");
    metadata.put(VALIDATION_STATUS, VALIDATION_FAILED.toString());
    return metadata;
  }
}
