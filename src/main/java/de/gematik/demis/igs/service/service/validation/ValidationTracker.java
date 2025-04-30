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

import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_DESCRIPTION;
import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_NOT_INITIATED;

import de.gematik.demis.igs.service.utils.Constants.ValidationStatus;
import de.gematik.demis.igs.service.utils.Pair;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * This class is used to track the validation status of a document. Each validation step is tracked
 * separately and to set the validation status each step has to be finished. The error message is
 * stored only once and is not overwritten. The class have to be thread safe.
 */
@Component
@Slf4j
public class ValidationTracker {

  private Map<String, StatusObject> statusMap = new ConcurrentHashMap<>();

  /**
   * Initialize the validation status for a documentId
   *
   * @param documentId the id of the document to keep track of
   */
  public void init(String documentId) {
    statusMap.put(
        documentId,
        new StatusObject()
            .setValidationStatus(VALIDATION_NOT_INITIATED)
            .setGzipStatus(VALIDATION_NOT_INITIATED)
            .setHashStatus(VALIDATION_NOT_INITIATED));
  }

  /**
   * Update the validation status of a document
   *
   * @param documentId the id of the document to keep track of
   * @param validationStatus the new validation status
   */
  public void updateValidationStatus(String documentId, ValidationStatus validationStatus) {
    updateValidationStatus(documentId, validationStatus, null);
  }

  /**
   * Update the validation status of a document with an error message
   *
   * @param documentId the id of the document to keep track of
   * @param validationStatus the new validation status
   * @param errorMessage the error message
   */
  public void updateValidationStatus(
      String documentId, ValidationStatus validationStatus, String errorMessage) {
    statusMap.computeIfPresent(
        documentId,
        (k, v) -> v.setValidationStatus(validationStatus).setErrorMessage(errorMessage));
  }

  /**
   * Update the hash status of a document
   *
   * @param documentId the id of the document to keep track of
   * @param hashStatus the new hash status
   */
  public void updateHashStatus(String documentId, ValidationStatus hashStatus) {
    updateHashStatus(documentId, hashStatus, null);
  }

  /**
   * Update the hash status of a document with an error message
   *
   * @param documentId the id of the document to keep track of
   * @param hashStatus the new hash status
   * @param errorMessage the error message
   */
  public void updateHashStatus(
      String documentId, ValidationStatus hashStatus, String errorMessage) {
    statusMap.computeIfPresent(
        documentId, (k, v) -> v.setHashStatus(hashStatus).setErrorMessage(errorMessage));
  }

  /**
   * Update the gzip status of a document
   *
   * @param documentId the id of the document to keep track of
   * @param gzipStatus the new gzip status
   */
  public void updateGzipStatus(String documentId, ValidationStatus gzipStatus) {
    updateGzipStatus(documentId, gzipStatus, null);
  }

  /**
   * Update the gzip status of a document with an error message
   *
   * @param documentId the id of the document to keep track of
   * @param gzipStatus the new gzip status
   * @param errorMessage the error message
   */
  public void updateGzipStatus(
      String documentId, ValidationStatus gzipStatus, String errorMessage) {
    statusMap.computeIfPresent(
        documentId, (k, v) -> v.setGzipStatus(gzipStatus).setErrorMessage(errorMessage));
  }

  /**
   * Drop the validation status of a documentId
   *
   * @param documentId the id of the document to drop
   */
  public void drop(String documentId) {
    statusMap.remove(documentId);
  }

  /**
   * Check if all validation steps are finished
   *
   * @param documentId the id of the document to check
   * @return true if all validation steps are finished
   */
  public Boolean isFinished(String documentId) {
    if (!statusMap.containsKey(documentId)) {
      return false;
    }
    StatusObject statusObject = statusMap.get(documentId);
    return statusObject.validationStatus.isProcceeded()
        && statusObject.hashStatus.isProcceeded()
        && statusObject.gzipStatus.isProcceeded();
  }

  /**
   * Calculate the metadata for a documentId. The metadata contains information whether the
   * validation was successful or not and an error message if the validation failed
   *
   * @param documentId the id of the document to calculate the metadata for
   * @return a list of metadata pairs
   */
  public List<Pair> calculateMetaData(String documentId) {
    if (!statusMap.containsKey(documentId)) {
      return List.of();
    }
    StatusObject statusObject = statusMap.get(documentId);
    if (statusObject.isSuccess()) {
      return List.of(new Pair(VALIDATION_STATUS, VALID.name()));
    }
    return List.of(
        new Pair(VALIDATION_STATUS, VALIDATION_FAILED.name()),
        new Pair(VALIDATION_DESCRIPTION, statusObject.getErrorMessage()));
  }

  @Data
  @Accessors(chain = true)
  private class StatusObject {

    private ValidationStatus validationStatus;
    private ValidationStatus hashStatus;
    private ValidationStatus gzipStatus;
    private String errorMessage;

    public synchronized StatusObject setErrorMessage(String errorMessage) {
      if (StringUtils.isBlank(this.errorMessage)) {
        this.errorMessage = errorMessage;
      }
      return this;
    }

    boolean isSuccess() {
      return validationStatus == ValidationStatus.VALID
          && hashStatus == ValidationStatus.VALID
          && gzipStatus == ValidationStatus.VALID;
    }
  }
}
