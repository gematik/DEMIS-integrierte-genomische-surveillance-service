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

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

import de.gematik.demis.igs.service.api.model.MultipartUploadComplete;
import de.gematik.demis.igs.service.api.model.S3Info;
import de.gematik.demis.igs.service.api.model.ValidationInfo;
import de.gematik.demis.igs.service.service.DocumentReferenceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@AllArgsConstructor
public class S3Controller {

  static final String S3_CONTROLLER_UPLOAD_BASE = "/S3Controller/upload";
  public static final String DOCUMENT_ID_PATH_VARIABLE = "documentId";
  private static final String PATH_DOCUMENT_ID = "/{" + DOCUMENT_ID_PATH_VARIABLE + "}";
  static final String S3_UPLOAD_INFO =
      S3_CONTROLLER_UPLOAD_BASE + PATH_DOCUMENT_ID + "/s3-upload-info";
  static final String S3_UPLOAD_VALIDATE =
      S3_CONTROLLER_UPLOAD_BASE + PATH_DOCUMENT_ID + "/$validate";
  static final String S3_UPLOAD_VALIDATION_STATUS =
      S3_CONTROLLER_UPLOAD_BASE + PATH_DOCUMENT_ID + "/$validation-status";
  static final String S3_UPLOAD_FINISH_UPLOAD =
      S3_CONTROLLER_UPLOAD_BASE + PATH_DOCUMENT_ID + "/$finish-upload";

  private final DocumentReferenceService documentReferenceService;

  @GetMapping(path = S3_UPLOAD_INFO)
  public ResponseEntity<S3Info> determineUploadInfo(
      @PathVariable(name = DOCUMENT_ID_PATH_VARIABLE) String documentId,
      @RequestParam double fileSize) {
    return ok(documentReferenceService.determineUploadInfo(documentId, fileSize));
  }

  @PostMapping(path = S3_UPLOAD_VALIDATE)
  public ResponseEntity<Void> initiateValidation(
      @PathVariable(name = DOCUMENT_ID_PATH_VARIABLE) String documentId,
      @RequestHeader(value = AUTHORIZATION) String authorization) {
    documentReferenceService.prepareValidation(documentId);
    documentReferenceService.validateBinary(documentId, authorization);
    return noContent().build();
  }

  @GetMapping(path = S3_UPLOAD_VALIDATION_STATUS)
  public ResponseEntity<ValidationInfo> validationStatus(
      @PathVariable(name = DOCUMENT_ID_PATH_VARIABLE) String documentId) {
    return ok(documentReferenceService.getValidationStatus(documentId));
  }

  @PostMapping(
      path = S3_UPLOAD_FINISH_UPLOAD,
      consumes = {APPLICATION_JSON_VALUE})
  public ResponseEntity<Void> finishUpload(
      @PathVariable(name = DOCUMENT_ID_PATH_VARIABLE) String documentId,
      @RequestBody MultipartUploadComplete multipartUploadComplete) {
    documentReferenceService.finishUpload(documentId, multipartUploadComplete);
    return noContent().build();
  }
}
