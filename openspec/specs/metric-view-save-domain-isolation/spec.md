# Metric-View Save Domain Isolation

## Purpose

Keep metric-view persistence and vector updates scoped to the authenticated
domain even when the nested function payload omits its domain identifier.

## Requirements

### Requirement: Metric-view save propagates the authenticated domain

The metric-view save operation MUST assign the authenticated metric-view domain
to the nested function before persisting either asset or updating its search
vector.

#### Scenario: Nested function omits domain identifier

- **WHEN** an authenticated user saves a metric view whose nested function has
  no `domainId`
- **THEN** the save succeeds and the function is persisted with the metric
  view's authenticated domain

#### Scenario: Existing function is edited within the current domain

- **WHEN** an authenticated user saves an existing metric view with a function
  payload that does not carry the domain field
- **THEN** the update remains associated with the current authenticated domain
  and does not pass a null domain to vector retrieval

#### Scenario: Existing function belongs to another domain

- **WHEN** an authenticated user submits a function identifier owned by a
  different domain
- **THEN** the save is rejected with the existing domain-mismatch validation
  and neither function nor metric-view persistence is attempted

### Requirement: Metric-view save rejects missing authenticated domain safely

The metric-view save operation MUST return a caller-safe validation error when
no authenticated domain is available, rather than throwing a null pointer
exception or writing an unscoped function.

#### Scenario: Domain context is missing

- **WHEN** a save request reaches the service without a domain identifier
- **THEN** the request fails with a stable validation message and no function
  or metric-view row is written
