package de.gematik.demis.igs.service;

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

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public abstract class MinioTestBase {

  private static final String MINIO_ROOT_USER = "MY_ACCESS_KEY";
  private static final String MINIO_ROOT_PASSWORD = "VERY_VERY_SECURE_PASSWORD";

  protected static final GenericContainer<?> MINIO_CONTAINER =
      new GenericContainer<>("minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1")
          .withExposedPorts(9000)
          .withEnv("MINIO_ROOT_USER", MINIO_ROOT_USER)
          .withEnv("MINIO_ROOT_PASSWORD", MINIO_ROOT_PASSWORD)
          .waitingFor(
              new HttpWaitStrategy().forPort(9000).forPath("/minio/health/live").forStatusCode(200))
          .withCommand("server /mnt/data");

  static {
    MINIO_CONTAINER.start();
  }

  @DynamicPropertySource
  static void minioProperties(DynamicPropertyRegistry registry) {
    String storageUrl =
        "http://" + MINIO_CONTAINER.getHost() + ":" + MINIO_CONTAINER.getFirstMappedPort();
    registry.add("simple.storage.service.url", () -> storageUrl);
    registry.add("simple.storage.service.cluster-url", () -> storageUrl);
    registry.add("simple.storage.service.access-key", () -> MINIO_ROOT_USER);
    registry.add("simple.storage.service.secret-key", () -> MINIO_ROOT_PASSWORD);
  }
}
