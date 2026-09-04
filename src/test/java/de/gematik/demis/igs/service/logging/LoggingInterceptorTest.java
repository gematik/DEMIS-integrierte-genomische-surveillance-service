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

import static de.gematik.demis.igs.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoggingInterceptorTest {

  @Mock private IgsPathogenLogger igsPathogenLogger;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private LoggingInterceptor interceptor;
  private LoggingContext loggingContext;

  @BeforeEach
  void setUp() {
    loggingContext = new LoggingContext();
    interceptor = new LoggingInterceptor(igsPathogenLogger, loggingContext);
  }

  @Test
  void shouldLogForSuccessfulRequest() {
    when(response.getStatus()).thenReturn(HttpServletResponse.SC_OK);

    interceptor.afterCompletion(request, response, new Object(), null);

    verify(igsPathogenLogger).log();
    assertThat(loggingContext.isStatus()).isTrue();
  }

  @Test
  void shouldLogAndSetFailureWhenResponseHasClientOrServerError() {
    when(response.getStatus()).thenReturn(FHIR_VALIDATION_ERROR.getHttpStatus().value());

    interceptor.afterCompletion(request, response, new Object(), null);

    verify(igsPathogenLogger).log();
    assertThat(loggingContext.isStatus()).isFalse();
  }

  @Test
  void shouldLogAndSetFailureWhenExceptionIsPresent() {
    interceptor.afterCompletion(request, response, new Object(), new RuntimeException("ex"));

    verify(igsPathogenLogger).log();
    assertThat(loggingContext.isStatus()).isFalse();
  }
}
