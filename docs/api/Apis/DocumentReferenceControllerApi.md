# DocumentReferenceControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**generateDocumentReference**](DocumentReferenceControllerApi.md#generateDocumentReference) | **POST** /fhir/DocumentReference |  |
| [**getBinary**](DocumentReferenceControllerApi.md#getBinary) | **GET** /fhir/DocumentReference/{documentId}/$binary-access-read |  |


<a name="generateDocumentReference"></a>
# **generateDocumentReference**
> String generateDocumentReference(Content-Type, body)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **Content-Type** | [**MediaType**](../Models/.md)|  | [default to null] |
| **body** | **String**|  | |

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/json, application/xml, application/json+fhir, application/fhir+json
- **Accept**: application/json, application/xml, application/json+fhir, application/fhir+json

<a name="getBinary"></a>
# **getBinary**
> File getBinary(documentId, path)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **documentId** | **String**|  | [default to null] |
| **path** | **String**|  | [default to null] |

### Return type

**File**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/octet-stream

