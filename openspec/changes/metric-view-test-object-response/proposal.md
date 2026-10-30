# Proposal

## Why

The Ontology Manager's JSON test action calls `POST /metricView/testExecute`, but that endpoint still wraps a single metric result in an array. The normal execution endpoint already returns a single result object, so the two user-visible paths produce inconsistent JSON shapes for the same metric view.

## What Changes

- Normalize `POST /metricView/testExecute` using the same response cardinality rules as `POST /metricView/execute`.
- Return the sole result object directly under `data`.
- Preserve all multiple results as an array.
- Omit `data` when there are no results.
- Replace the regression expectation that `testExecute` retains a one-item array.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `metric-view-execute-response`: Apply the single-object, multi-array, and empty-omitted response contract to both metric view execution endpoints.

## Impact

The affected code is the data engine's `MetricViewController` and its focused controller test. The public response contract changes for one-result and empty `POST /metricView/testExecute` calls; the service interface, authorization, persistence, database, and frontend request code remain unchanged.
