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

import static de.gematik.demis.igs.service.exception.ErrorCode.INVALID_SENDER;
import static lombok.AccessLevel.PRIVATE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.igs.service.exception.IgsServiceException;
import java.util.Base64;
import lombok.NoArgsConstructor;

/** Static utility class for checking roles in a JWT token */
@NoArgsConstructor(access = PRIVATE)
public class JwtUtils {

  private static ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Check if a token contains a specific role
   *
   * @param token
   * @param role
   * @return
   */
  public static boolean hasRole(String token, String role) {
    try {
      if (token.startsWith("Bearer ")) {
        token = token.substring(7);
      }
      String payload = new String(Base64.getDecoder().decode(token.split("\\.")[1]));
      JsonNode jsonNode = objectMapper.readTree(payload).path("realm_access").path("roles");
      if (jsonNode.isArray()) {
        for (JsonNode node : jsonNode) {
          if (node.asText().equals(role)) {
            return true;
          }
        }
      }
      return false;
    } catch (IndexOutOfBoundsException | JsonProcessingException e) {
      throw new IgsServiceException(INVALID_SENDER, "Token invalid");
    }
  }
}
