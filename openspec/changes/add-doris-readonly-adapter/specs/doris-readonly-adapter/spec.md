## ADDED Requirements

### Requirement: Doris provider and connection

The data-engine assembly SHALL register a provider whose type is `doris`, whose label is `Doris`, whose `sql()` value is `true`, and whose connection fields include `url`, `user`, and `password`. The provider SHALL show a Doris FE MySQL-protocol JDBC example using port 9030.

#### Scenario: Provider is listed

- **WHEN** the assembled application asks the `DataAdapterRegistry` for installed providers
- **THEN** it includes exactly one Doris provider with type `doris` and label `Doris`
- **AND** the provider advertises SQL support and the three required connection fields

#### Scenario: Doris FE connection test succeeds

- **WHEN** a user supplies a valid Doris FE URL, user, and password
- **THEN** the adapter opens a JDBC connection through the FE MySQL protocol
- **AND** the connection test returns database product, version, driver, and visible metadata without issuing a write

#### Scenario: SELECT-only credentials are sufficient

- **WHEN** the configured Doris user has SELECT permission but no INSERT, UPDATE, DELETE, or DDL permission
- **THEN** connection testing and read metadata discovery succeed
- **AND** no mutation privilege is required

### Requirement: Visible Doris metadata discovery

The Doris adapter SHALL expose the connected user's visible databases/catalogs, schemas, tables, views, and columns through its connection metadata report. It SHALL distinguish tables from views and SHALL not report objects that the connected user cannot see.

#### Scenario: Discover tables, views, and columns

- **WHEN** the connection test runs against a Doris FE containing a visible table and view
- **THEN** the report includes their database/catalog and schema context, normalized object type, and column names/types

#### Scenario: Hidden objects are excluded

- **WHEN** the Doris user lacks visibility for a database, table, view, or column
- **THEN** that object is absent from the metadata report
- **AND** the adapter does not attempt to elevate privileges

### Requirement: Live Ontology DSL reads

The Doris adapter SHALL translate the existing Ontology DSL into read-only Doris SQL and execute it against Doris at query time. It SHALL support the existing SQL-adapter query shapes for single-table queries, multi-table joins, condition filters, aggregates, ordering, and pagination.

#### Scenario: Query one mapped table

- **WHEN** an ontology class is mapped to a Doris table and a DSL query requests rows from that class
- **THEN** the adapter emits a Doris SELECT statement for the mapped table and returns the current Doris rows

#### Scenario: Join mapped tables

- **WHEN** the DSL describes relationships between two or more mapped ontology classes
- **THEN** the adapter emits a Doris JOIN query using the mapped identifiers and returns the joined result

#### Scenario: Filter, aggregate, order, and page

- **WHEN** a DSL query contains supported predicates, aggregate expressions, sort terms, and an offset/limit
- **THEN** the generated Doris SQL preserves those semantics and returns the ordered page
- **AND** predicate values are bound or safely encoded so user values cannot become SQL syntax

#### Scenario: New source rows are visible

- **WHEN** a new committed row is added to a mapped Doris table after one ontology query completes
- **THEN** the next ontology query reads Doris again and can return the new row without import, cache invalidation, or synchronization

### Requirement: Explicit read-only behavior

The Doris adapter SHALL reject writes and unsupported stored-data operations with `UnsupportedDataAdapterOperationException`. It SHALL identify the Doris adapter and operation in the error, and SHALL never report an unsupported mutation as a successful no-op.

#### Scenario: Object mutation is rejected

- **WHEN** a caller invokes insert, update, or delete on the Doris adapter
- **THEN** the adapter throws an unsupported-operation error naming the operation
- **AND** it does not issue a mutation statement to Doris

#### Scenario: Schema and edge mutation are rejected

- **WHEN** a caller invokes namespace, class, edge-type, edge-create, or edge-delete mutation
- **THEN** the adapter throws an unsupported-operation error

#### Scenario: Unsupported vector/object retrieval is rejected

- **WHEN** a caller requests a Doris operation that requires an Ontomato-managed copied object or vector store
- **THEN** the adapter throws an unsupported-operation error instead of returning fabricated or stale data

### Requirement: Build and test assembly

The Doris adapter module SHALL be included in the root Maven reactor, the data-engine application dependencies and runtime assembly, and the image payload configuration. Tests SHALL cover provider registration, read-only policy, SQL rendering, and opt-in real-Doris compatibility without committing credentials or runtime data.

#### Scenario: Application assembly contains Doris

- **WHEN** the data-engine application is built with its normal Maven and image assembly entry points
- **THEN** the Doris adapter classes and JDBC dependency are present in the runtime payload
- **AND** the provider can be discovered by the application

#### Scenario: External compatibility test is controlled

- **WHEN** the Doris integration-test environment variables are absent
- **THEN** the external test is skipped with an explicit reason
- **AND** unit tests remain runnable without a Doris server

