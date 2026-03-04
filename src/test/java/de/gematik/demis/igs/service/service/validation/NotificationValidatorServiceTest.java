package de.gematik.demis.igs.service.service.validation;

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

import static de.gematik.demis.igs.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;
import static de.gematik.demis.igs.service.exception.ErrorCode.FHIR_VALIDATION_FATAL;
import static de.gematik.demis.igs.service.exception.ErrorCode.FILE_NOT_FOUND;
import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_DOCUMENT_REFERENCE;
import static de.gematik.demis.igs.service.exception.ErrorCode.SEQUENCE_DATA_NOT_VALID;
import static de.gematik.demis.igs.service.service.validation.ValidationServiceClient.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.igs.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE;
import static de.gematik.demis.igs.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE_OLD;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.ErrorMessages.RESOURCE_NOT_FOUND_ERROR_MSG;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.exception.IgsValidationException;
import de.gematik.demis.igs.service.service.fhir.FhirBundleOperationService;
import de.gematik.demis.igs.service.service.fhir.FhirOperationOutcomeOperationService;
import de.gematik.demis.igs.service.service.storage.S3StorageService;
import de.gematik.demis.igs.service.utils.RequestHeaderProvider;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import util.BaseUtil;

@ExtendWith(MockitoExtension.class)
class NotificationValidatorServiceTest {

  public static final String VERSION = "v1";
  private static final String DOCUMENT_REFERENCE_BASE =
      "https://demis.rki.de/surveillance/notification-sequence%s/fhir/DocumentReference/";
  private static final String FIRST_DOCUMENT_REFERENCE_ID = "ecd3f1f0-b6b6-46e0-b721-2d9869ab8195";
  private static final String FIRST_DOCUMENT_REFERENCE =
      format(DOCUMENT_REFERENCE_BASE, "") + FIRST_DOCUMENT_REFERENCE_ID;
  private static final String SECOND_DOCUMENT_REFERENCE_ID = "fde4g2g1-b6b6-46e0-b721-2d9869ab8195";
  private static final String SECOND_DOCUMENT_REFERENCE =
      format(DOCUMENT_REFERENCE_BASE, "") + SECOND_DOCUMENT_REFERENCE_ID;
  private static final String MALICIOUS_DOCUMENT_REFERENCE =
      "https://malicious/surveillance/notification-sequence/fhir/DocumentReference/"
          + FIRST_DOCUMENT_REFERENCE_ID;
  private static final String VERSIONED_DOCUMENT_REFERENCE =
      format(DOCUMENT_REFERENCE_BASE, "/" + VERSION) + FIRST_DOCUMENT_REFERENCE_ID;
  private static final String VERSIONED_MALICIOUS_DOCUMENT_REFERENCE =
      "https://malicious/surveillance/notification-sequence/"
          + VERSION
          + "/fhir/DocumentReference/"
          + FIRST_DOCUMENT_REFERENCE_ID;

  BaseUtil testUtil = new BaseUtil();
  @Mock ValidationServiceClient client;
  @Mock FhirOperationOutcomeOperationService outcomeService;
  @Mock FhirBundleOperationService fhirBundleOperationService;
  @Mock S3StorageService s3StorageService;
  @Mock RequestHeaderProvider requestHeaderProvider;
  @Captor ArgumentCaptor<HttpHeaders> headerCaptor;

  @InjectMocks NotificationValidatorService underTest;

  @BeforeEach
  void setUp() {
    underTest.setDemisExternalUrl("https://demis.rki.de");
    // outcomeService should only response what he was given
    lenient()
        .when(outcomeService.error(any(), any(), any(), any()))
        .thenAnswer((Answer<OperationOutcome>) invocationOnMock -> invocationOnMock.getArgument(0));
    lenient()
        .when(outcomeService.success(any()))
        .thenAnswer((Answer<OperationOutcome>) invocationOnMock -> invocationOnMock.getArgument(0));
  }

  private Map<String, String> determineMetadataForValidationStatus(String validationStatus) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("some_parameter", "some_value");
    metadata.put(VALIDATION_STATUS, validationStatus);
    return metadata;
  }

  @Nested
  class ValidateFhir {

    @Test
    void shouldValidateSuccessfully() {
      String bundleString = testUtil.getDefaultBundleAsString();
      when(client.validateJsonBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      OperationOutcome outcome = underTest.validateFhir(bundleString, APPLICATION_JSON);
      assertThat(
              outcome.getIssue().stream()
                  .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
          .isNotEmpty()
          .allMatch(severity -> severity.equals(INFORMATION));
    }

    @Test
    void shouldCallXmlClientIfMediaTypeXml() {
      String bundleString = testUtil.getDefaultBundleAsString();
      when(client.validateXmlBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      OperationOutcome outcome = underTest.validateFhir(bundleString, APPLICATION_XML);
      assertThat(
              outcome.getIssue().stream()
                  .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
          .isNotEmpty()
          .allMatch(severity -> severity.equals(INFORMATION));
    }

    @Test
    void shouldThrowExceptionIfValidationRequestFails() {
      String bundleString = testUtil.getDefaultBundleAsString();
      when(client.validateJsonBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createBadRequestResponse());
      ServiceCallException exception =
          assertThrows(
              ServiceCallException.class,
              () -> underTest.validateFhir(bundleString, APPLICATION_JSON));
      assertThat(exception.getErrorCode()).isEqualTo("VS");
      assertThat(exception.getHttpStatus()).isEqualTo(400);
    }

    @Test
    void shouldThrowExceptionIfValidationFatal() {
      String bundleString = testUtil.getDefaultBundleAsString();
      when(client.validateJsonBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createOutcomeResponse(FATAL));
      IgsValidationException exception =
          assertThrows(
              IgsValidationException.class,
              () -> underTest.validateFhir(bundleString, APPLICATION_JSON));
      assertThat(exception.getErrorCode()).contains(FHIR_VALIDATION_FATAL.toString());
    }

    @Test
    void shouldThrowExceptionIfValidationError() {
      String bundleString = testUtil.getDefaultBundleAsString();
      when(client.validateJsonBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createOutcomeResponse(ERROR));
      IgsValidationException exception =
          assertThrows(
              IgsValidationException.class,
              () -> underTest.validateFhir(bundleString, APPLICATION_JSON));
      assertThat(exception.getErrorCode()).contains(FHIR_VALIDATION_ERROR.toString());
    }

    @Test
    void shouldThrowExceptionIfValidationWarning() {
      String bundleString = testUtil.getDefaultBundleAsString();
      when(client.validateJsonBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createOutcomeResponse(WARNING));
      IgsValidationException exception =
          assertThrows(
              IgsValidationException.class,
              () -> underTest.validateFhir(bundleString, APPLICATION_JSON));
      assertThat(exception.getErrorCode()).contains(FHIR_VALIDATION_ERROR.toString());
    }

    @Nested
    class VersionedEndpoints {

      @Test
      void shouldForwardHeaderCorrectly() {
        String bundleString = testUtil.getDefaultBundleAsString();
        String profile = "igs-profile-snapshots";
        when(requestHeaderProvider.receiveApiVersionsFromRequest()).thenReturn(List.of(VERSION));
        when(requestHeaderProvider.receiveFhirProfileFromRequest()).thenReturn(List.of(profile));
        when(client.validateJsonBundle(any(), eq(bundleString)))
            .thenReturn(testUtil.createOutcomeResponse(INFORMATION));

        underTest.validateFhir(bundleString, APPLICATION_JSON);

        verify(client).validateJsonBundle(headerCaptor.capture(), eq(bundleString));
        assertThat(headerCaptor.getValue())
            .isNotNull()
            .hasSize(3)
            .containsKey(HEADER_FHIR_PROFILE)
            .extractingByKey(HEADER_FHIR_PROFILE)
            .isEqualTo(List.of(profile));
        assertThat(headerCaptor.getValue())
            .extractingByKey(HEADER_FHIR_API_VERSION)
            .isEqualTo(List.of(VERSION));
      }

      @Test
      void shouldUseDefaultIfNoHeaderSet() {
        String bundleString = testUtil.getDefaultBundleAsString();
        when(requestHeaderProvider.receiveApiVersionsFromRequest()).thenReturn(null);
        when(requestHeaderProvider.receiveFhirProfileFromRequest()).thenReturn(null);
        when(client.validateJsonBundle(any(), eq(bundleString)))
            .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
        underTest.validateFhir(bundleString, APPLICATION_JSON);

        verify(client).validateJsonBundle(headerCaptor.capture(), eq(bundleString));
        assertThat(headerCaptor.getValue())
            .isNotNull()
            .hasSize(1)
            .containsKey(HEADER_FHIR_PROFILE_OLD)
            .extractingByKey(HEADER_FHIR_PROFILE_OLD)
            .isEqualTo(List.of("igs-profile-snapshots"));
      }
    }
  }

  @Nested
  class ValidateDocumentReferences {

    @NullSource
    @EmptySource
    @ParameterizedTest
    void shouldThrowIgsServiceExceptionIfNoDocumentReferenceDelivered(List<String> docrefs) {
      Bundle bundle = testUtil.getDefaultBundle();
      when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle)).thenReturn(docrefs);
      IgsServiceException ex =
          assertThrows(
              IgsServiceException.class, () -> underTest.validateDocumentReferences(bundle));
      assertThat(ex.getErrorCode()).isEqualTo(SEQUENCE_DATA_NOT_VALID.name());
      assertThat(ex.getMessage()).isEqualTo("No DocumentReferences found in bundle.");
    }

    @Test
    void shouldThrowIgsServiceExceptionIfDocumentNotFoundWithInvalidSequenceOnValidation() {
      Bundle bundle = testUtil.getDefaultBundle();
      when(s3StorageService.getMetadata(FIRST_DOCUMENT_REFERENCE_ID))
          .thenThrow(new IgsServiceException(FILE_NOT_FOUND, RESOURCE_NOT_FOUND_ERROR_MSG));
      when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
          .thenReturn(List.of(FIRST_DOCUMENT_REFERENCE));
      IgsServiceException ex =
          assertThrows(
              IgsServiceException.class, () -> underTest.validateDocumentReferences(bundle));
      assertThat(ex.getErrorCode()).isEqualTo(SEQUENCE_DATA_NOT_VALID.name());
      assertThat(ex.getMessage())
          .isEqualTo(
              "DocumentReference with ID " + FIRST_DOCUMENT_REFERENCE_ID + " has not been found");
    }

    @Test
    void shouldThrowIgsServiceExceptionIfUrlOfNotificationNotPointingToDemis() {
      Bundle bundle = testUtil.getDefaultBundle();
      when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
          .thenReturn(List.of(MALICIOUS_DOCUMENT_REFERENCE));
      IgsServiceException ex =
          assertThrows(
              IgsServiceException.class, () -> underTest.validateDocumentReferences(bundle));
      assertThat(ex.getErrorCode()).isEqualTo(INVALID_DOCUMENT_REFERENCE.name());
      assertThat(ex.getMessage())
          .isEqualTo(
              "The document reference url "
                  + MALICIOUS_DOCUMENT_REFERENCE
                  + " does not point to Demis-Storage.");
    }

    @Test
    void shouldThrowIgsServiceExceptionIfSecondUrlOfNotificationNotPointingToDemis() {
      Bundle bundle = testUtil.getDefaultBundle();
      when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
          .thenReturn(List.of(FIRST_DOCUMENT_REFERENCE, MALICIOUS_DOCUMENT_REFERENCE));
      IgsServiceException ex =
          assertThrows(
              IgsServiceException.class, () -> underTest.validateDocumentReferences(bundle));
      assertThat(ex.getErrorCode()).isEqualTo(INVALID_DOCUMENT_REFERENCE.name());
      assertThat(ex.getMessage())
          .isEqualTo(
              "The document reference url "
                  + MALICIOUS_DOCUMENT_REFERENCE
                  + " does not point to Demis-Storage.");
    }

    @Test
    void shouldValidateDocumentReferences() {
      Bundle bundle = testUtil.getDefaultBundle();
      when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
          .thenReturn(List.of(FIRST_DOCUMENT_REFERENCE, SECOND_DOCUMENT_REFERENCE));
      when(s3StorageService.getMetadata(FIRST_DOCUMENT_REFERENCE_ID))
          .thenReturn(testUtil.determineMetadataForValid());
      when(s3StorageService.getMetadata(SECOND_DOCUMENT_REFERENCE_ID))
          .thenReturn(testUtil.determineMetadataForValid());

      underTest.validateDocumentReferences(bundle);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"VALIDATING", "VALIDATION_FAILED", "VALIDATION_NOT_INITIATED"})
    void shouldThrowIgsServiceExceptionOnNoSuccessfulValidation(String validationStatus) {
      Bundle bundle = testUtil.getDefaultBundle();
      when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
          .thenReturn(List.of(FIRST_DOCUMENT_REFERENCE, SECOND_DOCUMENT_REFERENCE));
      when(s3StorageService.getMetadata(FIRST_DOCUMENT_REFERENCE_ID))
          .thenReturn(testUtil.determineMetadataForValid());
      when(s3StorageService.getMetadata(SECOND_DOCUMENT_REFERENCE_ID))
          .thenReturn(determineMetadataForValidationStatus(validationStatus));
      assertThatThrownBy(() -> underTest.validateDocumentReferences(bundle))
          .isInstanceOf(IgsServiceException.class)
          .hasMessage(
              "Sequence data with document ID "
                  + SECOND_DOCUMENT_REFERENCE_ID
                  + " has not been validated successfully");
    }

    @Nested
    class VersionedEndpoints {

      @Test
      void shouldCheckDocumentReferenceUrlWithVersionNumber() {
        Bundle bundle = testUtil.getDefaultBundle();
        when(requestHeaderProvider.receiveApiVersionsFromRequest()).thenReturn(List.of(VERSION));
        when(s3StorageService.getMetadata(FIRST_DOCUMENT_REFERENCE_ID))
            .thenReturn(testUtil.determineMetadataForValid());
        when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
            .thenReturn(List.of(VERSIONED_DOCUMENT_REFERENCE));

        underTest.validateDocumentReferences(bundle);
      }

      @Test
      void shouldCheckDocumentReferenceUrlWithVersionNumberAndNoVersionNumberSuccessfully() {
        Bundle bundle = testUtil.getDefaultBundle();
        when(requestHeaderProvider.receiveApiVersionsFromRequest()).thenReturn(List.of(VERSION));
        when(s3StorageService.getMetadata(FIRST_DOCUMENT_REFERENCE_ID))
            .thenReturn(testUtil.determineMetadataForValid());
        when(s3StorageService.getMetadata(SECOND_DOCUMENT_REFERENCE_ID))
            .thenReturn(testUtil.determineMetadataForValid());
        when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
            .thenReturn(List.of(VERSIONED_DOCUMENT_REFERENCE, SECOND_DOCUMENT_REFERENCE));

        underTest.validateDocumentReferences(bundle);
      }

      @Test
      void shouldThrowExceptionIfOneDocumentReferenceWithoutVersionNumber() {
        Bundle bundle = testUtil.getDefaultBundle();
        when(requestHeaderProvider.receiveApiVersionsFromRequest()).thenReturn(List.of(VERSION));
        when(fhirBundleOperationService.determineDocumentReferenceUrls(bundle))
            .thenReturn(
                List.of(VERSIONED_DOCUMENT_REFERENCE, VERSIONED_MALICIOUS_DOCUMENT_REFERENCE));

        IgsServiceException ex =
            assertThrows(
                IgsServiceException.class, () -> underTest.validateDocumentReferences(bundle));
        assertThat(ex.getErrorCode()).isEqualTo(INVALID_DOCUMENT_REFERENCE.name());
        assertThat(ex.getMessage())
            .isEqualTo(
                "The document reference url "
                    + VERSIONED_MALICIOUS_DOCUMENT_REFERENCE
                    + " does not point to Demis-Storage.");
      }
    }
  }
}
