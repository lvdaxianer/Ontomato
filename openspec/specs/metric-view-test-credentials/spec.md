# metric-view-test-credentials Specification

## Purpose
Allow the Ontology Manager to execute a metric-view test that depends on the
request-scoped `REQUEST_TOKEN` without persisting the trusted token.

## Requirements

### Requirement: Test dialog accepts a temporary trusted token

The metric-view test dialog MUST provide a masked trusted-token input and MUST
require a non-blank value before starting a test execution.

#### Scenario: Missing token is rejected locally

- **WHEN** the user starts a metric-view test with an empty or whitespace-only
  trusted token
- **THEN** the Manager shows a validation message and does not send a test
  request

#### Scenario: Token is sent as request context

- **WHEN** the user starts a test with a non-blank trusted token
- **THEN** the request includes `X-Trusted-Token` with that value
- **AND** the JSON body contains no trusted-token field

### Requirement: Test credentials are not persisted

The Manager MUST keep the trusted token only in transient component state and
MUST clear it when the test dialog closes or the test request finishes.

#### Scenario: Dialog closes

- **WHEN** the user closes the metric-view test dialog
- **THEN** the trusted-token input is cleared before a later dialog opening

#### Scenario: Request completes

- **WHEN** a metric-view test request succeeds or fails
- **THEN** the trusted token is cleared from the test component state
- **AND** the token is not written to browser storage or logs
