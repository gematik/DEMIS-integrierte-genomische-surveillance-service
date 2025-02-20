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

package de.gematik.demis.igs.service.service.storage;

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

import de.gematik.demis.igs.service.api.model.CompletedChunk;
import de.gematik.demis.igs.service.api.model.S3Info;
import de.gematik.demis.igs.service.api.model.ValidationInfo;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.igs.service.utils.Pair;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface SimpleStorageService {

  /**
   * Creates an object in S3 compatible storage for a DocumentReference and its attachment
   *
   * @param documentId generated ID of DocumentReference to be saved
   * @param metadata contains additional information such as contentType of the attachment to be
   *     stored
   * @param stream binary data of the attachment
   */
  void putBlob(String documentId, Map<String, String> metadata, InputStream stream);

  /**
   * Returns the additional information such as contentType of the attachment for a given
   * DocumentReference
   *
   * @param documentId the id of the existing document
   * @return map of metadata
   * @throws de.gematik.demis.igs.service.exception.IgsServiceException thrown if document does not
   *     exist
   */
  Map<String, String> getMetadata(String documentId) throws IgsServiceException;

  /**
   * Returns the binary data of the attachment for a given DocumentReference
   *
   * @param documentId the id of the existing document
   * @return InputStreamResource of the attachment
   */
  InputStream getBlob(String documentId);

  /**
   * Creates a signed URL for a given DocumentReference
   *
   * @param documentId the id of the existing document
   * @param fileSize size of the file in bytes
   * @return S3Info object containing the signed URLs, the upload URL and the partSize
   */
  S3Info createSignedUrls(String documentId, double fileSize);

  /**
   * Check if a DocumentReference exists. If not throws an exception
   *
   * @param documentId the id of the existing document
   */
  void checkIfDocumentExists(String documentId);

  /**
   * Updates metadata values of a documentReference. Not allowed to update the validation-status
   *
   * @param pairs first-second pairs of metadata to be updated
   */
  void updateMetaDataValues(String documentId, Pair... pairs);

  /**
   * Sets the validating status of a documentReference to pending
   *
   * @param documentId
   */
  void setValidatingStatusToPending(String documentId);

  /**
   * Checks if each validation have been completed and sets the validation status of a
   * documentReference accordingly
   *
   * @param documentId
   */
  void finalizeValidation(String documentId);

  /**
   * Reads first two bytes from the given documentReference
   *
   * @param documentId the id of the existing document
   * @return
   */
  Pair getFirstBytesOf(String documentId);

  /**
   * Replace the content of a documentReference with an empty file. The metadata will be kept
   *
   * @param documentId the id of the existing document
   */
  void emptyFile(String documentId);

  /**
   * Returns a list of the status for given documentIds
   *
   * @param validationInfo list of ValidationInfo objects containing the documentId and current
   *     status to update
   * @return list of ValidationInfo objects containing the status and message
   */
  ValidationInfo getStatusOfDocument(ValidationInfo validationInfo);

  /**
   * Informs the S3 that an upload process has been completed and set tag to indicate that the
   * upload is completed
   *
   * @param documentId The ID of the document of which the upload has been completed.
   * @param uploadId The ID of the upload process.
   * @param completedChunks Part number and associated eTag of each chunk.
   */
  void informUploadComplete(
      String documentId, String uploadId, List<CompletedChunk> completedChunks);

  /**
   * Returns the binary data of the attachment for a given DocumentReference from the valid bucket
   *
   * @param documentId the id of the existing document
   * @return InputStreamResource of the attachment
   */
  InputStream getBlobFromValidBucket(String documentId);
}
