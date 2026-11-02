# Python Format REST API

## 1. Module overview and coverage

| Item | Content |
| --- | --- |
| Business module | Python source formatting (`python-format`) |
| Document path | `apis/python-format.md` |
| Module responsibility | Format Python source for the Ontology Manager editor without executing or persisting it. |
| Base URL / version | `<DATA_ENGINE_BASE_URL>/api/data-query`; no explicit version prefix is defined by the controller. |
| Common conventions | The controller returns HTTP-level JSON with `success: true` or `success: false`; the request language is supplied through `Accept-Language`. |

| Controller / route ownership | Public endpoint | Documentation section | Coverage |
| --- | --- | --- | --- |
| `PythonFormatController` | `POST /python/format` | [2. Format Python source](#2-format-python-source) | Covered |

## 2. Format Python source

### 2.1 Endpoint overview

| Item | Content |
| --- | --- |
| Endpoint name | Format Python source |
| Purpose | Run the configured Black formatter on source submitted by the caller. |
| Side effects | None; source is not saved and is not executed. |
| Preconditions | A current host request context and a Python runtime with Black installed. |
| Failure behavior | Syntax errors, formatter unavailability, and formatter failures return `success: false`. |

### 2.2 Request definition

```http
POST <DATA_ENGINE_BASE_URL>/api/data-query/python/format
```

| Header | Required | Example | Description |
| --- | --- | --- | --- |
| `Accept-Language` | Yes | `zh-CN` | Selects localized formatter error messages. |
| `Content-Type` | Yes | `application/json` | Identifies the JSON request body. |

```json
{
  "code": "def f( a,b ):\n  return a+b\n"
}
```

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | string | No | Python source to format. A missing value is treated as an empty string by the controller. |

### 2.3 Response definition

Successful response:

```json
{
  "success": true,
  "data": {
    "code": "def f(a, b):\n    return a + b\n"
  }
}
```

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `success` | boolean | Yes | `true` when formatting succeeds. |
| `data.code` | string | Yes on success | Formatted Python source. |

Failure response:

```json
{
  "success": false,
  "message": "formatter error"
}
```

The endpoint uses the configured formatter with a bounded execution timeout.
The Ontology Manager keeps the original editor content when the request fails.
