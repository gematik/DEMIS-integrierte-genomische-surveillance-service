/*
 * Copyright [2024], gematik GmbH
 *
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
 */

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
 * #L%
 */

import static de.gematik.demis.igs.service.utils.Constants.PROCESS_NOTIFICATION_RESPONSE_PROFILE;
import static java.util.Objects.requireNonNullElseGet;

import de.gematik.demis.igs.service.exception.ErrorCode;
import java.util.Comparator;
import java.util.UUID;
import lombok.Setter;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

/** Service class for edit and processing FHIR OperationOutcome resources */
@Service
public class FhirOperationOutcomeOperationService {

  private final SeverityComparator severityComparator = new SeverityComparator();

  @Setter
  @Value("${igs.outcome-issue-threshold}")
  OperationOutcome.IssueSeverity outcomeIssueThreshold;

  /**
   * Adds the profile to the OperationOutcome and filters and orders the issues
   *
   * @param outcome the OperationOutcome to process
   * @return
   */
  public OperationOutcome success(OperationOutcome outcome) {
    processOutcome(outcome);
    outcome.getIssue().add(0, allOk());
    return outcome;
  }

  /**
   * Adds an error issue to the OperationOutcome and filters and orders the issues
   *
   * @param operationOutcome the OperationOutcome to process
   * @param status the HTTP status code
   * @param errorCode the error code
   * @param message the error message
   * @return the processed OperationOutcome
   */
  public OperationOutcome error(
      OperationOutcome operationOutcome,
      HttpStatusCode status,
      ErrorCode errorCode,
      String message) {
    final OperationOutcome result = requireNonNullElseGet(operationOutcome, OperationOutcome::new);

    result
        .addIssue()
        .setSeverity(IssueSeverity.ERROR)
        .setCode(status.value() >= 500 ? IssueType.EXCEPTION : IssueType.PROCESSING)
        .setDiagnostics(message)
        .setDetails(new CodeableConcept().addCoding(new Coding().setCode(errorCode.getCode())))
        .setId(UUID.randomUUID().toString());

    processOutcome(result);
    return result;
  }

  private void processOutcome(final OperationOutcome outcome) {
    PROCESS_NOTIFICATION_RESPONSE_PROFILE.applyTo(outcome);
    outcome.setText(createOutcomeText());
    filterOutcomeIssues(outcome);
    orderOutcomeIssues(outcome);
  }

  private void filterOutcomeIssues(final OperationOutcome outcome) {
    outcome
        .getIssue()
        .removeIf(
            issue -> severityComparator.compare(issue.getSeverity(), outcomeIssueThreshold) < 0);
  }

  private void orderOutcomeIssues(final OperationOutcome outcome) {
    outcome
        .getIssue()
        .sort(
            Comparator.comparing(
                OperationOutcomeIssueComponent::getSeverity, severityComparator.reversed()));
  }

  private Narrative createOutcomeText() {
    var div = new XhtmlNode(NodeType.Element, "div");
    div.addText("");
    return new Narrative().setStatus(Narrative.NarrativeStatus.GENERATED).setDiv(div);
  }

  private OperationOutcomeIssueComponent allOk() {
    return new OperationOutcomeIssueComponent()
        .setSeverity(IssueSeverity.INFORMATION)
        .setCode(IssueType.INFORMATIONAL)
        .setDetails(new CodeableConcept().setText("All OK"));
  }
}
