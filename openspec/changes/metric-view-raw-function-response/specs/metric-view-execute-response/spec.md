# Metric View Execute Response

## REMOVED Requirements

### Requirement: Single metric result is returned as an object

The metric-view endpoints SHALL no longer convert a one-item result array into
an object.

### Requirement: Non-single metric results preserve their collection shape

The previous cardinality-based collection-shape rule is replaced by the
answer-aware passthrough requirement below.

### Requirement: Test execution preserves completeness metadata with normalized results

The test-execution endpoint no longer uses cardinality normalization for its
`data` value; completeness metadata remains covered by the new stable-envelope
requirement.

## ADDED Requirements

### Requirement: Function output is exposed by answer-aware passthrough

The metric view execution endpoints `POST /metricView/execute` and
`POST /metricView/testExecute` SHALL parse the JSON value emitted by
`function.code`. If the top-level parsed object contains an `answer` property,
the endpoints SHALL place that property's value directly in `data`; otherwise
they SHALL place the complete parsed value directly in `data`.

#### Scenario: Answer array remains an array

- **WHEN** `function.code` emits `{"answer":[{"id":"x"}]}`
- **THEN** the response SHALL contain `success: true`
- **AND** `data` SHALL be `[ {"id":"x"} ]`
- **AND** the one-item array SHALL NOT be converted into an object

#### Scenario: Answer object remains an object

- **WHEN** `function.code` emits `{"answer":{"id":"x"}}`
- **THEN** `data` SHALL be `{ "id": "x" }`

#### Scenario: No answer property is passed through

- **WHEN** `function.code` emits `{ "id": "x", "name": "example" }`
- **THEN** `data` SHALL be the complete emitted object
- **AND** no `answer` property SHALL be invented or required

#### Scenario: Non-object JSON is passed through

- **WHEN** `function.code` emits a JSON array or scalar without a top-level
  `answer` property
- **THEN** `data` SHALL preserve that parsed JSON value and type

### Requirement: Execution metadata and errors remain stable

Successful metric-view responses SHALL retain `success: true` and any
`PythonExecuteResult` completeness metadata. Invalid or failed execution SHALL
retain the existing `success: false` and `message` error envelope.

#### Scenario: Partial query metadata

- **WHEN** a successful metric-view execution carries partial-query metadata
- **THEN** the response SHALL include that metadata alongside the direct `data`
  value

#### Scenario: Function output cannot be parsed

- **WHEN** the Python output is not valid JSON or violates the execution
  protocol
- **THEN** the endpoint SHALL return `success: false` with a diagnostic
  `message` and SHALL NOT return a fabricated `data` value
