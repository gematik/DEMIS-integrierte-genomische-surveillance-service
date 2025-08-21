package util;

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

import static de.gematik.demis.igs.service.utils.Constants.VALIDATION_STATUS;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALID;
import static de.gematik.demis.igs.service.utils.Constants.ValidationStatus.VALIDATION_FAILED;
import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNull;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.igs.service.utils.Pair;
import feign.Request;
import feign.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.CompositionRelatesToComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.MolecularSequence;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

public class BaseUtil {

  private static final FhirContext fhirContext = FhirContext.forR4Cached();

  public static final String TOKEN_NOT_PARSABLE =
      "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHM256RGc4LWFDMlZ2eVJrdHlXS0VoN1J3cXVTWGZPdE9BaWN4ejFsdVIwIn0eyJleHAiOjE3NDEyNTc4MzQsImlhdCI6MTc0MTI1NzUzNCwiYXV0aF90aW1lIjoxNzQxMjU0NTIyLCJqdGkiOiJjN2MxOTU0Zi1kOWMwLTQ5MWYtOTZjNC00MGFkZGUyYzJkZGIiLCJpc3MiOiJodHRwczovL2F1dGguaW5ncmVzcy5sb2NhbC9yZWFsbXMvUE9SVEFMIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6ImExYzUyZGI5LTFlYTAtNDZiMy05NDk1LTVjMjVmODhhNzk2MSIsInR5cCI6IkJlYXJlciIsImF6cCI6Im1lbGRlcG9ydGFsIiwic2lkIjoiNzllZjkzYzMtOGUwNi00YTBiLWJjNDAtOWI3Nzg2NzMzYmE4IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwczovL3BvcnRhbC5pbmdyZXNzLmxvY2FsIiwiaHR0cDovL2xvY2FsaG9zdDo0MjAwIiwiaHR0cHM6Ly9tZWxkdW5nLmluZ3Jlc3MubG9jYWwiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImlncy1ub3RpZmljYXRpb24tZGF0YS1zZW5kZXIiLCJkaXNlYXNlLW5vdGlmaWNhdGlvbi1zZW5kZXIiLCJvZmZsaW5lX2FjY2VzcyIsImlncy1zZXF1ZW5jZS1kYXRhLXNlbmRlciIsImRlZmF1bHQtcm9sZXMtcG9ydGFsIiwidW1hX2F1dGhvcml6YXRpb24iLCJwYXRob2dlbi1ub3RpZmljYXRpb24tc2VuZGVyIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6Im9wZW5pZCBlbWFpbCBwcm9maWxlIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJhY2NvdW50VHlwZSI6InBlcnNvbiIsIm5hbWUiOiJ0ZXN0LWlncy0xIEJ1bmRJRC1Nb2NrLWZlZGVyYXRlZCIsImFjY291bnRTb3VyY2UiOiJidW5kaWQiLCJhY2NvdW50SXNUZW1wb3JhcnkiOmZhbHNlLCJhY2NvdW50SWRlbnRpZmllciI6Imh0dHBzOi8vZGVtaXMucmtpLmRlL2ZoaXIvc2lkL0J1bmRJZEJQSzJ8Zy1jYjY1OWUwNy1iNTE2LTRmNzctOTMxZC1kNDIyOWRkMmJkOGIiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJnLWNiNjU5ZTA3LWI1MTYtNGY3Ny05MzFkLWQ0MjI5ZGQyYmQ4YiIsImxldmVsT2ZBc3N1cmFuY2UiOiJTVE9SSy1RQUEtTGV2ZWwtMSIsImdpdmVuX25hbWUiOiJ0ZXN0LWlncy0xIiwiZmFtaWx5X25hbWUiOiJCdW5kSUQtTW9jay1mZWRlcmF0ZWQiLCJ1c2VybmFtZSI6ImctY2I2NTllMDctYjUxNi00Zjc3LTkzMWQtZDQyMjlkZDJiZDhiIn0AQuCuXkzpLDF0bsy_KL8I2lQZ5Pq1sWZ-HJ4NL0qpWCAw6wCa0SggAo70PYHuOa-0emLmqPedFEgiZnJMHpHYwha_GOyCZzyvUnC0fjhaITZD4KyEJvDW8r74PLGbFpnwfFSyaAyM921xCvHB5Sj0Jx3e-sE-SUKvv5-CVqZ5-1uDHFLg-NnV8ZKFToMbdGe9esQ8tfyD_RWWuGE-RUed78Ytz5O5OZ_YlkNuNYoh2junPxwVvCnL_iaZalJvzTtub1m4HXrrO0TPQRkIxiYTTbQ6lj-RZ67T3jTvsP-kRiV-wL62vVuHWG2ruYtQ-k2Kl_m87KdxNgwujbYqvDxx0yD_NffFOhv7dNP4OSWwWv2uPaRgZphr79L4yYtUXdUsn_RhH3QGjCXIlHfnfAPaIa0ino10UFZToToO9qyKK2XEpAUpqvQHHHqKQLEXjSBFMHlk7tipSXGzMFcp5dGjPfUU4wClhPeTMQu-r7SuTcf04ZKOJgsxusa9stA_h0uhEB74Fzbdi0u9g123Kllm0Ufh19xw02J0dHhf5hHilxdTJCYJ5DjUZ1MtfmEMcRF5Uqx2q3eQGQcIouEhpkQ9dBBkjQCzMdlSh4rjYPypStvn4SVs69liq0MG-deUnw7Y7um9QCf0OGmOFyoqMgphX6KFCGXyJS9hd7wjB4oFlg";
  public static final String TOKEN_NRZ =
      "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHM256RGc4LWFDMlZ2eVJrdHlXS0VoN1J3cXVTWGZPdE9BaWN4ejFsdVIwIn0.eyJleHAiOjE3NDEyNTc4MzQsImlhdCI6MTc0MTI1NzUzNCwiYXV0aF90aW1lIjoxNzQxMjU0NTIyLCJqdGkiOiJjN2MxOTU0Zi1kOWMwLTQ5MWYtOTZjNC00MGFkZGUyYzJkZGIiLCJpc3MiOiJodHRwczovL2F1dGguaW5ncmVzcy5sb2NhbC9yZWFsbXMvUE9SVEFMIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6ImExYzUyZGI5LTFlYTAtNDZiMy05NDk1LTVjMjVmODhhNzk2MSIsInR5cCI6IkJlYXJlciIsImF6cCI6Im1lbGRlcG9ydGFsIiwic2lkIjoiNzllZjkzYzMtOGUwNi00YTBiLWJjNDAtOWI3Nzg2NzMzYmE4IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwczovL3BvcnRhbC5pbmdyZXNzLmxvY2FsIiwiaHR0cDovL2xvY2FsaG9zdDo0MjAwIiwiaHR0cHM6Ly9tZWxkdW5nLmluZ3Jlc3MubG9jYWwiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImlncy1ub3RpZmljYXRpb24tZGF0YS1zZW5kZXIiLCJkaXNlYXNlLW5vdGlmaWNhdGlvbi1zZW5kZXIiLCJvZmZsaW5lX2FjY2VzcyIsImlncy1zZXF1ZW5jZS1kYXRhLXNlbmRlciIsImRlZmF1bHQtcm9sZXMtcG9ydGFsIiwidW1hX2F1dGhvcml6YXRpb24iLCJwYXRob2dlbi1ub3RpZmljYXRpb24tc2VuZGVyIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6Im9wZW5pZCBlbWFpbCBwcm9maWxlIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJhY2NvdW50VHlwZSI6InBlcnNvbiIsIm5hbWUiOiJ0ZXN0LWlncy0xIEJ1bmRJRC1Nb2NrLWZlZGVyYXRlZCIsImFjY291bnRTb3VyY2UiOiJidW5kaWQiLCJhY2NvdW50SXNUZW1wb3JhcnkiOmZhbHNlLCJhY2NvdW50SWRlbnRpZmllciI6Imh0dHBzOi8vZGVtaXMucmtpLmRlL2ZoaXIvc2lkL0J1bmRJZEJQSzJ8Zy1jYjY1OWUwNy1iNTE2LTRmNzctOTMxZC1kNDIyOWRkMmJkOGIiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJnLWNiNjU5ZTA3LWI1MTYtNGY3Ny05MzFkLWQ0MjI5ZGQyYmQ4YiIsImxldmVsT2ZBc3N1cmFuY2UiOiJTVE9SSy1RQUEtTGV2ZWwtMSIsImdpdmVuX25hbWUiOiJ0ZXN0LWlncy0xIiwiZmFtaWx5X25hbWUiOiJCdW5kSUQtTW9jay1mZWRlcmF0ZWQiLCJ1c2VybmFtZSI6ImctY2I2NTllMDctYjUxNi00Zjc3LTkzMWQtZDQyMjlkZDJiZDhiIn0.AQuCuXkzpLDF0bsy_KL8I2lQZ5Pq1sWZ-HJ4NL0qpWCAw6wCa0SggAo70PYHuOa-0emLmqPedFEgiZnJMHpHYwha_GOyCZzyvUnC0fjhaITZD4KyEJvDW8r74PLGbFpnwfFSyaAyM921xCvHB5Sj0Jx3e-sE-SUKvv5-CVqZ5-1uDHFLg-NnV8ZKFToMbdGe9esQ8tfyD_RWWuGE-RUed78Ytz5O5OZ_YlkNuNYoh2junPxwVvCnL_iaZalJvzTtub1m4HXrrO0TPQRkIxiYTTbQ6lj-RZ67T3jTvsP-kRiV-wL62vVuHWG2ruYtQ-k2Kl_m87KdxNgwujbYqvDxx0yD_NffFOhv7dNP4OSWwWv2uPaRgZphr79L4yYtUXdUsn_RhH3QGjCXIlHfnfAPaIa0ino10UFZToToO9qyKK2XEpAUpqvQHHHqKQLEXjSBFMHlk7tipSXGzMFcp5dGjPfUU4wClhPeTMQu-r7SuTcf04ZKOJgsxusa9stA_h0uhEB74Fzbdi0u9g123Kllm0Ufh19xw02J0dHhf5hHilxdTJCYJ5DjUZ1MtfmEMcRF5Uqx2q3eQGQcIouEhpkQ9dBBkjQCzMdlSh4rjYPypStvn4SVs69liq0MG-deUnw7Y7um9QCf0OGmOFyoqMgphX6KFCGXyJS9hd7wjB4oFlg";
  public static final String TOKEN_FAST_A =
      "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHM256RGc4LWFDMlZ2eVJrdHlXS0VoN1J3cXVTWGZPdE9BaWN4ejFsdVIwIn0.eyJleHAiOjE3NDEzNjE1NzksImlhdCI6MTc0MTM2MTI3OSwiYXV0aF90aW1lIjoxNzQxMzYxMjc5LCJqdGkiOiI5OWMxY2QyNi03NjRlLTQ3OWYtOWVmOC0yNTE0NzVkNmQ0YWMiLCJpc3MiOiJodHRwczovL2F1dGguaW5ncmVzcy5sb2NhbC9yZWFsbXMvUE9SVEFMIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6ImU2OTg2MGQyLTcxZTUtNGE0Ny1hMzllLTEwMGM2NGE4NjM4MCIsInR5cCI6IkJlYXJlciIsImF6cCI6Im1lbGRlcG9ydGFsIiwic2lkIjoiMzY2ZTRmZjAtZDQ1Ni00N2NjLWIyODctNmVlZmRmY2ZkNjI3IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwczovL3BvcnRhbC5pbmdyZXNzLmxvY2FsIiwiaHR0cDovL2xvY2FsaG9zdDo0MjAwIiwiaHR0cHM6Ly9tZWxkdW5nLmluZ3Jlc3MubG9jYWwiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImlncy1ub3RpZmljYXRpb24tZGF0YS1zZW5kZXIiLCJkaXNlYXNlLW5vdGlmaWNhdGlvbi1zZW5kZXIiLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtcG9ydGFsIiwidW1hX2F1dGhvcml6YXRpb24iLCJwYXRob2dlbi1ub3RpZmljYXRpb24tc2VuZGVyIiwiaWdzLXNlcXVlbmNlLWRhdGEtc2VuZGVyLWZhc3RhLW9ubHkiXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIGVtYWlsIHByb2ZpbGUiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsImFjY291bnRUeXBlIjoicGVyc29uIiwiYWNjb3VudFNvdXJjZSI6ImJ1bmRpZCIsImFjY291bnRJc1RlbXBvcmFyeSI6dHJ1ZSwiYWNjb3VudElkZW50aWZpZXIiOiJodHRwczovL2RlbWlzLnJraS5kZS9maGlyL3NpZC9CdW5kSWRCUEsyfGctMmYyMTNkNTAtMWRjOS00OGNhLTk3MGMtYzE2NWVmNjQzYWUzIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiZy0yZjIxM2Q1MC0xZGM5LTQ4Y2EtOTcwYy1jMTY1ZWY2NDNhZTMiLCJsZXZlbE9mQXNzdXJhbmNlIjoiU1RPUkstUUFBLUxldmVsLTEiLCJ1c2VybmFtZSI6ImctMmYyMTNkNTAtMWRjOS00OGNhLTk3MGMtYzE2NWVmNjQzYWUzIn0.rqbrayo2zKkFHXCpEHzt0g4qIsHkwg-Xt9gKRlVfE5rKsMx1lFrcBuCSIrKWQHTynWrOxv68_lZY1Wk0kuJHBl8q5vH6xax4-d5Mi063XhOsb_AkRaBTgRq4z9Bbk8pios7vvNCbxTCL0NyxZqUvpBuz0DvG1VXFDNX_DXdMXBn4GNJe7b0WA-npI60HXJ8gIME78FWigEYwwbM3EwKuqLOAPxIFtQSZFWnWJhR0xXykN03rm-XUSsC3W0-qPtKG-npmF20Pu8P2QYh4fDfAxOPcERHPJ68sbJom0Uc_1qizzjnjEghwU9GHNaUhjKmpdhlwgkjT0m2aM6aGZSN41b1evsR64XYJy3ipNg96HGT-9hkF_xH4I_1u1NMm3btpaDqLL7f6Y-bJu-DtF61yf_prKbIHodGdX0mpo2bJyjJ0R4GTGcqgBTZhnEjxlT70gQ6f4nvsOCq6CoME9xg3-aNCFP-fv1SOuHg7u7sBogJb1ranahJcnsUJdNxJmBqNz_YiMVegmUcrSnNvdxp-4Xu1jHZuE6dx9Hkh1MNUVScWiab3uJ4ndLvOt4codBdOKWA8Z3kstRZgj3F3GBQ1kd49i6GtyEf9GXvpSsDfXU4HJ9FnqkueLfixNkAkIGWdFWdM1i_asz_vA-C8SBce5RJZFf3s_dV1dfJfsuxRKsc";
  public static final String PATH_TO_DOCUMENT_REFERENCE_JSON =
      "createDocumentReference/createDocumentReference.json";
  public static final String PATH_TO_DOCUMENT_REFERENCE_XML =
      "createDocumentReference/createDocumentReference.xml";
  public static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITHOUT_CONTENT =
      "createDocumentReference/documentReferenceWithoutContent.json";
  public static final String PATH_TO_DOCUMENT_REFERENCE_JSON_WITH_INVALID_DATE =
      "createDocumentReference/documentReferenceInvalidDate.json";
  public static final String PATH_TO_FASTA = "sampleSequenceData/Sample.fa";
  public static final String PATH_TO_FASTA_ONLY_N = "sampleSequenceData/Sample_only_n.fa";
  public static final String PATH_TO_FASTA_WITH_PATHOGEN_SHORT =
      "sampleSequenceData/SamplePathogenShort.fa";
  public static final String PATH_TO_FASTA_WITH_PATHOGEN_LONG =
      "sampleSequenceData/SamplePathogenLong.fa";
  public static final String PATH_TO_FASTA_WITH_PATHOGEN_N51 =
      "sampleSequenceData/SamplePathogenN.fa";
  public static final String PATH_TO_FASTA_WITH_PATHOGEN_STRICT_VALID =
      "sampleSequenceData/SamplePathogenStrictValid.fa";
  public static final String PATH_TO_FASTA_GZIP = "sampleSequenceData/Sample.fa.gz";
  public static final String PATH_TO_FASTA_ZIP = "sampleSequenceData/Sample.fa.zip";
  public static final String PATH_TO_FASTQ = "sampleSequenceData/Sample12346_R2.fastq";
  public static final String PATH_TO_FASTQ_GZIP = "sampleSequenceData/Sample12346_R2.fastq.gz";
  public static final String PATH_TO_FASTA_INVALID = "sampleSequenceData/SampleInvalid.fa";
  public static final String PATH_TO_FASTQ_INVALID =
      "sampleSequenceData/Sample12346_R2Invalid.fastq";
  public static final String PATH_TO_GZIP_INVALID =
      "sampleSequenceData/Sample.fastq_corrupt_gzip.gzip";
  public static final String PATH_TO_IGS_NOTIFICATION = "igsNotification/notification.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_JSON = "igsNotification/IGSMeldung.json";
  public static final String PATH_TO_IGS_NOTIFICATION_WRONG_SEQUENCEING_LAB_ID =
      "igsNotification/notificationWithInvalidSequenceLabId.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_BUNDLE =
      "igsNotification/notificationAsBundle.xml";
  public static final String PATH_TO_INVALID_IGS_NOTIFICATION =
      "igsNotification/notificationInvalid.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_COMPOSITION =
      "igsNotification/notificationWithoutComposition.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_RELATES_TO =
      "igsNotification/notificationWithoutRelatesTo.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_MOLECULAR_SEQUENCE =
      "igsNotification/notificationWithoutMolecularSequence.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_DIAGNOSTIC_REPORT =
      "igsNotification/notificationWithoutDiagnosticReport.xml";
  public static final String PATH_TO_IGS_NOTIFICATION_WITHOUT_SPECIMEN =
      "igsNotification/notificationWithoutSpecimen.xml";
  public static final String PATH_TO_ORGANIZATION_RESSOURCE = "igsNotification/organization.xml";
  public static final String PROVENANCE_RESOURCE = "provenance/provenanceResource.json";
  private final Parameters parameter;

  @SneakyThrows
  public BaseUtil() {
    parameter =
        fhirContext
            .newXmlParser()
            .parseResource(Parameters.class, readFileToString(PATH_TO_IGS_NOTIFICATION));
  }

  public Bundle getBundleFromJsonString(String value) {
    return fhirContext.newJsonParser().parseResource(Bundle.class, value);
  }

  public Bundle getDefaultBundle() {
    return (Bundle) parameter.getParameter().getFirst().getResource();
  }

  public String getDefaultBundleAsString() {
    return fhirContext
        .newJsonParser()
        .encodeResourceToString(parameter.getParameter().getFirst().getResource());
  }

  public String getDefaultCompositionId() {
    return getDefaultBundle().getEntry().getFirst().getResource().getIdPart();
  }

  @SneakyThrows
  public Bundle getBundleFromFile(String path) {
    return (Bundle)
        ((Parameters) fhirContext.newXmlParser().parseResource(readFileToString(path)))
            .getParameter()
            .getFirst()
            .getResource();
  }

  @SneakyThrows
  public <T extends IBaseResource> T readFileToResource(String filePath, Class<T> clazz) {
    String resource = readFileToString(filePath);
    if (resource.startsWith("<")) {
      return fhirContext.newXmlParser().parseResource(clazz, resource);
    }
    return fhirContext.newJsonParser().parseResource(clazz, resource);
  }

  public String readFileToString(String relativePath) throws IOException, URISyntaxException {
    Path pathToDocumentReferenceRequest =
        Path.of(requireNonNull(getClass().getClassLoader().getResource(relativePath)).toURI());
    return readString(pathToDocumentReferenceRequest);
  }

  public byte[] readFileToByteArray(String relativePath) throws IOException, URISyntaxException {
    return readFileToString(relativePath).getBytes();
  }

  public InputStream readFileToInputStream(String relativePath) throws IOException {
    ClassPathResource resource = new ClassPathResource(relativePath);
    return resource.getInputStream();
  }

  public Pair getFirstBytesOfFile(String relativePath) throws IOException {
    InputStream stream = readFileToInputStream(relativePath);
    return new Pair(String.valueOf(stream.read()), String.valueOf(stream.read()));
  }

  public long getFileSize(String relativePath) throws URISyntaxException {
    return new File(requireNonNull(getClass().getClassLoader().getResource(relativePath)).toURI())
        .length();
  }

  public String getFilePath(String relativePath) throws URISyntaxException {
    return requireNonNull(getClass().getClassLoader().getResource(relativePath)).toURI().getPath();
  }

  public Specimen defaultSpecimen() {
    return (Specimen)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Specimen)
                .findFirst()
                .orElseThrow()
                .getResource();
  }

  public DiagnosticReport defaultDiagnosticReport() {
    return (DiagnosticReport)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.DiagnosticReport)
                .findFirst()
                .orElseThrow()
                .getResource();
  }

  public Organization defaultOrganization() {
    return (Organization)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Organization)
                .toList()
                .getLast()
                .getResource();
  }

  public MolecularSequence defaultMolecularSequence() {
    return (MolecularSequence)
        ((Bundle) parameter.getParameter().getFirst().getResource())
            .getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.MolecularSequence)
                .findFirst()
                .orElseThrow()
                .getResource();
  }

  public CompositionRelatesToComponent defaultRelatesToComponent() {
    Bundle bundle = (Bundle) parameter.getParameter().getFirst().getResource();
    Composition composition =
        (Composition)
            bundle.getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Composition)
                .findFirst()
                .orElseThrow()
                .getResource();
    return composition.getRelatesTo().getFirst();
  }

  public Response createOutcomeResponse(IssueSeverity lvl) {
    String body =
        switch (lvl) {
          case INFORMATION -> getSuccessOutcome();
          case WARNING -> getWarningOutcome();
          case ERROR -> getErrorOutcome();
          case FATAL -> getFatalOutcome();
          case NULL -> null;
        };
    int status =
        switch (lvl) {
          case INFORMATION -> 200;
          case WARNING, ERROR, FATAL -> 422;
          case NULL -> 0;
        };
    return buildResponseWithDefaultRequest(status, body);
  }

  public Response createBadRequestResponse() {
    return buildResponseWithDefaultRequest(400, null);
  }

  public String translateDocumentReferenceToString(
      MediaType mediaType, DocumentReference documentReference) {
    if (mediaType == APPLICATION_JSON) {
      return fhirContext.newJsonParser().encodeResourceToString(documentReference);
    }
    return fhirContext.newXmlParser().encodeResourceToString(documentReference);
  }

  public String generateDocumentReferenceJsonForFile(String filePath)
      throws IOException, NoSuchAlgorithmException {
    return translateDocumentReferenceToString(
        APPLICATION_JSON, generateDocumentReference(filePath));
  }

  public String generateFastQDocumentReferenceXmlString(String filePath)
      throws IOException, NoSuchAlgorithmException {
    return translateDocumentReferenceToString(APPLICATION_XML, generateDocumentReference(filePath));
  }

  public DocumentReference generateDocumentReference(String path)
      throws IOException, NoSuchAlgorithmException {
    String hash = calcHashOnFile(path);

    Attachment attachment = new Attachment();
    attachment.setHashElement(new Base64BinaryType(hash));
    return new DocumentReference()
        .addContent(
            new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
  }

  public String calcHashOnFile(String path) throws IOException, NoSuchAlgorithmException {
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
    try (FileInputStream fis =
            new FileInputStream(getClass().getClassLoader().getResource(path).getPath());
        DigestInputStream dis = new DigestInputStream(fis, messageDigest)) {

      byte[] buffer = new byte[4096];
      while (dis.read(buffer) != -1) {
        // Just read buffer; DigestInputStream automatically updates digest
      }
    }
    byte[] hashBytes = messageDigest.digest();
    StringBuilder hexString = new StringBuilder();
    for (byte b : hashBytes) {
      hexString.append(String.format("%02x", b));
    }
    return hexString.toString();
  }

  private Response buildResponseWithDefaultRequest(int status, String body) {
    return Response.builder()
        .status(status)
        .request(
            Request.create(
                Request.HttpMethod.GET,
                "https://example.com",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8))
        .body(body, StandardCharsets.UTF_8)
        .build();
  }

  private String getSuccessOutcome() {
    return generateOutcomeAsJsonString(1, 0, 0, 0);
  }

  private String getFatalOutcome() {
    return generateOutcomeAsJsonString(0, 0, 0, 1);
  }

  private String getErrorOutcome() {
    return generateOutcomeAsJsonString(0, 0, 1, 0);
  }

  private String getWarningOutcome() {
    return generateOutcomeAsJsonString(0, 1, 0, 0);
  }

  public OperationOutcome generateOutcome(int info, int warning, int error, int fatal) {
    OperationOutcome outcome = generateOutcome();
    IntStream.range(0, info)
        .forEach(i -> outcome.addIssue().setSeverity(INFORMATION).getDetails().setText("All OK"));
    IntStream.range(0, warning)
        .forEach(
            i -> outcome.addIssue().setSeverity(WARNING).getDetails().setText("This is a warning"));
    IntStream.range(0, error)
        .forEach(
            i -> outcome.addIssue().setSeverity(ERROR).getDetails().setText("This is an error"));
    IntStream.range(0, fatal)
        .forEach(
            i -> outcome.addIssue().setSeverity(FATAL).getDetails().setText("This failed fatal"));
    return outcome;
  }

  private String generateOutcomeAsJsonString(int inform, int warning, int error, int fatal) {
    return fhirContext
        .newJsonParser()
        .encodeResourceToString(generateOutcome(inform, warning, error, fatal));
  }

  private static OperationOutcome generateOutcome() {
    OperationOutcome operationOutcome = new OperationOutcome();

    operationOutcome.setMeta(
        new Meta()
            .addProfile(
                "https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationResponse"));

    Narrative text = new Narrative();
    text.setStatus(Narrative.NarrativeStatus.GENERATED);
    XhtmlNode value = new XhtmlNode();
    value.setValue("http://www.w3.org/1999/xhtml");
    text.setDiv(value);
    operationOutcome.setText(text);
    return operationOutcome;
  }

  public boolean streamCompare(InputStream inputStream1, InputStream inputStream2)
      throws IOException {
    if (inputStream1 == inputStream2) {
      return true;
    }
    int byte1, byte2;
    do {
      byte1 = inputStream1.read();
      byte2 = inputStream2.read();
      if (byte1 != byte2) {
        return false;
      }
    } while (byte1 != -1);
    return true;
  }

  public Map<String, String> determineMetadataForValid() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("some_parameter", "some_value");
    metadata.put(VALIDATION_STATUS, VALID.toString());
    return metadata;
  }

  public Map<String, String> determineMetadataForInValid() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("some_parameter", "some_value");
    metadata.put(VALIDATION_STATUS, VALIDATION_FAILED.toString());
    return metadata;
  }
}
