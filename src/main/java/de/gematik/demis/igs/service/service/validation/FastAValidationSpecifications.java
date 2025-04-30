package de.gematik.demis.igs.service.service.validation;

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

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for FastA validation that got loaded from application.yaml. Each pathogen that
 * should be validated in an extended way should be defined here.
 */
@Slf4j
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "fasta.validation")
public class FastAValidationSpecifications {

  @Getter @Builder.Default private List<FastAConfig> fastAConfigs = new ArrayList<>();

  private Map<String, String> pathogen;

  @PostConstruct
  public void init() {
    if (pathogen != null) {
      createConfigsFromEnvironment();
    }
  }

  private void createConfigsFromEnvironment() {
    pathogen.forEach(
        (key, value) -> {
          String[] parts = value.split(",");
          if (parts.length != 3) {
            log.error("Invalid fasta validation configuration format for {}: {}", key, value);
            return;
          }
          if (key.length() != 4) {
            log.error(
                "Pathogen code must be 4 characters long without whitespaces. Found: {}", key);
            return;
          }
          try {
            FastAConfig config =
                FastAConfig.builder()
                    .name(key.toLowerCase())
                    .longest(Long.parseLong(parts[0].trim()))
                    .shortest(Long.parseLong(parts[1].trim()))
                    .percentageN(Double.parseDouble(parts[2].trim()))
                    .build();
            fastAConfigs.add(config);
            log.info("Added FastA validation configuration: {}", config);
          } catch (NumberFormatException e) {
            log.error("Error parsing fasta validation configuration for {}: {}", key, value, e);
          }
        });
  }

  /**
   * Find a configured FastAConfig by name. If not found returns null.
   *
   * @param name name of the searched config
   * @return
   */
  public FastAConfig findConfigByName(String name) {
    return fastAConfigs.stream()
        .filter(config -> config.name.equals(name))
        .findFirst()
        .orElse(null);
  }

  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  public static class FastAConfig {

    private String name;
    private long longest;
    private long shortest;
    private double percentageN;
  }
}
