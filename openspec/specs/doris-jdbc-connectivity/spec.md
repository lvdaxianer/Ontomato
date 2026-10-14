# doris-jdbc-connectivity Specification

## Purpose
Define how the Doris adapter identifies its MySQL-compatible FE connection and
verifies live, read-only metadata and Ontology DSL queries.
## Requirements
### Requirement: Doris connection reports adapter and wire identities separately

The Doris connection test MUST report `databaseProduct` as `Doris` and MUST
retain the JDBC compatibility product in `jdbcDatabaseProduct` when the FE is
accessed through the MySQL protocol.

#### Scenario: MySQL-compatible FE is identified as Doris

- **WHEN** a Doris JDBC connection returns `MySQL` as its JDBC database product
- **THEN** the connection report contains `databaseProduct` equal to `Doris`
- **AND** the report contains `jdbcDatabaseProduct` equal to `MySQL`

### Requirement: Real FE verification is read-only and live

The opt-in real-FE verification MUST discover metadata visible to the supplied
user and execute a bounded Ontology DSL SELECT against the FE without writing
data or relying on copied business records.

#### Scenario: Select-only credentials query Doris successfully

- **WHEN** the test is configured with a Doris FE URL and credentials that can
  read metadata and one visible table or view
- **THEN** metadata discovery returns databases, schemas, tables or views, and
  columns visible to that user
- **AND** the DSL execution returns an answer envelope from a live SELECT
- **AND** the test issues no INSERT, UPDATE, DELETE, DDL, replication, or CDC
  operation
