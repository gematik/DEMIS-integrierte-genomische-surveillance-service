package de.gematik.demis.igs.service;

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

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

  private final ThreadConfig config;

  @Bean(name = "defaultTaskExecutor")
  public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(config.getCorePoolSize());
    executor.setMaxPoolSize(config.getMaxPoolSize());
    executor.setQueueCapacity(config.getQueueCapacity());
    executor.setThreadNamePrefix(config.getThreadNamePrefix());
    executor.setTaskDecorator(
        runnable -> {
          Context context = Context.current();
          return () -> {
            try (Scope scope = context.makeCurrent()) {
              runnable.run();
            }
          };
        });
    executor.initialize();
    return executor;
  }

  @Override
  public Executor getAsyncExecutor() {
    return taskExecutor();
  }
}
