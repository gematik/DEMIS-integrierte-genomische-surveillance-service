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

import static lombok.AccessLevel.PRIVATE;

import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;

@NoArgsConstructor(access = PRIVATE)
public class Constants {

  public static final String VALIDATION_STATUS = "validation-status";
  public static final String UPLOAD_STATUS = "upload-status";
  public static final String UPLOAD_STATUS_DONE = "done";
  public static final String VALIDATION_DESCRIPTION = "validation-description";
  public static final String HASH_METADATA_NAME = "hash";

  public static final String HASH_ALGORITHM = "SHA-256";

  public static final String PROFILE_BASE_URL = "https://demis.rki.de/fhir/";
  public static final String LABORATORY_ID_URL =
      PROFILE_BASE_URL + "NamingSystem/DemisLaboratoryId";

  public static final String EXTENSION_URL = PROFILE_BASE_URL + "StructureDefinition/TransactionID";
  public static final String EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE =
      PROFILE_BASE_URL + "StructureDefinition/ReceptionTimeStamp";
  public static final String NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM =
      PROFILE_BASE_URL + "NamingSystem/NotificationBundleId";
  public static final String RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM =
      PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartment";

  public static final String RKI_DEPARTMENT_IDENTIFIER = "1.";

  public static final Profile<OperationOutcome> PROCESS_NOTIFICATION_RESPONSE_PROFILE =
      new Profile<>(PROFILE_BASE_URL + "StructureDefinition/ProcessNotificationResponse");

  public static class Profile<T extends Resource> {

    @Getter private final String url;

    private Profile(String url) {
      Objects.requireNonNull(url, "url");
      this.url = url;
    }

    /**
     * Applies the URL wrapped by this object as the profile on the give resource {@code t} if not
     * already present.
     *
     * @param t the resource on which the profile will be applied
     */
    public void applyTo(T t) {
      if (t.getMeta().getProfile().stream()
          .map(CanonicalType::asStringValue)
          .toList()
          .contains(url)) {
        return;
      }
      t.getMeta().addProfile(url);
    }
  }

  public enum ValidationStatus {
    VALIDATING,
    VALID,
    VALIDATION_FAILED,
    VALIDATION_NOT_INITIATED;

    public boolean isProcceeded() {
      return this == VALID || this == VALIDATION_FAILED;
    }
  }
}
