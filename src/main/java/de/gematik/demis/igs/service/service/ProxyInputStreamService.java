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

import de.gematik.demis.igs.service.exception.ErrorCode;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Service for running tasks that process InputStreams and OutputStreams using a thread pool
 * executor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyInputStreamService {

  @Qualifier("defautlTaskExecutor")
  private final ThreadPoolTaskExecutor executor;

  /**
   * Runs the provided task asynchronously by submitting it to the executor. The task processes the
   * provided InputStream and OutputStream.
   *
   * @param in the InputStream to be processed
   * @param pos the OutputStream to be written to
   * @param task the task that processes the InputStream and OutputStream
   */
  private void executeInSeperateThread(
      InputStream in, OutputStream pos, BiFunction<InputStream, OutputStream, Void> task) {
    executor.submit(
        () -> {
          try (in;
              pos) {
            task.apply(in, pos);
          } catch (Exception e) {
            log.error("Error processing stream", e);
          }
        });
  }

  /**
   * Runs the provided task asynchronously using the provided InputStream. Returns an InputStream
   * that represents the result of the task.
   *
   * @param in the InputStream to be processed
   * @param task the task that processes the InputStream and OutputStream
   * @return the InputStream that represents the result of the task
   */
  public InputStream run(InputStream in, BiFunction<InputStream, OutputStream, Void> task) {
    try {
      PipedOutputStream pos = new PipedOutputStream();
      PipedInputStream pis = new PipedInputStream(pos);
      executeInSeperateThread(in, pos, task);
      return pis;
    } catch (IOException e) {
      throw new IgsServiceException(ErrorCode.INVALID_DOCUMENT, e.getMessage(), e);
    }
  }
}
