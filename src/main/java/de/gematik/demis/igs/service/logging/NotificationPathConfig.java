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

import static de.gematik.demis.igs.service.api.NotificationController.FHIR_BUNDLE_BASE;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * NotificationPathConfig is a configuration class that implements the WebMvcConfigurer interface.
 * It is responsible for adding the LoggingInterceptor to the interceptor registry for the specified
 * FHIR bundle base path.
 */
@Configuration
@RequiredArgsConstructor
public class NotificationPathConfig implements WebMvcConfigurer {

  private final LoggingInterceptor interceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {

    registry.addInterceptor(interceptor).addPathPatterns(FHIR_BUNDLE_BASE);
  }
}
