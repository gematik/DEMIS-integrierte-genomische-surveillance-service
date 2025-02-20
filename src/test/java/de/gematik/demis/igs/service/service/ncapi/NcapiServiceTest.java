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

package de.gematik.demis.igs.service.service.ncapi;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import de.gematik.demis.service.base.error.ServiceCallException;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import util.BaseUtil;

@ExtendWith(MockitoExtension.class)
class NcapiServiceTest {

  @Captor private ArgumentCaptor<String> jsonStringCaptor;
  @Captor private ArgumentCaptor<String> tokenCaptor;
  private BaseUtil testUtils = new BaseUtil();

  @Mock NotificationClearingApiClient client;

  @InjectMocks NcapiService service;

  @Test
  void shouldSendBundleInBundle() {
    Bundle bundle = testUtils.getDefaultBundle();
    when(client.sendNotification(tokenCaptor.capture(), jsonStringCaptor.capture()))
        .thenReturn(ResponseEntity.ok("Call successful"));
    service.sendNotificationToNcapi(bundle);
    assertThat(tokenCaptor.getValue()).startsWith("Bearer ");
    Bundle transactionBundle = testUtils.getBundleFromJsonString(jsonStringCaptor.getValue());
    assertThat(transactionBundle.getEntry()).hasSize(1);
  }

  @Test
  void shouldThrowIgsNcapiExceptionIfRequestIsNotOk() {
    Bundle bundle = testUtils.getDefaultBundle();
    when(client.sendNotification(tokenCaptor.capture(), jsonStringCaptor.capture()))
        .thenThrow(ServiceCallException.class);
    assertThrows(IgsServiceException.class, () -> service.sendNotificationToNcapi(bundle));
  }

  @Test
  void shouldSetTagForRkiCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    service.sendNotificationToNcapi(bundle);
    assertThat(bundle.getMeta().getTag()).hasSize(1);
    assertThat(bundle.getMeta().getTag().getFirst().getCode()).isEqualTo("1.");
    assertThat(bundle.getMeta().getTag().getFirst().getSystem())
        .isEqualTo("https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment");
  }
}
