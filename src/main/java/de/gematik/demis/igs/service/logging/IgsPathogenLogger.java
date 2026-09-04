package de.gematik.demis.igs.service.logging;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logger for IGS pathogen validation results. This logger is used to log the validation status of
 * pathogens in a structured JSON format. The log entries include the notification ID, transaction
 * ID, pathogen code, and validation status (SUCCESS or FAILURE).
 */
@Component
@RequiredArgsConstructor
public final class IgsPathogenLogger {
  private static final Logger LOG = LoggerFactory.getLogger("igs-pathogen-logger");
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
  private final LoggingContext context;

  static String toJson(final IgsPathogenLogEntry entry) throws JsonProcessingException {
    return MAPPER.writeValueAsString(entry);
  }

  public void log() {
    if (context == null) {
      return;
    }
    final String validationStatus = context.isStatus() ? "SUCCESS" : "FAILURE";
    final IgsPathogenLogEntry entry =
        new IgsPathogenLogEntry(
            context.getNotificationId(),
            context.getTransactionId(),
            context.getPathogenCode(),
            validationStatus);

    try {
      LOG.info(toJson(entry));
    } catch (JsonProcessingException e) {
      LOG.warn(
          "Failed to serialize igs pathogen log entry: notificationId={}, transactionId={}, pathogenCode={}, validationStatus={}",
          entry.notificationId(),
          entry.transactionId(),
          entry.pathogenCode(),
          validationStatus,
          e);
    }
  }

  public record IgsPathogenLogEntry(
      String notificationId, String transactionId, String pathogenCode, String validationStatus) {}
}
