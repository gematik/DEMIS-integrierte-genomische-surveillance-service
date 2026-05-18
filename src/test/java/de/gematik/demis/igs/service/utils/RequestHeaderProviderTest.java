package de.gematik.demis.igs.service.utils;

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

import static de.gematik.demis.igs.service.utils.RequestHeaderProvider.HEADER_FHIR_API_VERSION_LEGACY;
import static de.gematik.demis.igs.service.utils.RequestHeaderProvider.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.igs.service.utils.RequestHeaderProvider.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.igs.service.utils.RequestHeaderProvider.HEADER_FHIR_PROFILE_LEGACY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestHeaderProviderTest {

  @Mock private HttpServletRequest httpServletRequest;
  private RequestHeaderProvider underTest;

  @BeforeEach
  public void setup() {
    underTest = new RequestHeaderProvider(httpServletRequest);
  }

  @Test
  void testReceiveApiVersion_FromRequest_NoHeader() {
    assertNull(underTest.receiveApiVersionsFromRequest());
  }

  @Test
  void testReceivePackageVersion_FromRequest_WithHeader() {
    final String headerValue = "1.0";
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE_VERSION)).thenReturn(headerValue);

    assertEquals(List.of(headerValue), underTest.receiveApiVersionsFromRequest());
  }

  @Test
  void testReceiveApiVersion_FromRequest_WithLegacyHeader() {
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE_VERSION)).thenReturn(null);
    final String headerValue = "1.0";
    when(httpServletRequest.getHeader(HEADER_FHIR_API_VERSION_LEGACY)).thenReturn(headerValue);

    assertEquals(List.of(headerValue), underTest.receiveApiVersionsFromRequest());
  }

  @Test
  void testReceiveFhirProfile_FromRequest_NoHeader() {
    assertNull(underTest.receiveFhirProfileFromRequest());
  }

  @Test
  void testReceiveFhirPackage_FromRequest_WithHeader() {
    final String headerValue = "profile1";
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE)).thenReturn(headerValue);

    assertEquals(List.of(headerValue), underTest.receiveFhirProfileFromRequest());
  }

  @Test
  void testReceiveFhirProfile_FromRequest_WithLegacyHeader() {
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE)).thenReturn(null);
    final String headerValue = "profile1";
    when(httpServletRequest.getHeader(HEADER_FHIR_PROFILE_LEGACY)).thenReturn(headerValue);

    assertEquals(List.of(headerValue), underTest.receiveFhirProfileFromRequest());
  }
}
