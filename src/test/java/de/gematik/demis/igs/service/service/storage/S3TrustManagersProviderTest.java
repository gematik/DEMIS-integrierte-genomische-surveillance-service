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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.gematik.demis.service.base.error.ServiceException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class S3TrustManagersProviderTest {

  @Mock SimpleStorageServiceConfiguration s3configuration;

  @Test
  @SneakyThrows
  void shouldAddSingleCertTwoTimes() {
    String singleCert =
        new String(
            Objects.requireNonNull(getClass().getResourceAsStream("/certs/singleCert"))
                .readAllBytes());
    when(s3configuration.getStorageTlsCertificate()).thenReturn(singleCert);
    when(s3configuration.getStorageTlsCertificateInternal()).thenReturn(singleCert);
    S3TrustManagersProvider provider = new S3TrustManagersProvider(s3configuration);
    checkTrustManagerHaveExpectedAmountOfTrustedCerts(provider, 1);
  }

  @Test
  @SneakyThrows
  void shouldAddMultipleCertsTwoTimes() {
    String singleCert =
        new String(
            Objects.requireNonNull(getClass().getResourceAsStream("/certs/multipleCerts"))
                .readAllBytes());
    when(s3configuration.getStorageTlsCertificate()).thenReturn(singleCert);
    when(s3configuration.getStorageTlsCertificateInternal()).thenReturn(singleCert);
    S3TrustManagersProvider provider = new S3TrustManagersProvider(s3configuration);
    checkTrustManagerHaveExpectedAmountOfTrustedCerts(provider, 6);
  }

  @Test
  @SneakyThrows
  void shouldLoadTrustedCertsToTruststoreWithFailingCertificate() {
    String singleCert =
        new String(
            Objects.requireNonNull(getClass().getResourceAsStream("/certs/oneValidOneInvalidCert"))
                .readAllBytes());
    when(s3configuration.getStorageTlsCertificate()).thenReturn(singleCert);
    when(s3configuration.getStorageTlsCertificateInternal()).thenReturn(singleCert);
    S3TrustManagersProvider provider = new S3TrustManagersProvider(s3configuration);
    checkTrustManagerHaveExpectedAmountOfTrustedCerts(provider, 1);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfNoCertCouldBeImported() {
    String singleCert =
        new String(
            Objects.requireNonNull(getClass().getResourceAsStream("/certs/invalidCert"))
                .readAllBytes());
    when(s3configuration.getStorageTlsCertificate()).thenReturn(singleCert);
    S3TrustManagersProvider provider = new S3TrustManagersProvider(s3configuration);
    assertThatThrownBy(provider::trustManagers).isInstanceOf(ServiceException.class);
  }

  private void checkTrustManagerHaveExpectedAmountOfTrustedCerts(
      S3TrustManagersProvider provider, int expectedAmount) {
    TrustManager[] trustManagers = provider.trustManagers();
    assertThat(trustManagers).hasSize(1);
    TrustManager trustManager = trustManagers[0];
    assertThat(trustManager).isInstanceOf(X509TrustManager.class);
    X509TrustManager x509TrustManager = (X509TrustManager) trustManager;
    X509Certificate[] trustedCerts = x509TrustManager.getAcceptedIssuers();
    assertThat(trustedCerts).isNotNull();
    assertThat(trustedCerts).hasSize(expectedAmount);
  }
}
