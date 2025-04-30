package de.gematik.demis.igs.service.service.fhir;

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

import static de.gematik.demis.igs.service.utils.Constants.PROCESS_NOTIFICATION_RESPONSE_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.NULL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;

import de.gematik.demis.igs.service.exception.ErrorCode;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import util.BaseUtil;

class FhirOperationOutcomeOperationServiceTest {

  private BaseUtil testUtil = new BaseUtil();
  private FhirOperationOutcomeOperationService underTest =
      new FhirOperationOutcomeOperationService();

  @Test
  void shouldAddProfileCorrectly() {
    OperationOutcome output = testUtil.generateOutcome(1, 0, 0, 0);
    output = underTest.success(output);
    assertThat(output.getMeta().getProfile().stream().map(CanonicalType::asStringValue))
        .contains(PROCESS_NOTIFICATION_RESPONSE_PROFILE.getUrl());
    assertThat(output.getMeta().getProfile()).hasSize(1);
  }

  static Stream<Arguments> shouldFilterIssuesCorrectly() {
    return Stream.of(
        Arguments.of(1, 1, 1, 1, FATAL, 2),
        Arguments.of(1, 1, 1, 1, ERROR, 3),
        Arguments.of(1, 1, 1, 1, WARNING, 4),
        Arguments.of(1, 1, 1, 1, INFORMATION, 5),
        Arguments.of(1, 1, 1, 1, NULL, 5),
        Arguments.of(5, 4, 3, 2, FATAL, 3),
        Arguments.of(5, 4, 3, 2, ERROR, 6),
        Arguments.of(5, 4, 3, 2, WARNING, 10),
        Arguments.of(5, 4, 3, 2, INFORMATION, 15),
        Arguments.of(5, 4, 3, 2, NULL, 15));
  }

  @ParameterizedTest
  @MethodSource("shouldFilterIssuesCorrectly")
  void shouldFilterOnSuccessIssuesCorrectly(
      int info, int warning, int error, int fatal, IssueSeverity lvl, int expected) {
    OperationOutcome outcome = testUtil.generateOutcome(info, warning, error, fatal);
    underTest.setOutcomeIssueThreshold(lvl);
    outcome = underTest.success(outcome);
    assertThat(outcome.getIssue()).hasSize(expected);
  }

  @ParameterizedTest
  @MethodSource("shouldFilterIssuesCorrectly")
  void shouldFilterOnErrorIssuesCorrectly(
      int info, int warning, int error, int fatal, IssueSeverity lvl, int expected) {
    OperationOutcome outcome = testUtil.generateOutcome(info, warning, error, fatal);
    underTest.setOutcomeIssueThreshold(lvl);
    outcome =
        underTest.error(
            outcome,
            HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCode.FHIR_VALIDATION_ERROR,
            "SomethingWrong");
    // -1 because the error message is added. In practice, a fatal error should break the process
    assertThat(outcome.getIssue()).hasSize(lvl.compareTo(FATAL) == 0 ? expected - 1 : expected);
  }
}
