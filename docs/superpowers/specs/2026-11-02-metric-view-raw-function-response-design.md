# Metric View Raw Function Response Design

## Decision

Metric-view execution will expose the parsed JSON emitted by `function.code`
without cardinality normalization. A top-level `answer` property is an
execution protocol wrapper: its value becomes `data`; when the wrapper is
absent, the complete parsed JSON value becomes `data`.

## Compatibility boundary

The raw path is limited to metric-view execution. Existing action, dashboard,
and write-task callers retain their current row-array parser and validation so
this response-contract change does not alter unrelated APIs.

## Acceptance examples

```json
{"answer":[{"id":"x"}]}
```

becomes:

```json
{"success":true,"data":[{"id":"x"}]}
```

while:

```json
{"id":"x"}
```

becomes:

```json
{"success":true,"data":{"id":"x"}}
```
