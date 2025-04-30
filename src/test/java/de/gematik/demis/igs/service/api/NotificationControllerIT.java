package de.gematik.demis.igs.service.api;

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

import static de.gematik.demis.igs.service.api.NotificationController.FHIR_BUNDLE_BASE;
import static de.gematik.demis.igs.service.parser.FhirParser.deserializeResource;
import static de.gematik.demis.igs.service.utils.Constants.PROCESS_NOTIFICATION_RESPONSE_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION;
import static util.BaseUtil.PATH_TO_IGS_NOTIFICATION_WRONG_SEQUENCEING_LAB_ID;

import de.gematik.demis.igs.service.service.ncapi.NotificationClearingApiClient;
import de.gematik.demis.igs.service.service.storage.S3StorageService;
import de.gematik.demis.igs.service.service.validation.ValidationServiceClient;
import de.gematik.demis.service.base.error.ServiceCallException;
import feign.Response;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import util.BaseUtil;

@SpringBootTest(properties = {"igs.demis.external-url=https://ingress.local"})
@AutoConfigureMockMvc
class NotificationControllerIT {

  private static final BaseUtil baseUtil = new BaseUtil();

  private static final String PATTERN =
      "IGS-10285-CVDP-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}";
  private static final String FIRST_DOCUMENT_ID = "ecd3f1f0-b6b6-46e0-b721-2d9869ab8195";
  private static final String SECOND_DOCUMENT_ID = "fde4g2g1-b6b6-46e0-b721-2d9869ab8195";
  @Autowired MockMvc mockMvc;
  @MockitoBean private ValidationServiceClient validationClient;
  @MockitoBean private NotificationClearingApiClient ncapiClient;
  @MockitoBean private S3StorageService s3StorageService;

  @ParameterizedTest
  @ValueSource(strings = {APPLICATION_OCTET_STREAM_VALUE, APPLICATION_PDF_VALUE, ""})
  void shouldReturn415IfWrongContentType(String contentType) throws Exception {
    String notification = baseUtil.readFileToString(PATH_TO_IGS_NOTIFICATION);
    mockMvc
        .perform(post(FHIR_BUNDLE_BASE).contentType(contentType).content(notification))
        .andExpect(status().isUnsupportedMediaType());
  }

  @ParameterizedTest
  @ValueSource(strings = {APPLICATION_OCTET_STREAM_VALUE, APPLICATION_PDF_VALUE})
  void shouldReturn415IfWrongAccept(String contentType) throws Exception {
    String notification = baseUtil.readFileToString(PATH_TO_IGS_NOTIFICATION);
    mockMvc
        .perform(
            post(FHIR_BUNDLE_BASE)
                .contentType(APPLICATION_JSON)
                .accept(contentType)
                .content(notification))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  @SneakyThrows
  void shouldUploadNotificationSuccessfully() {
    when(validationClient.validateXmlBundle(anyString()))
        .thenReturn(baseUtil.createOutcomeResponse(INFORMATION));
    when(ncapiClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenReturn(ResponseEntity.ok().build());
    when(s3StorageService.getMetadata(FIRST_DOCUMENT_ID))
        .thenReturn(baseUtil.determineMetadataForValid());
    when(s3StorageService.getMetadata(SECOND_DOCUMENT_ID))
        .thenReturn(baseUtil.determineMetadataForValid());
    String notification = baseUtil.readFileToString(PATH_TO_IGS_NOTIFICATION);
    MockHttpServletResponse response =
        mockMvc
            .perform(
                post(FHIR_BUNDLE_BASE)
                    .contentType(APPLICATION_XML_VALUE)
                    .accept(APPLICATION_JSON)
                    .content(notification))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

    Parameters parameters =
        deserializeResource(response.getContentAsString(), APPLICATION_JSON, Parameters.class);
    Optional<ParametersParameterComponent> transactionId =
        parameters.getParameter().stream()
            .filter(e -> e.getName().equals("transactionID"))
            .findFirst();
    Optional<ParametersParameterComponent> submitterGeneratedNotificationID =
        parameters.getParameter().stream()
            .filter(e -> e.getName().equals("submitterGeneratedNotificationID"))
            .findFirst();
    Optional<ParametersParameterComponent> labSequenceID =
        parameters.getParameter().stream()
            .filter(e -> e.getName().equals("labSequenceID"))
            .findFirst();

    assertAll(
        () -> assertThat(parameters.getParameter()).hasSize(4),
        () -> assertThat(transactionId).isPresent(),
        () ->
            assertThat(((Identifier) transactionId.orElseThrow().getValue()).getValue())
                .matches(Pattern.compile(PATTERN)),
        () -> assertThat(submitterGeneratedNotificationID).isPresent(),
        () ->
            assertThat(
                    ((Identifier) submitterGeneratedNotificationID.orElseThrow().getValue())
                        .getValue())
                .isEqualTo("f8585efb-1872-4a4f-b88d-8c889e93487b"),
        () -> assertThat(labSequenceID).isPresent(),
        () ->
            assertThat(((Identifier) labSequenceID.orElseThrow().getValue()).getValue())
                .isEqualTo("A384"));
  }

  @Test
  @SneakyThrows
  void shouldReturn422WithOperationOutputInBodyIfValidationFailed() {
    Response outcomeResponse = baseUtil.createOutcomeResponse(ERROR);
    when(validationClient.validateXmlBundle(anyString())).thenReturn(outcomeResponse);
    when(ncapiClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenReturn(ResponseEntity.ok().build());
    String notification = baseUtil.readFileToString(PATH_TO_IGS_NOTIFICATION);
    MockHttpServletResponse response =
        mockMvc
            .perform(
                post(FHIR_BUNDLE_BASE)
                    .contentType(APPLICATION_XML_VALUE)
                    .accept(APPLICATION_JSON)
                    .content(notification))
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse();
    OperationOutcome res =
        deserializeResource(
            response.getContentAsString(), APPLICATION_JSON, OperationOutcome.class);
    OperationOutcome expected =
        deserializeResource(
            new String(outcomeResponse.body().asInputStream().readAllBytes()),
            APPLICATION_JSON,
            OperationOutcome.class);
    assertAll(
        () ->
            assertThat(res.getIssue().getFirst())
                .usingRecursiveComparison()
                .isEqualTo(expected.getIssue().getFirst()),
        () -> assertThat(res.getIssue()).hasSize(2),
        () -> assertThat(res.getMeta().getProfile()).hasSize(1),
        () ->
            assertThat(res.getMeta().getProfile().getFirst().asStringValue())
                .isEqualTo(PROCESS_NOTIFICATION_RESPONSE_PROFILE.getUrl()));
  }

  @Test
  @SneakyThrows
  void shouldReturn500IfNcapiThrowsException() {
    when(ncapiClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenThrow(ServiceCallException.class);
    String notification = baseUtil.readFileToString(PATH_TO_IGS_NOTIFICATION);
    mockMvc
        .perform(
            post(FHIR_BUNDLE_BASE)
                .contentType(APPLICATION_XML_VALUE)
                .accept(APPLICATION_JSON)
                .content(notification))
        .andExpect(status().isInternalServerError());
  }

  @Test
  @SneakyThrows
  void shouldReturn400IfOneDocumentIsNotValidated() {
    String notification = baseUtil.readFileToString(PATH_TO_IGS_NOTIFICATION);
    when(validationClient.validateXmlBundle(anyString()))
        .thenReturn(baseUtil.createOutcomeResponse(INFORMATION));
    when(s3StorageService.getMetadata(FIRST_DOCUMENT_ID))
        .thenReturn(baseUtil.determineMetadataForValid());
    when(s3StorageService.getMetadata(SECOND_DOCUMENT_ID))
        .thenReturn(baseUtil.determineMetadataForInValid());
    mockMvc
        .perform(
            post(FHIR_BUNDLE_BASE)
                .contentType(APPLICATION_XML_VALUE)
                .accept(APPLICATION_JSON)
                .content(notification))
        .andExpect(status().isBadRequest());
  }

  @Test
  @SneakyThrows
  void shouldReturn400IfSequenceLabIdWrong() {
    when(validationClient.validateXmlBundle(anyString()))
        .thenReturn(baseUtil.createOutcomeResponse(INFORMATION));
    when(s3StorageService.getMetadata(FIRST_DOCUMENT_ID))
        .thenReturn(baseUtil.determineMetadataForValid());
    when(s3StorageService.getMetadata(SECOND_DOCUMENT_ID))
        .thenReturn(baseUtil.determineMetadataForValid());
    String notification =
        baseUtil.readFileToString(PATH_TO_IGS_NOTIFICATION_WRONG_SEQUENCEING_LAB_ID);
    mockMvc
        .perform(
            post(FHIR_BUNDLE_BASE)
                .contentType(APPLICATION_XML_VALUE)
                .accept(APPLICATION_JSON)
                .content(notification))
        .andExpect(status().isBadRequest());
  }
}
