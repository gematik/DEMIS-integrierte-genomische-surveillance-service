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
 * #L%
 */

import static de.gematik.demis.igs.service.utils.Constants.HASH_METADATA_NAME;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.UPLOAD_STATUS_DONE;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_NOT_INITIATED;
import static de.gematik.demis.igs.service.utils.Pair.pair;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static util.BaseUtil.PATH_TO_FASTA_GZIP;
import static util.BaseUtil.PATH_TO_FASTQ;

import de.gematik.demis.igs.service.api.model.ValidationInfo;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.service.storage.SimpleStorageService;
import de.gematik.demis.igs.service.service.validation.HashValidatorFunction;
import de.gematik.demis.igs.service.service.validation.SequenceValidatorService;
import de.gematik.demis.igs.service.service.validation.ValidationTracker;
import de.gematik.demis.igs.service.utils.Pair;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import util.BaseUtil;

class DocumentReferenceServiceTest {

  public static final String DOCUMENT_ID = "SomeID";
  public static final String EXAMPLE_HASH = "SOME_HASH";
  private static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_CONTENT =
      "createDocumentReference/documentReferenceWithoutContent.json";
  private static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_ATTACHMENT =
      "createDocumentReference/documentReferenceWithoutAttachment.json";
  private static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_HASH =
      "createDocumentReference/documentReferenceWithoutHash.json";
  private static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_EMPTY_HASH =
      "createDocumentReference/documentReferenceWithEmptyHash.json";
  private static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_INVALID_DATE =
      "createDocumentReference/documentReferenceInvalidDate.json";
  private final BaseUtil testUtil = new BaseUtil();
  private SimpleStorageService storageService;
  private SequenceValidatorService sequenceValidatorService;
  private DocumentReferenceService underTest;
  private ProxyInputStreamService proxy;
  private ValidationTracker tracker;

  @BeforeEach
  void setUp() {
    storageService = mock(SimpleStorageService.class);
    sequenceValidatorService = mock(SequenceValidatorService.class);
    proxy = mock(ProxyInputStreamService.class);
    tracker = mock(ValidationTracker.class);
    underTest =
        new DocumentReferenceService(storageService, sequenceValidatorService, proxy, tracker);
    underTest.setLongPollingIntervalSecs(1);
    underTest.setLongPollingTimeoutSecs(3);
  }

  @Nested
  class DocumentReferenceTest {

    @ParameterizedTest
    @ValueSource(strings = {PATH_TO_FASTQ, PATH_TO_FASTA_GZIP})
    void expectSuccessGivenValidDocumentReference(String path) throws Exception {
      org.hl7.fhir.r4.model.DocumentReference documentReferenceString =
          testUtil.generateDocumentReference(path);
      HashMap<String, String> expectedMetadata = new HashMap<>();
      expectedMetadata.put(
          HASH_METADATA_NAME,
          documentReferenceString
              .getContent()
              .getFirst()
              .getAttachment()
              .getHashElement()
              .asStringValue());
      doNothing().when(storageService).putBlob(anyString(), anyMap(), any(InputStream.class));

      org.hl7.fhir.r4.model.DocumentReference documentReference =
          underTest.generateDocumentReference(
              testUtil.translateDocumentReferenceToString(
                  APPLICATION_JSON, documentReferenceString),
              APPLICATION_JSON);
      String documentReferenceId = documentReference.getId();
      expectedMetadata.put(VALIDATION_STATUS, VALIDATION_NOT_INITIATED.name());
      verify(storageService, times(1))
          .putBlob(any(String.class), eq(expectedMetadata), any(InputStream.class));
      assertEquals(UUID.fromString(documentReferenceId).toString(), documentReferenceId);
    }

    @Test
    void expectIgsServiceExceptionGivenDocumentReferenceWithoutContent() throws Exception {
      String documentReferenceString =
          testUtil.readFileToString(PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_CONTENT);
      String expectedErrorMessage = "DocumentReference content is not present";

      Exception exception =
          assertThrows(
              IgsServiceException.class,
              () -> underTest.generateDocumentReference(documentReferenceString, APPLICATION_JSON));

      assertEquals(expectedErrorMessage, exception.getMessage());
      verifyNoInteractions(storageService);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_ATTACHMENT,
          PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_HASH,
          PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_EMPTY_HASH
        })
    void expectIgsServiceExceptionGivenDocumentReferenceWithoutAttachment(String documentReference)
        throws Exception {
      String documentReferenceString = testUtil.readFileToString(documentReference);
      String expectedErrorMessage = "DocumentReference does not contain a hash value";

      Exception exception =
          assertThrows(
              IgsServiceException.class,
              () -> underTest.generateDocumentReference(documentReferenceString, APPLICATION_JSON));

      assertEquals(expectedErrorMessage, exception.getMessage());
      verifyNoInteractions(storageService);
    }

    @Test
    void expectIgsServiceExceptionGivenDocumentReferenceWithBadDate() throws Exception {
      String documentReferenceString =
          testUtil.readFileToString(PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_INVALID_DATE);

      assertThrows(
          IgsServiceException.class,
          () -> underTest.generateDocumentReference(documentReferenceString, APPLICATION_JSON));

      verifyNoInteractions(storageService);
    }
  }

  @Nested
  class ValidationRunningTests {

    @Test
    @SneakyThrows
    void shouldThrowExceptionIfUploadCompleteTagMissing() {
      when(storageService.getMetadata(DOCUMENT_ID))
          .thenReturn(Map.of(VALIDATION_STATUS, VALIDATING.toString()));
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.prepareValidation(DOCUMENT_ID));
      assertEquals(
          ex.getMessage(), "Der Upload des angefragen Dokuments ist noch nicht abgeschlossen.");
    }

    @Test
    @SneakyThrows
    void shouldThrowExceptionIfValidationIsRunning() {
      when(storageService.getMetadata(DOCUMENT_ID))
          .thenReturn(
              Map.of(UPLOAD_STATUS, UPLOAD_STATUS_DONE, VALIDATION_STATUS, VALIDATING.toString()));
      IgsServiceException ex =
          assertThrows(IgsServiceException.class, () -> underTest.prepareValidation(DOCUMENT_ID));
      assertEquals(
          ex.getMessage(), format("Document with id %s is already validating", DOCUMENT_ID));
    }

    @Test
    @SneakyThrows
    void shouldNotThrowIfStatusNotPresent() {
      when(storageService.getMetadata(DOCUMENT_ID))
          .thenReturn(Map.of(UPLOAD_STATUS, UPLOAD_STATUS_DONE));
      assertDoesNotThrow(() -> underTest.prepareValidation(DOCUMENT_ID));
      verify(storageService).setValidatingStatusToPending(DOCUMENT_ID);
    }
  }

  @Nested
  class CheckValidationTest {

    @Test
    @SneakyThrows
    void shouldCallServiceOnSuccess() {
      InputStream in1 = new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8));
      InputStream in2 = new ByteArrayInputStream("2".getBytes(StandardCharsets.UTF_8));
      InputStream in3 = new ByteArrayInputStream("3".getBytes(StandardCharsets.UTF_8));
      Pair firstBytes = pair("1", "2");
      when(storageService.getBlob(DOCUMENT_ID)).thenReturn(in1);
      when(storageService.getFirstBytesOf(DOCUMENT_ID)).thenReturn(firstBytes);
      when(storageService.getMetadata(DOCUMENT_ID))
          .thenReturn(new HashMap<>(Map.of(HASH_METADATA_NAME, EXAMPLE_HASH)));
      when(proxy.run(eq(in1), any(HashValidatorFunction.class))).thenReturn(in2);
      when(proxy.run(eq(in2), any(GzipDecompressionFunction.class))).thenReturn(in3);

      underTest.validateBinary(DOCUMENT_ID);

      assertAll(
          () -> verify(sequenceValidatorService).validateSequence(in3, DOCUMENT_ID, tracker),
          () -> verify(storageService, times(1)).getBlob(DOCUMENT_ID),
          () -> verify(storageService, times(1)).getMetadata(DOCUMENT_ID),
          () -> verify(storageService, times(1)).getFirstBytesOf(DOCUMENT_ID),
          () -> verify(storageService, times(1)).finalizeValidation(DOCUMENT_ID),
          () -> verify(tracker, times(1)).init(DOCUMENT_ID),
          () -> verify(tracker, times(1)).drop(DOCUMENT_ID));
    }

    @Test
    @SneakyThrows
    void shouldUseStorageServiceCorrectlyOnInternalServerError() {
      when(storageService.getBlob(DOCUMENT_ID))
          .thenReturn(testUtil.readFileToInputStream(PATH_TO_FASTQ));
      when(storageService.getFirstBytesOf(DOCUMENT_ID)).thenReturn(new Pair("1", "2"));

      doThrow(new IOException("Error"))
          .when(sequenceValidatorService)
          .validateSequence(any(), any(), any());
      assertThrows(IgsServiceException.class, () -> underTest.validateBinary(DOCUMENT_ID));

      assertAll(
          () -> verify(storageService, times(1)).getBlob(DOCUMENT_ID),
          () -> verify(storageService, times(1)).getMetadata(DOCUMENT_ID),
          () -> verify(storageService, times(1)).getFirstBytesOf(DOCUMENT_ID),
          () -> verify(storageService, times(1)).finalizeValidation(DOCUMENT_ID));
    }

    @Test
    @SneakyThrows
    void shouldCallTrackerCorrectlyOnInternalServerError() {
      when(storageService.getBlob(DOCUMENT_ID))
          .thenReturn(testUtil.readFileToInputStream(PATH_TO_FASTQ));
      when(storageService.getFirstBytesOf(DOCUMENT_ID)).thenReturn(new Pair("1", "2"));

      doThrow(new IOException("Error"))
          .when(sequenceValidatorService)
          .validateSequence(any(), any(), any());
      assertThrows(IgsServiceException.class, () -> underTest.validateBinary(DOCUMENT_ID));

      assertAll(
          () -> verify(tracker, times(1)).init(DOCUMENT_ID),
          () -> verify(tracker, times(1)).drop(DOCUMENT_ID));
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GetValidationStatusTest {

    @Captor private ArgumentCaptor<ValidationInfo> validationInfoCaptor;

    @Test
    @SneakyThrows
    void shouldAskThreeTimesForValidationStatus() {
      String errorMsg = "SomeError";
      ValidationInfo result = ValidationInfo.builder().documentId(DOCUMENT_ID).build();
      when(storageService.getStatusOfDocument(validationInfoCaptor.capture()))
          .then(
              answer -> {
                result.setStatus(VALIDATING.name());
                return result;
              })
          .then(
              answer -> {
                result.setStatus(VALIDATING.name());
                return result;
              })
          .then(
              answer -> {
                result.setStatus(VALIDATION_FAILED.name());
                result.setMessage(errorMsg);
                return result;
              });
      underTest.getValidationStatus(DOCUMENT_ID);
      verify(storageService, times(3)).getStatusOfDocument(any());
      assertAll(
          () -> assertThat(validationInfoCaptor.getAllValues()).hasSize(3),
          () -> assertThat(validationInfoCaptor.getValue().getDocumentId()).isEqualTo(DOCUMENT_ID),
          () ->
              assertThat(validationInfoCaptor.getAllValues().getLast().getStatus())
                  .isEqualTo(VALIDATION_FAILED.name()),
          () ->
              assertThat(validationInfoCaptor.getAllValues().getLast().getMessage())
                  .isEqualTo(errorMsg));
    }

    @Test
    @SneakyThrows
    void shouldAskThreeTimesAndReturnCurrentStatus() {
      ValidationInfo result = ValidationInfo.builder().documentId(DOCUMENT_ID).build();
      when(storageService.getStatusOfDocument(validationInfoCaptor.capture()))
          .then(
              answer -> {
                result.setStatus(VALIDATING.name());
                return result;
              })
          .then(
              answer -> {
                result.setStatus(VALIDATING.name());
                return result;
              })
          .then(
              answer -> {
                result.setStatus(VALIDATING.name());
                return result;
              });
      ValidationInfo res = underTest.getValidationStatus(DOCUMENT_ID);
      verify(storageService, times(3)).getStatusOfDocument(any());
      assertAll(
          () -> assertThat(res.getDocumentId()).isEqualTo(DOCUMENT_ID),
          () -> assertThat(res.getStatus()).isEqualTo(VALIDATING.name()));
    }

    @Test
    @SneakyThrows
    void shouldAskOnlyOneTimeIfStatusAlreadyFinished() {
      String errorMsg = "SomeError";
      ValidationInfo result = ValidationInfo.builder().documentId(DOCUMENT_ID).build();
      when(storageService.getStatusOfDocument(validationInfoCaptor.capture()))
          .then(
              answer -> {
                result.setStatus(VALIDATION_FAILED.name());
                result.setMessage(errorMsg);
                return result;
              });
      ValidationInfo res = underTest.getValidationStatus(DOCUMENT_ID);
      verify(storageService, times(1)).getStatusOfDocument(any());
      assertAll(
          () -> assertThat(res.getDocumentId()).isEqualTo(DOCUMENT_ID),
          () -> assertThat(res.getStatus()).isEqualTo(VALIDATION_FAILED.name()),
          () -> assertThat(res.getMessage()).isEqualTo(errorMsg));
    }

    @Test
    @SneakyThrows
    void shouldAskOnlyTwoTimeIfStatusAlreadyFinished2() {
      ValidationInfo result = ValidationInfo.builder().documentId(DOCUMENT_ID).build();
      when(storageService.getStatusOfDocument(validationInfoCaptor.capture()))
          .then(
              answer -> {
                result.setStatus(VALIDATING.name());
                return result;
              })
          .then(
              answer -> {
                result.setStatus(VALID.name());
                return result;
              });
      ValidationInfo res = underTest.getValidationStatus(DOCUMENT_ID);
      verify(storageService, times(2)).getStatusOfDocument(any());
      assertAll(
          () -> assertThat(res.getDocumentId()).isEqualTo(DOCUMENT_ID),
          () -> assertThat(res.getStatus()).isEqualTo(VALID.name()));
    }
  }
}
