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

import static de.gematik.demis.igs.service.exception.ErrorCode.INTERNAL_SERVER_ERROR;

import de.gematik.demis.service.base.error.ServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
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
  public static final String END_CERTIFICATE = "END CERTIFICATE";
  private final SimpleStorageServiceConfiguration s3configuration;

  @Override
  public TrustManager[] trustManagers() {
    TrustManager[] trustManagers;
    try {
      // initialize an empty truststore
      KeyStore truststore = KeyStore.getInstance(KEYSTORE_FORMAT);
      truststore.load(null, UUID.randomUUID().toString().toCharArray());

      loadTrustedCertsToTruststore(truststore);

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(truststore);

      trustManagers = trustManagerFactory.getTrustManagers();
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      throw new ServiceException(
          INTERNAL_SERVER_ERROR.getCode(), "Error while creating truststore", exception);
    }
    return trustManagers;
  }

  private void loadTrustedCertsToTruststore(KeyStore truststore) throws CertificateException {
    addCertificatesToTruststore(s3configuration.getStorageTlsCertificate(), truststore);
    addCertificatesToTruststore(s3configuration.getStorageTlsCertificateInternal(), truststore);
  }

  private void addCertificatesToTruststore(String base64KeyChain, KeyStore truststore)
      throws CertificateException {
    String certFile = new String(Base64.getDecoder().decode(base64KeyChain.replaceAll("\\s", "")));
    int occurrences = certFile.split("\\b" + END_CERTIFICATE + "\\b", -1).length - 1;
    log.info("Adding {} certificates to truststore", occurrences);
    int errors = 0;
    try (InputStream is = new ByteArrayInputStream(certFile.getBytes())) {
      CertificateFactory cf = CertificateFactory.getInstance(CERTIFICATE_FORMAT);
      int certIndex = 1;
      while (is.available() > 0) {
        try {
          Certificate cert = cf.generateCertificate(is);
          String alias = CERTIFICATE_ALIAS + certIndex++;
          truststore.setCertificateEntry(alias, cert);
        } catch (Exception ex) {
          ++errors;
          log.warn("Error while adding certificate to truststore: {}", ex.getMessage());
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (errors >= occurrences) {
      log.error("Could not load any certificate to truststore");
      throw new ServiceException(
          INTERNAL_SERVER_ERROR.getCode(), "Error while creating truststore");
    } else if (errors > 0) {
      log.warn("Could not load {} certificates to truststore", errors);
    }
  }
}
