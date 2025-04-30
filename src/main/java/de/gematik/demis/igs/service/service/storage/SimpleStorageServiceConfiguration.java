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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** All configuration properties for the Storage Service */
@Component
@ConfigurationProperties(prefix = "simple.storage.service")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimpleStorageServiceConfiguration {

  private String url;
  private String clusterUrl;
  private String accessKey;
  private String secretKey;
  private Bucket uploadBucket;
  private Bucket validatedBucket;
  private long multipartMaxUploadSizeInBytes;
  private long multipartUploadChunkSizeInBytes;
  private int signedUrlExpirationInMinutes;
  private String storageTlsCertificate;
  private String storageTlsCertificateInternal;
  private boolean skipTrustStoreCreation;

  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Bucket {

    private String name;
    private Integer deletionDeadlineInDays;
  }
}
