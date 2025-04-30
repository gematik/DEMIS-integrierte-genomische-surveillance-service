# S3ControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**determineUploadInfo**](S3ControllerApi.md#determineUploadInfo) | **GET** /S3Controller/upload/{documentId}/s3-upload-info |  |
| [**finishUpload**](S3ControllerApi.md#finishUpload) | **POST** /S3Controller/upload/{documentId}/$finish-upload |  |
| [**initiateValidation**](S3ControllerApi.md#initiateValidation) | **POST** /S3Controller/upload/{documentId}/$validate |  |
| [**validationStatus**](S3ControllerApi.md#validationStatus) | **GET** /S3Controller/upload/{documentId}/$validation-status |  |


<a name="determineUploadInfo"></a>
# **determineUploadInfo**
> S3Info determineUploadInfo(documentId, fileSize)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **documentId** | **String**|  | [default to null] |
| **fileSize** | **Double**|  | [default to null] |

### Return type

[**S3Info**](../Models/S3Info.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*

<a name="finishUpload"></a>
# **finishUpload**
> finishUpload(documentId, MultipartUploadComplete)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **documentId** | **String**|  | [default to null] |
| **MultipartUploadComplete** | [**MultipartUploadComplete**](../Models/MultipartUploadComplete.md)|  | |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="initiateValidation"></a>
# **initiateValidation**
> initiateValidation(documentId, Authorization)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **documentId** | **String**|  | [default to null] |
| **Authorization** | **String**|  | [default to null] |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="validationStatus"></a>
# **validationStatus**
> ValidationInfo validationStatus(documentId)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **documentId** | **String**|  | [default to null] |

### Return type

[**ValidationInfo**](../Models/ValidationInfo.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*

