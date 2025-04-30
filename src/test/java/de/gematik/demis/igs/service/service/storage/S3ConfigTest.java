package de.gematik.demis.igs.service.service.storage;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
class S3ConfigTest {

  public static final String CLUSTER_URL = "http://localhost:9000";
  @Mock private SimpleStorageServiceConfiguration s3configuration;

  @Mock private HttpClientProvider provider;

  private S3Config s3Config;

  @BeforeEach
  void setUp() {
    when(s3configuration.getAccessKey()).thenReturn("accessKey");
    when(s3configuration.getSecretKey()).thenReturn("secretKey");
    when(s3configuration.getClusterUrl()).thenReturn(CLUSTER_URL);
    s3Config = new S3Config(s3configuration, provider);
  }

  @Test
  void testS3ClientInitializedWithClusterUrl() {
    S3Client s3Client = s3Config.s3Client();
    assertThat(s3Client).isNotNull();
    assertThat(s3Client.serviceClientConfiguration().endpointOverride()).isPresent();
    assertThat(s3Client.serviceClientConfiguration().endpointOverride().get())
        .hasToString(CLUSTER_URL);
  }
}
