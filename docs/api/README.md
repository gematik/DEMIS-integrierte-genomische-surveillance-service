# Documentation for name

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

| Class | Method | HTTP request | Description |
|------------ | ------------- | ------------- | -------------|
| *DocumentReferenceControllerApi* | [**generateDocumentReference**](Apis/DocumentReferenceControllerApi.md#generateDocumentReference) | **POST** /DocumentReference |  |
*DocumentReferenceControllerApi* | [**getBinary**](Apis/DocumentReferenceControllerApi.md#getBinary) | **GET** /DocumentReference/{documentId}/$binary-access-read |  |
| *NotificationControllerApi* | [**saveNotificationBundle**](Apis/NotificationControllerApi.md#saveNotificationBundle) | **POST** /$process-notification-sequence |  |
| *S3ControllerApi* | [**determineUploadInfo**](Apis/S3ControllerApi.md#determineUploadInfo) | **GET** /S3Controller/upload/{documentId}/s3-upload-info |  |
*S3ControllerApi* | [**finishUpload**](Apis/S3ControllerApi.md#finishUpload) | **POST** /S3Controller/upload/{documentId}/$finish-upload |  |
*S3ControllerApi* | [**initiateValidation**](Apis/S3ControllerApi.md#initiateValidation) | **POST** /S3Controller/upload/{documentId}/$validate |  |
*S3ControllerApi* | [**validationStatus**](Apis/S3ControllerApi.md#validationStatus) | **GET** /S3Controller/upload/{documentId}/$validation-status |  |


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
