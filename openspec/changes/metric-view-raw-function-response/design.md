# Design

## Response data flow

The Python process continues to emit one JSON value on stdout. The shared
function executor parses that value once for metric-view calls:

1. If the parsed top-level object contains `answer`, use its value.
2. Otherwise use the parsed top-level value itself.
3. Put that selected value directly in the controller's `data` field.

The selected value is never cardinality-normalized. An `answer` array with one
item remains an array, and an object without `answer` remains an object. The
existing `success` field and `PythonExecuteResult` completeness fields remain in
the response envelope.

The existing parser used by action, dashboard, and write-task flows remains
unchanged. Metric-view execution uses a separate raw-result path so the change
does not silently alter unrelated callers that currently rely on an array of
rows and `outKeyRefs` validation.

## Code editor flow

The Ontology Manager keeps the CodeMirror editor as the editing surface. The
formatter callback sends the current Python source to `POST /python/format` and
replaces editor content only after a successful response. Formatting failures
show a localized error and leave the existing source untouched.

The formatting endpoint validates that code is present, runs the configured
Black formatter through the backend service, and returns the formatted source
or a safe error response. It does not persist code or execute user programs.

## Error handling and compatibility

- Malformed or non-JSON Python output remains an execution error with the
  existing controller error envelope.
- A valid JSON scalar, object, or array without `answer` is a valid direct data
  result.
- Existing action/dashboard/write-task parsing and response behavior is outside
  the modified metric-view contract.
- The endpoint documentation records the new response examples and formatting
  endpoint separately.

## Verification

- Add RED/GREEN tests for answer extraction, no-answer passthrough, one-item
  array preservation, multi-item array preservation, and empty arrays.
- Run the existing focused Java controller/service tests and the new formatter
  tests.
- Run the Ontology Manager unit/component tests and production build.
- Run the data-engine package/build checks and `git diff --check`.
