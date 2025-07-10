<img align="right" width="250" height="47" src="../media/Gematik_Logo_Flag.png"/> <br/>

# Integrierte Genomische Surveillance Service

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
       <ul>
        <li><a href="#quality-gate">Quality Gate</a></li>
        <li><a href="#release-notes">Release Notes</a></li>
      </ul>
	</li>
    <li>
      <a href="#getting-started">Getting Started</a>
    </li>
    <li>
      <a href="#usage">Usage</a>
      <ul>
        <li><a href="#endpoints">Endpoints</a></li>
      </ul>
    </li>
    <li><a href="#configuration-of-retention-periods">Configuration of Retention Periods</a></li>
    <li><a href="#security-policy">Security Policy</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

Integrated Genomic Surveillance (IGS) is an effective public health strategy for monitoring infectious agents, infectious
diseases, and transmission dynamics. IGS is gaining increasing importance worldwide. In IGS, data from various sources
are linked. Specifically, the results of modern DNA sequencing methods and genome sequence analysis of pathogen isolates
from patient samples are combined with epidemiological information from the notification system under the German Infection
Protection Act (IfSG) and other pathogen-related data.

The IGS service offers functionality for sequencing laboratories to upload IGS notifications with genome sequence data
attached. The genome sequence data is validated and can be downloaded by RKI after all checks passed.

### Quality Gate

[![Quality Gate Status](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aintegrierte-genomische-surveillance-service&metric=alert_status&token=sqb_49db8f8714e22d29d8cab13b203d531dadf8dfb9)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aintegrierte-genomische-surveillance-service)
[![Vulnerabilities](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aintegrierte-genomische-surveillance-service&metric=vulnerabilities&token=sqb_49db8f8714e22d29d8cab13b203d531dadf8dfb9)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aintegrierte-genomische-surveillance-service)
[![Bugs](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aintegrierte-genomische-surveillance-service&metric=bugs&token=sqb_49db8f8714e22d29d8cab13b203d531dadf8dfb9)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aintegrierte-genomische-surveillance-service)
[![Code Smells](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aintegrierte-genomische-surveillance-service&metric=code_smells&token=sqb_49db8f8714e22d29d8cab13b203d531dadf8dfb9)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aintegrierte-genomische-surveillance-service)
[![Lines of Code](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aintegrierte-genomische-surveillance-service&metric=ncloc&token=sqb_49db8f8714e22d29d8cab13b203d531dadf8dfb9)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aintegrierte-genomische-surveillance-service)
[![Coverage](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aintegrierte-genomische-surveillance-service&metric=coverage&token=sqb_49db8f8714e22d29d8cab13b203d531dadf8dfb9)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aintegrierte-genomische-surveillance-service)

[![Quality gate](https://sonar.prod.ccs.gematik.solutions/api/project_badges/quality_gate?project=de.gematik.demis%3Aintegrierte-genomische-surveillance-service&token=sqb_49db8f8714e22d29d8cab13b203d531dadf8dfb9)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aintegrierte-genomische-surveillance-service)

### Release Notes

See [ReleaseNotes](ReleaseNotes.md) for all information regarding the (newest) releases.

## Getting Started

The application can be executed from a mvn command file or a Docker Image.

```sh
mvn clean verify
```

The docker image can be built with the following command:

```docker
docker build -t igs-service:latest .
```

The image can alternatively also be built with maven:

```sh
mvn -e clean install -Pdocker
```

## Usage

The application can be started as Docker container with the following commands:

```shell
docker run --rm --name igs-service -p 8080:8080 igs-service:latest
```

### Endpoints

Subsequently we list the endpoints provided by the IGS-Service.

| HTTP Method | Endpoint                                                                                           | Parameters                        | Body                                                                                                                             | Returns                                                                                                                                                       | Description                                                                                                                                                 |
|-------------|----------------------------------------------------------------------------------------------------|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| POST        | /fhir/DocumentReference                                                                            | -                                 | Contains the SHA256 hash that is later used for validating the sequence data                                                     | - The location of the document reference as HTTP header<br>- The DocumentReference containing the documentID                                                  |                                                                                                                                                             |
| POST        | /S3Controller/upload/{documentId}/$finish-upload                                                   | - documentID                      | - The uploadID<br>- Part number and corresponding ETag                                                                           | Finalizes the upload of the sequence data to the S3 storage                                                                                                   |                                                                                                                                                             |
| POST        | /S3Controller/upload/{documentId}/$validate                                                        | - documentID                      |                                                                                                                                  | Starts the validation process of the sequence data                                                                                                            |                                                                                                                                                             |
| GET         | /S3Controller/upload/{documentId}/$validation-status                                               | - documentID                      |                                                                                                                                  | - the current status of the validation<br>- an error message in case of a failed validation<br>- the information whether the validation process has completed | Polls for the validation status. With this endpoint a long polling mechanism has been implemented, the request can take up to 15 seconds before it returns. |
| GET         | /fhir/DocumentReference/{documentId}/$binary-access-read?path=DocumentReference.content.attachment | - documentID                      |                                                                                                                                  | The uploaded sequence data                                                                                                                                    |                                                                                                                                                             |
| GET         | /S3Controller/upload/{documentId}/s3-upload-info?fileSize={fileSizeInBytes}                        | - documentID<br>- fileSizeInBytes |                                                                                                                                  | - uploadID<br>- presigned URLs<br>- part size in bytes                                                                                                        | Determines information which the client needs in order to upload sequence data to the S3 storage                                                            |
| PUT         | {presignedUrl}                                                                                     | -                                 | A chunk of the sequence data. The size of the chunk is determined by parameter partSizeInBytes in the response to s3-upload-info | ETag as HTTP header                                                                                                                                           | Upload each chunk of the sequence data to its corresponding presigned URL                                                                                   |
| POST        | /fhir/$process-notification-sequence                                                               | -                                 | The IGS notification                                                                                                             |                                                                                                                                                               | Used for sending the IGS notification.                                                                                                                      |

## Extended Validation for fastA

Different pathogens could be configured by setting environment variables.

e.g.

```
FASTA_VALIDATION_PATHOGEN_DUMY="1000,100,0.1"
```

This example would configure the pathogen "DUMY" with the following parameters:

- maxSequenceLength: 1000
- minSequenceLength: 100
- maxNucleotideAmbiguity: 10%

## Configuration of Retention Periods

When the default retention period for notifications in DEMIS is changed it also has to be changed in the IGS service. There are two
parameters concerning retention policies:

- simple.storage.service.validated-bucket.deletion-deadline-in-days: This parameter configures the retention period of successfully
  validated sequence data in days. This parameter has to be changed when the default retention period of notifications in DEMIS is changed.
- simple.storage.service.upload-bucket.deletion-deadline-in-days: This parameter is used to configure how many days uploaded sequence data
  and its associated metadata is stored allowing the client to complete the upload process. It is used for organizational purposes and to
  implement security features. This parameter is not related to retention periods of notification data.

## Security Policy

If you want to see the security policy, please check our [SECURITY.md](.github/SECURITY.md).

## Contributing

If you want to contribute, please check our [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License

Copyright 2024-2025 gematik GmbH

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See the [LICENSE](./LICENSE.md) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

## Contact

E-Mail to [DEMIS Entwicklung](mailto:demis-entwicklung@gematik.de?subject=[GitHub]%20IGS-Service)
