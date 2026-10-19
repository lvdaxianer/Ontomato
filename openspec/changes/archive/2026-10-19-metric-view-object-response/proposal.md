# Proposal

## Why

The metric view execution endpoint currently wraps a single result object in a JSON array. Consumers of metric view results expect the object itself under `data`, so the extra array layer makes the endpoint contract inconsistent with the returned metric payload.

## What Changes

- Change only `POST /metricView/execute` so a single result object is returned directly as `data`.
- Preserve an empty or multi-item result as an array so the endpoint remains safe for result shapes that are not a single object.
- Leave `POST /metricView/testExecute`, the service return type, and all other endpoints unchanged.
- Add a focused controller regression test for the response shape.

## Capabilities

### New Capabilities

- `metric-view-execute-response`: Defines the response shape of metric view execution results.

### Modified Capabilities

- None.

## Impact

The affected API response is `POST /metricView/execute` in the data engine controller. Downstream callers receiving a one-item result will see `data` as an object instead of a one-element array; empty and multi-item results retain their existing array shape. No database, service, deployment, or remote repository changes are required.
