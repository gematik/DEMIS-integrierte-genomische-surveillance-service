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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class IgsPathogenLoggerTest {
  @Test
  void shouldSerializeKnownFieldsToJson() throws Exception {
    IgsPathogenLogger.IgsPathogenLogEntry entry =
        new IgsPathogenLogger.IgsPathogenLogEntry("notifId_1", "transId_1", "cvdp", "SUCCESS");

    String json = IgsPathogenLogger.toJson(entry);

    assertThat(json).contains("\"notificationId\":\"notifId_1\"");
    assertThat(json).contains("\"transactionId\":\"transId_1\"");
    assertThat(json).contains("\"pathogenCode\":\"cvdp\"");
    assertThat(json).contains("\"validationStatus\":\"SUCCESS\"");
  }

  @Test
  void shouldOmitNullFieldsInJson() throws Exception {
    IgsPathogenLogger.IgsPathogenLogEntry entry =
        new IgsPathogenLogger.IgsPathogenLogEntry("notif-1", null, null, "FAILURE");

    String json = IgsPathogenLogger.toJson(entry);

    assertThat(json).contains("\"notificationId\":\"notif-1\"");
    assertThat(json).contains("\"validationStatus\":\"FAILURE\"");
    assertThat(json).doesNotContain("transactionId");
    assertThat(json).doesNotContain("pathogenCode");
  }

  @Test
  void shouldNotThrowWhenContextFieldsAreMissing() {
    LoggingContext context = new LoggingContext();
    context.setStatus(false);
    IgsPathogenLogger logger = new IgsPathogenLogger(context);

    assertDoesNotThrow(logger::log);
  }
}
