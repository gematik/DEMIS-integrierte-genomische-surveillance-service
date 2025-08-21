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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATING;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INTERNAL_SERVER_ERROR_MESSAGE;
import static de.gematik.demis.igs.service.utils.ErrorMessages.INVALID_COMPRESSED_FILE_ERROR_MSG;
import static de.gematik.demis.igs.service.utils.StreamUtils.writeInputToOutput;

import de.gematik.demis.igs.service.service.validation.ValidationTracker;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiFunction;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

@Slf4j
@RequiredArgsConstructor
public class GzipDecompressionFunction implements BiFunction<InputStream, OutputStream, Void> {

  private final int firstByte;
  private final int secondByte;
  private final String documentId;
  private final ValidationTracker validationTracker;

  /**
   * Handles the decompression of the provided InputStream if it is compressed in GZIP format.
   *
   * <p>This method checks if the InputStream is compressed using the GZIP format. If it is, the
   * method returns a decompressed InputStream. If the InputStream is not compressed, it is returned
   * as is.
   *
   * @param in the InputStream to be checked and possibly decompressed
   * @return a decompressed InputStream if the input was compressed, or the original InputStream if
   *     not
   * @throws IOException if an I/O error occurs during decompression or reading the InputStream
   */
  @Override
  public Void apply(InputStream in, OutputStream out) {
    validationTracker.updateGzipStatus(documentId, VALIDATING);
    try {
      if (isDataCompressed()) {
        in = decompress(in);
      }
      writeInputToOutput(in, out);
      validationTracker.updateGzipStatus(documentId, VALID);
    } catch (IOException e) {
      if (e.getMessage()
          .contentEquals("Gzip-compressed data is corrupt (uncompressed size mismatch).")) {
        log.info("Received invalid compressed file for documentId: {}", documentId);
        validationTracker.updateGzipStatus(
            documentId, VALIDATION_FAILED, INVALID_COMPRESSED_FILE_ERROR_MSG);
      } else {
        validationTracker.updateGzipStatus(
            documentId, VALIDATION_FAILED, INTERNAL_SERVER_ERROR_MESSAGE);
      }
    }
    return null;
  }

  /**
   * Checks if the provided InputStream contains compressed data in GZIP format.
   *
   * <p>This method determines if the data is compressed by checking the first two bytes of the
   * stream against the GZIP magic number. The bytes are not supposed to come from the inputStream
   * it self due its condition of a PipedInputStream
   *
   * @return true if the InputStream is compressed in GZIP format, false otherwise
   */
  private boolean isDataCompressed() {
    int magic = (firstByte & 0xff) | ((secondByte << 8) & 0xff00);
    return magic == GZIPInputStream.GZIP_MAGIC;
  }

  /**
   * Decompress a GZIP InputStream and return the decompressed InputStream
   *
   * @param input the GZIP compressed InputStream
   * @return the decompressed InputStream
   */
  private InputStream decompress(InputStream input) throws IOException {
    return new GzipCompressorInputStream(input, true);
  }
}
