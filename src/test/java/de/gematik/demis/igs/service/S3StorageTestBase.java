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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

public abstract class S3StorageTestBase {

  private static final String STORAGE_ROOT_USER = "MY_ACCESS_KEY";
  private static final String STORAGE_ROOT_PASSWORD = "VERY_VERY_SECURE_PASSWORD";
  private static final Path S3_CONFIG_FILE = createS3ConfigFile();

  protected static final GenericContainer<?> STORAGE_CONTAINER =
      new GenericContainer<>("chrislusf/seaweedfs:4.40")
          .withExposedPorts(8333)
          .withCopyFileToContainer(
              MountableFile.forHostPath(S3_CONFIG_FILE.toString()), "/etc/seaweedfs/s3.json")
          .waitingFor(
              Wait.forLogMessage(".*Start Seaweed S3 API Server.*at http port 8333.*\\n", 1))
          .withStartupTimeout(Duration.ofSeconds(60))
          .withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json");

  private static Path createS3ConfigFile() {
    try {
      Path configFile = Files.createTempFile("seaweedfs-s3-", ".json");
      Files.writeString(
          configFile,
          """
              {
                "identities": [
                  {
                    "name": "ACCESS_KEY",
                    "credentials": [
                      {
                        "accessKey": "ACCESS_KEY",
                        "secretKey": "PASSWORD"
                      }
                    ],
                    "actions": ["Admin", "Read", "Write"]
                  }
                ]
              }
              """
              .replace("ACCESS_KEY", STORAGE_ROOT_USER)
              .replace("PASSWORD", STORAGE_ROOT_PASSWORD));
      configFile.toFile().deleteOnExit();
      return configFile;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  static {
    STORAGE_CONTAINER.start();
  }

  @DynamicPropertySource
  static void minioProperties(DynamicPropertyRegistry registry) {
    String storageUrl =
        "http://" + STORAGE_CONTAINER.getHost() + ":" + STORAGE_CONTAINER.getFirstMappedPort();
    registry.add("simple.storage.service.url", () -> storageUrl);
    registry.add("simple.storage.service.cluster-url", () -> storageUrl);
    registry.add("simple.storage.service.access-key", () -> STORAGE_ROOT_USER);
    registry.add("simple.storage.service.secret-key", () -> STORAGE_ROOT_PASSWORD);
  }
}
