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

public class ErrorMessages {

  public static final String INVALID_COMPRESSED_FILE_ERROR_MSG = "Invalid compressed file";
  public static final String INVALID_DOCUMENT_TYPE_ERROR_MSG =
      "Invalid file format. Supported file formats are: FASTA, FASTQ, and GZIP.";
  public static final String HASH_ERROR_MSG = "Hash does not match";
  public static final String FILE_SIZE_TO_LARGE_ERROR_MSG =
      "File size exceeds maximum allowed size";
  public static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";
  public static final String WRONG_PATH_DELIVERED_ERROR_MSG = "wrong path delivered";
  public static final String RESOURCE_NOT_FOUND_ERROR_MSG = "Requested resource not found";
  public static final String EMPTY_DOCUMENT_ERROR_MSG = "Empty document detected";
}
