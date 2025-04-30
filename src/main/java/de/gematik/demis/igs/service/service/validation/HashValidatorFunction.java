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

import static de.gematik.demis.igs.service.utils.Constants.HASH_ALGORITHM;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.HASH_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INTERNAL_SERVER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.utils.StreamUtils.writeInputToOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Class for validating the hash of a given InputStream */
@Slf4j
@AllArgsConstructor
public class HashValidatorFunction implements BiFunction<InputStream, OutputStream, Void> {

  private String hash;
  private String documentId;
  private final ValidationTracker validationTracker;

  /**
   * Applies a hash-based validation mechanism to the contents of the provided InputStream.
   *
   * <p>It reads from the InputStream, processes the data to compute its hash, writes the processed
   * data to the OutputStream, and then compares the computed hash with an expected hash value. If
   * an error occurs during processing, the metadata got updated accordingly.
   *
   * @param in the InputStream to be read and processed.
   * @param out the OutputStream where the processed data should be written.
   * @return always returns {@code null}.
   */
  @Override
  public Void apply(InputStream in, OutputStream out) {
    validationTracker.updateHashStatus(documentId, VALIDATING);
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      DigestInputStream dis = new DigestInputStream(in, digest);
      writeInputToOutput(dis, out);
      String calculatedHash = getHashFromDigest(digest);
      if (hash.equals(calculatedHash)) {
        validationTracker.updateHashStatus(documentId, VALID);
      } else {
        log.error("Hash mismatch: expected {}, got {}", hash, calculatedHash);
        validationTracker.updateHashStatus(documentId, VALIDATION_FAILED, HASH_ERROR_MSG);
      }
      dis.close();
    } catch (NoSuchAlgorithmException | IOException ex) {
      log.error("Error while validating hash", ex);
      validationTracker.updateHashStatus(
          documentId, VALIDATION_FAILED, INTERNAL_SERVER_ERROR_MESSAGE);
    }
    return null;
  }

  private String getHashFromDigest(MessageDigest digest) {
    byte[] hashBytes = digest.digest();
    StringBuilder hexString = new StringBuilder();
    for (byte b : hashBytes) {
      hexString.append(String.format("%02x", b));
    }
    return hexString.toString();
  }
}
