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

import static de.gematik.demis.igs.service.api.DocumentReferenceController.FHIR_DOCUMENT_REFERENCE_BASE;
import static de.gematik.demis.igs.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;
import static de.gematik.demis.igs.service.exception.ErrorCode.FHIR_VALIDATION_FATAL;
import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_DOCUMENT_REFERENCE;
import static de.gematik.demis.igs.service.exception.ErrorCode.SEQUENCE_DATA_NOT_VALID;
import static de.gematik.demis.igs.service.exception.ServiceCallErrorCode.VS;
import static de.gematik.demis.igs.service.parser.FhirParser.deserializeResource;
import static de.gematik.demis.igs.service.service.validation.ValidationServiceClient.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.igs.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE;
import static de.gematik.demis.igs.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE_OLD;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static java.util.Optional.ofNullable;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import de.gematik.demis.igs.service.exception.ErrorCode;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.exception.IgsValidationException;
import de.gematik.demis.igs.service.service.fhir.FhirBundleOperationService;
import de.gematik.demis.igs.service.service.fhir.FhirOperationOutcomeOperationService;
import de.gematik.demis.igs.service.service.storage.SimpleStorageService;
import de.gematik.demis.igs.service.utils.Constants;
import de.gematik.demis.service.base.error.ServiceCallException;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/** Service class for validating incoming FHIR notifications */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationValidatorService {

  private static final String CLUSTER_INTERNAL_IGS_URI_PREFIX =
      "/surveillance/notification-sequence%s/fhir/";
  private final ValidationServiceClient validationServiceClient;
  private final Decoder decoder = new StringDecoder();
  private final FhirOperationOutcomeOperationService outcomeService;
  private final FhirBundleOperationService fhirBundleOperationService;
  private final SimpleStorageService storageService;
  private final HttpServletRequest httpServletRequest;

  @Value("${igs.demis.external-url}")
  @Setter
  private String demisExternalUrl;

  @Setter
  @Value("${feature.flag.new_api_endpoints}")
  private boolean isVersionHeaderForwardEnabled;

  private List<String> getDownloadEndpoints() {
    ArrayList<String> validEndpoints = new ArrayList<>();
    validEndpoints.add(
        demisExternalUrl
            + CLUSTER_INTERNAL_IGS_URI_PREFIX.replace("%s", "")
            + FHIR_DOCUMENT_REFERENCE_BASE);
    List<String> versions = headersFromRequestByName(HEADER_FHIR_API_VERSION);
    if (Objects.nonNull(versions)) {
      versions.stream()
          .map(
              v ->
                  demisExternalUrl
                      + CLUSTER_INTERNAL_IGS_URI_PREFIX.replace("%s", "/" + v)
                      + FHIR_DOCUMENT_REFERENCE_BASE)
          .forEach(validEndpoints::add);
    }
    return validEndpoints;
  }

  /**
   * Validates a FHIR bundle if feature flag is enabled
   *
   * @param content the content to validate
   * @param mediaType the media type of the content
   * @return the validation result
   */
  public OperationOutcome validateFhir(String content, MediaType mediaType) {
    HttpStatusCode status;
    String body;
    try (Response response = getValidationResponse(content, mediaType)) {
      status = HttpStatus.valueOf(response.status());
      body = readResponse(response);
    }
    if (status.value() != HttpStatus.UNPROCESSABLE_ENTITY.value() && !status.is2xxSuccessful()) {
      throw new ServiceCallException("service response: " + body, VS, status.value(), null);
    }

    OperationOutcome operationOutcome =
        deserializeResource(body, APPLICATION_JSON, OperationOutcome.class);

    if (status.is2xxSuccessful()) {
      log.debug("Fhir Bundle successfully validated.");
      return outcomeService.success(operationOutcome);
    }
    final boolean hasFatalIssue =
        operationOutcome.getIssue().stream()
            .anyMatch(issue -> issue.getSeverity() == IssueSeverity.FATAL);
    final ErrorCode errorCode = hasFatalIssue ? FHIR_VALIDATION_FATAL : FHIR_VALIDATION_ERROR;
    operationOutcome =
        outcomeService.error(operationOutcome, status, errorCode, "Fhir Bundle validation failed.");
    throw new IgsValidationException(errorCode, operationOutcome);
  }

  private @Nullable List<String> headersFromRequestByName(@Nonnull String headerName) {
    return ofNullable(httpServletRequest.getHeader(headerName)).map(List::of).orElse(null);
  }

  private Response getValidationResponse(String content, MediaType mediaType) {
    final HttpHeaders headers = new HttpHeaders();

    if (isVersionHeaderForwardEnabled) {
      headers.computeIfAbsent(HEADER_FHIR_API_VERSION, this::headersFromRequestByName);
      headers.computeIfAbsent(HEADER_FHIR_PROFILE, this::headersFromRequestByName);
    }
    headers.computeIfAbsent(HEADER_FHIR_PROFILE_OLD, ignored -> List.of("igs-profile-snapshots"));

    return mediaType.equals(APPLICATION_JSON)
        ? validationServiceClient.validateJsonBundle(headers, content)
        : validationServiceClient.validateXmlBundle(headers, content);
  }

  private String readResponse(final Response response) {
    try {
      return (String) decoder.decode(response, String.class);
    } catch (final IOException e) {
      throw new ServiceCallException("error reading response", VS, response.status(), e);
    }
  }

  /**
   * Checks whether the DocumentRefereces in a bundle have been validated successfully as sequence
   * data.
   *
   * @param bundle The bundle that has to be checked.
   */
  public void validateDocumentReferences(Bundle bundle) {
    List<String> documentReferenceUrls =
        fhirBundleOperationService.determineDocumentReferenceUrls(bundle);
    if (documentReferenceUrls == null || documentReferenceUrls.isEmpty()) {
      throw new IgsServiceException(
          SEQUENCE_DATA_NOT_VALID, "No DocumentReferences found in bundle.");
    }
    List<String> validEndpoints = getDownloadEndpoints();
    List<String> invlidDocumentReferences =
        documentReferenceUrls.stream()
            .filter(v -> validEndpoints.stream().noneMatch(v::startsWith))
            .toList();
    if (!invlidDocumentReferences.isEmpty()) {
      throw new IgsServiceException(
          INVALID_DOCUMENT_REFERENCE,
          String.format(
              "The document reference url %s does not point to Demis-Storage.",
              String.join(" and ", invlidDocumentReferences)));
    }
    documentReferenceUrls.stream()
        .map(this::extractDocumentReferenceId)
        .forEach(this::ensureSequenceDataHasBeenValidated);
  }

  private String extractDocumentReferenceId(String documentReferenceUrl) {
    return documentReferenceUrl.substring(documentReferenceUrl.lastIndexOf("/") + 1);
  }

  private void ensureSequenceDataHasBeenValidated(String documentId) {
    Map<String, String> metadata;
    try {
      metadata = storageService.getMetadata(documentId);
    } catch (IgsServiceException e) {
      throw new IgsServiceException(
          SEQUENCE_DATA_NOT_VALID,
          String.format("DocumentReference with ID %s has not been found", documentId));
    }
    String validationStatus = metadata.get(VALIDATION_STATUS);
    if (validationStatus == null
        || validationStatus.isEmpty()
        || !validationStatus.equalsIgnoreCase(Constants.ValidationStatus.VALID.name())) {
      throw new IgsServiceException(
          SEQUENCE_DATA_NOT_VALID,
          String.format(
              "Sequence data with document ID %s has not been validated successfully", documentId));
    }
  }
}
