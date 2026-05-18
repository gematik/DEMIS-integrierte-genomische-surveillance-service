package de.gematik.demis.igs.service.parser;

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

import static de.gematik.demis.igs.service.parser.FhirParser.serializeResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_XML;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import util.BaseUtil;

class FhirParserTest {

  private final BaseUtil testData = new BaseUtil();

  @ParameterizedTest
  @ValueSource(strings = {"application/json", "application/json+fhir", "application/fhir+json"})
  void testJsonSerialization(String contentType) {
    MediaType mediaType = MediaType.valueOf(contentType);
    String bundleString = serializeResource(testData.getDefaultBundle(), mediaType);
    assertThat(bundleString).isNotBlank().startsWith("{");
  }

  @Test
  void testXmlSerialization() {
    String bundleString = serializeResource(testData.getDefaultBundle(), APPLICATION_XML);
    assertThat(bundleString).isNotBlank().startsWith("<");
  }
}
