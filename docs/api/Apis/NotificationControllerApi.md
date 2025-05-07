# NotificationControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**saveNotificationBundle**](NotificationControllerApi.md#saveNotificationBundle) | **POST** /fhir/$process-notification-sequence |  |


<a name="saveNotificationBundle"></a>
# **saveNotificationBundle**
> String saveNotificationBundle(Content-Type, body, Accept, Authorization)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **Content-Type** | [**MediaType**](../Models/.md)|  | [default to null] |
| **body** | **String**|  | |
| **Accept** | [**MediaType**](../Models/.md)|  | [optional] [default to null] |
| **Authorization** | **String**|  | [optional] [default to null] |

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/fhir+json, application/json, application/json+fhir, application/xml
- **Accept**: application/fhir+json, application/json, application/json+fhir, application/xml

