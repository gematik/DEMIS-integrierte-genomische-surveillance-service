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

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Provides beans for the S3 client and presigner */
@Configuration
@RequiredArgsConstructor
public class S3Config {

  private final SimpleStorageServiceConfiguration s3configuration;
  private final HttpClientProvider provider;

  @Bean
  public S3Client s3Client() {
    AwsBasicCredentials awsCredentials =
        AwsBasicCredentials.create(s3configuration.getAccessKey(), s3configuration.getSecretKey());
    StaticCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(awsCredentials);
    return S3Client.builder()
        .region(Region.EU_CENTRAL_2)
        .credentialsProvider(credentialsProvider)
        .endpointOverride(URI.create(s3configuration.getClusterUrl()))
        .httpClient(provider.createHttpClient())
        .forcePathStyle(true) // MinIO requires path-style access
        .build();
  }

  @Bean
  public S3Presigner presigner(@Autowired S3Client s3) {
    AwsBasicCredentials awsCredentials =
        AwsBasicCredentials.create(s3configuration.getAccessKey(), s3configuration.getSecretKey());
    StaticCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(awsCredentials);
    return S3Presigner.builder()
        .region(Region.EU_CENTRAL_2)
        .endpointOverride(URI.create(s3configuration.getUrl()))
        .credentialsProvider(credentialsProvider)
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .s3Client(s3)
        .build();
  }
}
