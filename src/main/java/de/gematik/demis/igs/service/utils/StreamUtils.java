package de.gematik.demis.igs.service.utils;

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

import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.NoArgsConstructor;

/** Utility class for performing common input and output stream operations. */
@NoArgsConstructor(access = PRIVATE)
public class StreamUtils {

  public static final int BYTE_BUFFER_SIZE = 256;

  /**
   * Reads all bytes from the input stream and writes them to the output stream.
   *
   * <p>This method uses a buffer of size {@link #BYTE_BUFFER_SIZE} to efficiently transfer bytes
   * from the input stream to the output stream until the end of the input stream is reached.
   *
   * <p>Both the input and output streams are closed after the copy operation completes.
   *
   * @param in the InputStream to read from.
   * @param out the OutputStream to write to.
   * @throws IOException if an I/O error occurs during reading or writing.
   */
  public static void writeInputToOutput(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[BYTE_BUFFER_SIZE];
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
      out.write(buffer, 0, bytesRead);
    }
    in.close();
    out.close();
  }
}
