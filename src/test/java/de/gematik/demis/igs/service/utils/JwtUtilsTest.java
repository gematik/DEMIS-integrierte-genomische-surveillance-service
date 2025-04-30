package de.gematik.demis.igs.service.utils;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static util.BaseUtil.TOKEN_FAST_A;
import static util.BaseUtil.TOKEN_NOT_PARSABLE;
import static util.BaseUtil.TOKEN_NRZ;

import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtUtilsTest {

  static Stream<Arguments> shouldReturnTrueIfTokenContainsRoles() {
    return Stream.of(
        Arguments.of(
            "TokenNrz",
            TOKEN_NRZ,
            List.of(
                "igs-notification-data-sender",
                "disease-notification-sender",
                "offline_access",
                "default-roles-portal",
                "uma_authorization",
                "pathogen-notification-sender",
                "igs-sequence-data-sender")),
        Arguments.of(
            "TokenFastA",
            TOKEN_FAST_A,
            List.of(
                "igs-notification-data-sender",
                "disease-notification-sender",
                "offline_access",
                "default-roles-portal",
                "uma_authorization",
                "pathogen-notification-sender",
                "igs-sequence-data-sender-fasta-only")));
  }

  private static Stream<Arguments> shouldReturnFalseIfRoleNotContainedInToken() {
    return Stream.of(
        Arguments.of(TOKEN_NRZ, "igs-notification-data-sender-fasta-only"),
        Arguments.of(TOKEN_FAST_A, "igs-sequence-data-sender"));
  }

  @SneakyThrows
  @MethodSource
  @ParameterizedTest(name = "{0}")
  void shouldReturnTrueIfTokenContainsRoles(String testName, String token, List<String> roles) {
    roles.forEach(
        role ->
            assertThat(JwtUtils.hasRole(token, role)).as("Does not have role: " + role).isTrue());
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource
  void shouldReturnFalseIfRoleNotContainedInToken(String token, String role) {
    assertThat(JwtUtils.hasRole(token, role)).isFalse();
  }

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(strings = {TOKEN_NOT_PARSABLE, "Not.parsable.token"})
  void shouldThrowExceptionIfTokenNotParsable(String token) {
    assertThrows(IgsServiceException.class, () -> JwtUtils.hasRole(token, "asdf"));
  }
}
