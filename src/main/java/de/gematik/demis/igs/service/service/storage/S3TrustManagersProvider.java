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

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.UUID;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.http.TlsTrustManagersProvider;

/**
 * Provides an implementation of TlsTrustManagersProvider that contains the certificate of the S3
 * storage.
 */
@RequiredArgsConstructor
@Slf4j
public class S3TrustManagersProvider implements TlsTrustManagersProvider {

  public static final String KEYSTORE_FORMAT = "PKCS12";
  public static final String CERTIFICATE_FORMAT = "X.509";
  public static final String CERTIFICATE_ALIAS = "storate-certificate";
  private final SimpleStorageServiceConfiguration s3configuration;

  @Override
  public TrustManager[] trustManagers() {
    TrustManager[] trustManagers = new TrustManager[] {};
    try {
      // initialize an empty truststore
      KeyStore truststore = KeyStore.getInstance(KEYSTORE_FORMAT);
      truststore.load(null, UUID.randomUUID().toString().toCharArray());

      addCertificatesToTruststore(truststore);

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(truststore);

      trustManagers = trustManagerFactory.getTrustManagers();
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
    }
    return trustManagers;
  }

  private void addCertificatesToTruststore(KeyStore truststore)
      throws CertificateException, KeyStoreException {
    addCertificateToTruststore(
        decodeCertificate(s3configuration.getStorageTlsCertificate()), truststore);
    addCertificateToTruststore(
        decodeCertificate(s3configuration.getStorageTlsCertificateInternal()), truststore);
  }

  private byte[] decodeCertificate(String certificateBase64) {
    return Base64.getDecoder().decode(certificateBase64);
  }

  private void addCertificateToTruststore(byte[] certificate, KeyStore truststore)
      throws CertificateException, KeyStoreException {
    CertificateFactory certificateFactory = CertificateFactory.getInstance(CERTIFICATE_FORMAT);
    X509Certificate x509Certificate =
        (X509Certificate)
            certificateFactory.generateCertificate(new ByteArrayInputStream(certificate));

    truststore.setCertificateEntry(CERTIFICATE_ALIAS, x509Certificate);
  }
}
