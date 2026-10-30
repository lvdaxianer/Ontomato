# Metric View REST APIs

## 1. Module overview and coverage

| Item | Content |
| --- | --- |
| Business module | Metric views (`metric-view`) |
| Document path | `apis/metric-view.md` |
| Module responsibility | Execute published metric views and return their calculated result payloads to the Ontology Manager and API clients. |
| Base URL / version | `<DATA_ENGINE_BASE_URL>/api/data-query`; no explicit version prefix is defined by the controller. |
| Common authentication and conventions | The controller obtains the current user and data permission from the host authentication context. The exact authentication header/cookie is provided by the host and is **待确认** from this public controller alone. Requests require the language header described below. Successful responses use `success: true`; handled failures use `success: false` and `message`. |

### 1.1 Controller / route coverage

| Controller / route ownership | Public endpoint | Documentation section | Coverage |
| --- | --- | --- | --- |
| `MetricViewController` | `POST /metricView/execute` | [2. Execute a published metric view](#2-execute-a-published-metric-view) | Covered |
| `MetricViewController` | `POST /metricView/testExecute` | [3. Test-execute a metric view definition](#3-test-execute-a-metric-view-definition) | Covered |

## 2. Execute a published metric view

### 2.1 Endpoint overview

| Item | Content |
| --- | --- |
| Endpoint name | Execute metric view |
| Purpose | Execute a metric view identified by its saved ID with runtime parameters. |
| Business scenario | The Ontology Manager or another authenticated client invokes a published metric view and consumes the calculated result. The endpoint is read-only with respect to metric-view definitions and does not define an idempotency key. |
| Preconditions | A current authenticated user is available; the metric view ID resolves to an executable metric view; the domain has a usable DSL timeout configuration. |
| Permission requirement | The service receives the current user's data permission and domain context. The exact role/permission name is **待确认** from this controller. |
| Version | No explicit API version is defined. |

### 2.2 Request definition

#### 2.2.1 Method and URL

```http
POST <DATA_ENGINE_BASE_URL>/api/data-query/metricView/execute
```

| Item | Content |
| --- | --- |
| Method | `POST` |
| URL | `<DATA_ENGINE_BASE_URL>/api/data-query/metricView/execute` |
| Content-Type | `application/json` |
| Accept | `application/json` |

#### 2.2.2 Parameters

##### Path parameters

不适用。

##### Query parameters

不适用。

##### Form or other parameters

不适用。

#### 2.2.3 Request headers

| Header | Required | Type / format | Example | Purpose | Missing or invalid behavior |
| --- | --- | --- | --- | --- | --- |
| `Accept-Language` | Yes | Language tag string | `zh-CN` | Selects the request language used by the data-engine service. | Controller binding failure or host-level handling; exact HTTP status is **待确认**. |
| Host authentication header | Host-defined | **待确认** | **待确认** | Supplies the current user used for authorization and domain resolution. | Host authentication handling; exact header and HTTP status are **待确认**. |
| `Content-Type` | Yes | MIME type | `application/json` | Identifies the JSON request body. | Framework handling; exact HTTP status is **待确认**. |

#### 2.2.4 Request cookies

请求 Cookie：不适用或由宿主认证层处理；该 Controller 未直接读取 Cookie，具体认证 Cookie 由宿主配置决定，**待确认**。

#### 2.2.5 Request body

The top-level body is a JSON object:

```json
{
  "id": "metric-view-id",
  "param": {
    "area": "adcode-150000",
    "networkType": "4G"
  }
}
```

| Field path | Type | Required | Default | Format / range | Example | Description |
| --- | --- | --- | --- | --- | --- | --- |
| `id` | string | Yes | None | Saved metric-view identifier | `metric-view-id` | Metric view to execute. |
| `param` | object | No | `null` or service-defined | Metric-view parameter object | `{ "area": "adcode-150000" }` | Runtime parameter values passed to the metric view. The accepted fields are defined by the selected metric view. |

Unknown fields and field-level validation are handled by the service; exact rejection rules are **待确认**.

### 2.3 Response definition

#### 2.3.1 Response overview

| HTTP status | Meaning | Trigger | Caller handling |
| --- | --- | --- | --- |
| `200 OK` | Request handled | The controller returns its JSON response, including handled business failures. | Inspect `success` before consuming `data`. |
| `401 Unauthorized` | **待确认** | Host authentication rejects the request before or around controller execution. | Re-authenticate according to host rules. |
| `403 Forbidden` | **待确认** | Host or service denies the user's data permission. | Do not retry unchanged credentials. |
| `500 Internal Server Error` | **待确认** | Unhandled framework or infrastructure failure. | Retry only according to the host's policy and include diagnostic context. |

#### 2.3.2 Response headers

| Header | Condition | Example | Purpose |
| --- | --- | --- | --- |
| `Content-Type` | JSON response | `application/json` | Response media type. |

响应 Cookie：不适用或由宿主认证层处理，具体行为 **待确认**。

#### 2.3.3 Success response

For exactly one result, `data` is the result object rather than a one-item array. For multiple results, `data` is an array preserving order. For no results, `data` is omitted. A partial execution may add completeness fields.

```json
{
  "success": true,
  "data": {
    "id": "6283427598497800337"
  }
}
```

| Field path | Type | Required | Nullable | Example | Description |
| --- | --- | --- | --- | --- | --- |
| `success` | boolean | Required | No | `true` | Indicates successful controller handling. |
| `data` | object | Required for one result | No | `{ "id": "6283427598497800337" }` | Sole result object. |
| `data` | array<object> | Required for multiple results | No | `[{ "id": "..." }, { "id": "..." }]` | All result objects in service order. |
| `data` | absent | For no results | N/A | N/A | No result payload. |
| `truncated` | boolean | Optional | No | `true` | Indicates that a query contributing to the result was partial. |
| `rowLimit` | integer | Optional | Yes | `100000` | Row limit associated with a partial result. |
| `truncationHint` | string | Optional | Yes | `...` | Human-readable completeness guidance. |

#### 2.3.4 Error response

Handled controller failures keep HTTP response handling in the controller and use this JSON shape:

```json
{
  "success": false,
  "message": "metric view execution failed"
}
```

| Field path | Type | Required | Example | Description |
| --- | --- | --- | --- | --- |
| `success` | boolean | Required | `false` | Indicates that controller execution failed. |
| `message` | string | Required when failure is handled | `metric view execution failed` | Error message returned by the caught exception. Exact localization and error-code mapping are **待确认**. |

### 2.4 Complete example

#### 2.4.1 curl request

```bash
curl --request POST '<DATA_ENGINE_BASE_URL>/api/data-query/metricView/execute' \
  --header 'Accept-Language: zh-CN' \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --header 'Authorization: Bearer <ACCESS_TOKEN>' \
  --data '{
    "id": "metric-view-id",
    "param": {
      "area": "adcode-150000",
      "networkType": "4G"
    }
  }'
```

#### 2.4.2 Success response

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"success":true,"data":{"id":"6283427598497800337"}}
```

## 3. Test-execute a metric view definition

### 3.1 Endpoint overview

| Item | Content |
| --- | --- |
| Endpoint name | Test-execute metric view |
| Purpose | Execute the metric-view definition and runtime parameters supplied by the caller without requiring the saved-ID request shape. |
| Business scenario | The Ontology Manager's JSON test dialog sends the current metric-view definition and parameter values for an interactive test. |
| Preconditions | A current authenticated user is available; the supplied metric-view definition is executable and published according to service rules. |
| Permission requirement | The service receives the current user's data permission and domain context. The exact role/permission name is **待确认** from this controller. |
| Idempotency | Read-only/test execution; no idempotency key is applicable. |
| Version | No explicit API version is defined. |

### 3.2 Request definition

#### 3.2.1 Method and URL

```http
POST <DATA_ENGINE_BASE_URL>/api/data-query/metricView/testExecute
```

#### 3.2.2 Parameters and headers

Path parameters：不适用。Query parameters：不适用。Form parameters：不适用。Request Cookie：由宿主认证层处理，具体行为 **待确认**。

Request headers are the same as [section 2.2.3](#222-request-headers): `Accept-Language` is required, `Content-Type` is `application/json`, and the host authentication header is **待确认**.

#### 3.2.3 Request body

```json
{
  "metricView": {
    "id": "metric-view-id",
    "status": "PUBLISHED"
  },
  "param": {
    "area": "adcode-150000",
    "networkType": "4G"
  }
}
```

| Field path | Type | Required | Default | Format / range | Example | Description |
| --- | --- | --- | --- | --- | --- | --- |
| `metricView` | object | Yes | None | Metric-view definition accepted by the service | `{ "status": "PUBLISHED" }` | Definition to test-execute. |
| `param` | object | No | `null` or service-defined | Runtime parameter object | `{ "area": "adcode-150000" }` | Values supplied to the definition. |

### 3.3 Response definition

The response envelope and cardinality rules are identical to [section 2.3.3](#233-success-response): one result is an object, multiple results remain an array, and no result omits `data`. The endpoint also preserves the optional completeness fields. Handled errors use the shape in [section 2.3.4](#234-error-response).

### 3.4 Complete example

```bash
curl --request POST '<DATA_ENGINE_BASE_URL>/api/data-query/metricView/testExecute' \
  --header 'Accept-Language: zh-CN' \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --header 'Authorization: Bearer <ACCESS_TOKEN>' \
  --data '{
    "metricView": {"id": "metric-view-id", "status": "PUBLISHED"},
    "param": {"area": "adcode-150000", "networkType": "4G"}
  }'
```

Example one-result response:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"success":true,"data":{"id":"6283427598497800337"}}
```

## 4. Common contract checks

- Both endpoints are read/test operations; pagination, sorting, caching, and idempotency keys are not applicable to the controller contract.
- Callers must branch on `success` and must not assume that `data` is always present or always an array.
- Authentication and exact host-level HTTP error mapping remain **待确认** because they are supplied outside this controller.
