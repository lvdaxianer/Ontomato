# Proposal

## Why

Metric-view Python programs currently lose their top-level JSON shape before the
HTTP response is built: `answer` is extracted and a one-item array is converted
to an object. This prevents metric-view authors from returning arbitrary JSON
payloads consistently and makes the API contract differ from the value emitted
by `function.code`.

The Ontology Manager is also receiving a related code-editor and server-side
formatting update that must be shipped with the data-engine change.

## What Changes

- Change metric-view execution to use the `answer` value when the parsed
  function output has a top-level `answer` property.
- Preserve the selected value's JSON type and array cardinality without
  converting one-item arrays into objects.
- When the parsed function output has no `answer` property, expose the whole
  parsed value as `data`.
- Keep the existing `success` envelope and completeness metadata behavior.
- Retain the Ontology Manager CodeMirror editor and Python formatting endpoint
  changes in the deployable change set.
- Add focused backend, frontend, and API-contract documentation coverage.

## Capabilities

### New Capabilities

- `ontology-manager-code-editor`: Provides the Ontology Manager's formatted
  Python code editor and its data-engine formatting request.

### Modified Capabilities

- `metric-view-execute-response`: Changes metric-view response extraction from
  cardinality-based normalization to answer-aware, type-preserving passthrough.

## Impact

- `packages/data-engine-core` Python result parsing and metric-view execution.
- `packages/ontology-manager` editor, client API, translations, and tests.
- New `/python/format` data-engine endpoint and its module documentation.
- Docker image builds for `datarag` and `ontology-manager`.
- Existing action/dashboard callers must retain their current result contract.
