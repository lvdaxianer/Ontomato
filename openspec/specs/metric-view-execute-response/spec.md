# Metric View Execute Response

## Purpose

Provide a stable metric view execution response shape for callers that consume a single metric result object, while retaining array semantics for empty or multi-result responses.

## Requirements

### Requirement: Single metric result is returned as an object

The metric view execution endpoint SHALL place the sole result object directly in the `data` property when execution produces exactly one result.

#### Scenario: One result object

- **WHEN** `POST /metricView/execute` completes successfully with one result object
- **THEN** the response SHALL contain `success: true` and `data` SHALL be that object rather than a one-element array

### Requirement: Non-single metric results preserve their collection shape

The metric view execution endpoint SHALL preserve an empty or multi-result execution result as an array in the `data` property.

#### Scenario: Empty result

- **WHEN** `POST /metricView/execute` completes successfully with no results
- **THEN** the response SHALL contain `success: true` and `data` SHALL be an empty array

#### Scenario: Multiple results

- **WHEN** `POST /metricView/execute` completes successfully with multiple results
- **THEN** the response SHALL contain `success: true` and `data` SHALL remain an array containing all results in order

### Requirement: Other metric execution endpoint remains unchanged

The metric view test execution endpoint SHALL continue returning its service result as an array without applying the single-result unwrapping behavior.

#### Scenario: Test execution response

- **WHEN** `POST /metricView/testExecute` completes successfully
- **THEN** its `data` property SHALL retain the array response shape provided by the service
