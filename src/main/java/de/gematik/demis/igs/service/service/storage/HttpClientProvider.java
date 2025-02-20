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
 * #L%
 */

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.TlsTrustManagersProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/** Provides a custom HttpClient that accepts the server certificate */
@Component
@RequiredArgsConstructor
public class HttpClientProvider {

  private final SimpleStorageServiceConfiguration s3configuration;

  /**
   * Creates a new SdkHttpClient with a custom TrustManager that accepts the server certificate
   *
   * @return
   */
  @SneakyThrows
  public SdkHttpClient createHttpClient() {
    TlsTrustManagersProvider trustManagersProvider = new S3TrustManagersProvider(s3configuration);
    return ApacheHttpClient.builder().tlsTrustManagersProvider(trustManagersProvider).build();
  }
}
