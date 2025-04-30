# Documentation for name

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

| Class | Method | HTTP request | Description |
|------------ | ------------- | ------------- | -------------|
| *DocumentReferenceControllerApi* | [**generateDocumentReference**](Apis/DocumentReferenceControllerApi.md#generatedocumentreference) | **POST** /fhir/DocumentReference |  |
*DocumentReferenceControllerApi* | [**getBinary**](Apis/DocumentReferenceControllerApi.md#getbinary) | **GET** /fhir/DocumentReference/{documentId}/$binary-access-read |  |
| *NotificationControllerApi* | [**saveNotificationBundle**](Apis/NotificationControllerApi.md#savenotificationbundle) | **POST** /fhir/$process-notification-sequence |  |
| *S3ControllerApi* | [**determineUploadInfo**](Apis/S3ControllerApi.md#determineuploadinfo) | **GET** /S3Controller/upload/{documentId}/s3-upload-info |  |
*S3ControllerApi* | [**finishUpload**](Apis/S3ControllerApi.md#finishupload) | **POST** /S3Controller/upload/{documentId}/$finish-upload |  |
*S3ControllerApi* | [**initiateValidation**](Apis/S3ControllerApi.md#initiatevalidation) | **POST** /S3Controller/upload/{documentId}/$validate |  |
*S3ControllerApi* | [**validationStatus**](Apis/S3ControllerApi.md#validationstatus) | **GET** /S3Controller/upload/{documentId}/$validation-status |  |


<a name="documentation-for-models"></a>
## Documentation for Models

 - [CompletedChunk](./Models/CompletedChunk.md)
 - [MediaType](./Models/MediaType.md)
 - [MultipartUploadComplete](./Models/MultipartUploadComplete.md)
 - [S3Info](./Models/S3Info.md)
 - [ValidationInfo](./Models/ValidationInfo.md)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
