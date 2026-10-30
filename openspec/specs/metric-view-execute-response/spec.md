# Metric View Execute Response

## Purpose

Provide a stable metric view execution response shape for callers that consume a single metric result object, while retaining array semantics for empty or multi-result responses.

## Requirements

### Requirement: Single metric result is returned as an object

The metric view execution endpoints `POST /metricView/execute` and `POST /metricView/testExecute` SHALL place a sole result object directly in `data`.

#### Scenario: One result object

- **WHEN** `POST /metricView/execute` completes successfully with one result object
- **THEN** the response SHALL contain `success: true` and `data` SHALL be that object rather than a one-element array

#### Scenario: Test execution one result object

- **WHEN** `POST /metricView/testExecute` completes successfully with one result object
- **THEN** the response SHALL contain `success: true` and `data` SHALL be that object rather than a one-element array

### Requirement: Non-single metric results preserve their collection shape

The metric view execution endpoints SHALL preserve multiple results as an array in `data` and omit `data` when execution produces no results.

#### Scenario: Multiple results

- **WHEN** either endpoint completes successfully with multiple results
- **THEN** the response SHALL contain `success: true` and `data` SHALL remain an array containing all results in order

#### Scenario: Empty result

- **WHEN** either endpoint completes successfully with no results
- **THEN** the response SHALL contain `success: true` and SHALL omit the `data` property

### Requirement: Test execution preserves completeness metadata with normalized results

The metric view test execution endpoint SHALL continue applying its `PythonExecuteResult` completeness metadata to successful responses while adopting the same cardinality-based `data` shape as the normal execution endpoint.

#### Scenario: Test execution response

- **WHEN** `POST /metricView/testExecute` completes successfully with one result object
- **THEN** its response SHALL include the completeness metadata produced by `PythonExecuteResult` and place the result object directly in `data`
