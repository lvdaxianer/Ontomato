## Context

Ontomato currently discovers SQL adapters through Spring providers and executes Ontology DSL through the `DataAdapter` contract. The existing MySQL adapter combines MySQL-specific DSL rendering, JDBC execution, and write operations. Doris exposes a MySQL-compatible FE protocol, but the requested behavior is deliberately read-only and must be proven against Doris rather than inherited from MySQL by assumption.

The change crosses the data-engine core, a new adapter module, application assembly, image packaging, and adapter tests. Ontomato must retain ontology definitions and mappings only; each ontology query must read current business rows from Doris.

## Goals / Non-Goals

**Goals:**

- Register a `doris` provider with URL, user, password, and a Doris FE JDBC example.
- Open and test a Doris FE connection through JDBC and report the visible database/catalog, schemas, tables, views, and columns without requiring write privileges.
- Translate the existing Ontology DSL into safe, read-only Doris SQL for the query shapes already supported by the SQL adapters: single-table reads, joins, filters, aggregates, ordering, and pagination.
- Execute every query against Doris at request time so newly committed Doris rows are visible on the next ontology query.
- Make unsupported writes and unsupported non-read paths fail explicitly with `UnsupportedDataAdapterOperationException`.
- Include the adapter in the Maven reactor, data-engine application assembly, and image build payload.
- Add deterministic unit tests plus an opt-in integration test controlled by test-only environment variables.

**Non-Goals:**

- No insert, update, delete, DDL, edge mutation, data copy, CDC, import, cache, or scheduled synchronization.
- No private enterprise module dependencies and no change to the ontology persistence model.
- No assumption that every MySQL expression, JDBC metadata method, or driver behavior is Doris-compatible; each used behavior is either covered by a focused test or verified by the real-Doris test.

## Decisions

### 1. Add an independent `packages/data-adapters/doris` module

The module will implement `DataAdapter` and `DataAdapterProvider` directly and reuse the repository's existing MySQL JDBC driver dependency because Doris FE speaks the MySQL wire protocol. The adapter remains a separate module so Doris-specific SQL, metadata normalization, and read-only policy cannot accidentally inherit MySQL write behavior.

Alternatives considered:

- Reusing `MysqlAdapter` by changing its provider type: rejected because it would expose writes and silently couple Doris behavior to MySQL-only SQL rendering.
- Adding a Doris-specific JDBC driver: deferred unless the real FE compatibility test proves the MySQL driver insufficient; the documented FE protocol makes the existing driver the smallest testable starting point.

### 2. Keep a dedicated read-only SQL compiler and JDBC execution path

The Doris adapter will parse the existing DSL shape and build SQL from validated ontology class/attribute definitions. Identifiers will be quoted with Doris-compatible backticks after validation against the supplied ontology mapping; values will be bound through `PreparedStatement` parameters. The compiler will have explicit allowlists for operators and supported aggregate/order forms, and will reject malformed or unsupported DSL instead of concatenating arbitrary SQL.

The compiler will emit Doris pagination syntax only after a real-FE compatibility test confirms the chosen form. MySQL-compatible functions such as `FROM_UNIXTIME` are treated the same way: a unit test proves rendering, while the opt-in Doris test proves execution.

Alternatives considered:

- Copying the MySQL `toSql` implementation: rejected because it concatenates values, contains write-oriented assumptions, and would make compatibility failures difficult to isolate.
- Introducing a broad cross-database SQL abstraction: rejected for this first read-only adapter because it expands the blast radius beyond the requested Doris capability.

### 3. Separate connection validation from Doris metadata discovery

The adapter will retain the common connection-test timeout and failure formatting, then add a Doris-specific metadata report assembled from JDBC metadata and read-only `information_schema` queries where the driver metadata is incomplete. The report will distinguish catalogs/databases, schemas, tables, views, and columns, and will only include objects visible to the connected user. Queries will not require `CREATE`, `INSERT`, or any other mutation privilege.

This keeps existing adapters' connection-report contract stable while allowing Doris to expose its richer source shape. Metadata discovery will use bounded result processing and the same connection lifecycle as the test probe.

Alternatives considered:

- Change the common tester to issue database-specific SQL for every adapter: rejected because it would create cross-adapter regressions and make the core tester know Doris details.
- Treat the selected JDBC catalog as the only database: rejected because Doris users can have visibility across databases and the requirement explicitly includes database/schema discovery.

### 4. Make unsupported operations explicit at the adapter boundary

All mutation methods, edge mutations, sample-data writes, and vector/object paths that require a stored Ontomato copy will throw `UnsupportedDataAdapterOperationException` with the `doris` type and operation name. `query` will delegate only to the existing live DSL read path where its contract permits; unsupported vector retrieval will fail explicitly. No method will return an empty success result for an unsupported operation.

### 5. Verify compatibility with two test layers

The module will contain unit tests for provider metadata, SQL rendering, identifier/value safety, pagination, and unsupported operations. A real-Doris integration test will run only when `ONTOMATO_TEST_DORIS_URL`, `ONTOMATO_TEST_DORIS_USER`, and `ONTOMATO_TEST_DORIS_PASSWORD` are all present. It will use a read-only account, exercise metadata and representative queries, and verify that a newly inserted fixture row is visible on the next read when the fixture setup is provided by the test environment. Missing variables skip the external test; they do not fabricate compatibility evidence.

The test will never log passwords or commit connection values. Java database/external tests will be invoked by their module/test selector rather than as an unfiltered external-database suite.

### 6. Wire assembly explicitly

The root Maven reactor, `apps/data-engine/pom.xml`, `apps/data-engine/build.mjs`, and the relevant image/payload configuration will list the Doris module and artifact. Registry visibility will therefore follow the existing provider scanning mechanism and will not require a second hard-coded provider registry.

## Risks / Trade-offs

- [Doris/MySQL syntax or driver differences] → Keep the compiler independent, execute representative SQL against a real FE when credentials are supplied, and document any version-specific limitation rather than claiming compatibility from a unit test.
- [JDBC metadata differs across Doris versions] → Prefer visible `information_schema` rows for Doris-specific discovery, normalize table/view types, and cover at least one real FE metadata response.
- [DSL contains more shapes than the current SQL adapters handle] → Preserve existing supported semantics, reject unsupported shapes with a stable error, and add tests for each advertised query shape.
- [External integration is unavailable in local CI] → Make the real test opt-in and report the missing environment clearly; local unit/assembly verification remains deterministic.
- [Sensitive connection data leaks through diagnostics] → Do not log URLs containing credentials or parameter values; preserve only sanitized exception messages and existing failure formatting.
- [Adding the module changes edition assembly behavior] → Add focused assembly tests and inspect image payload lists before modifying them; do not alter unrelated edition policy failures.

## Migration Plan

1. Add the module, provider, compiler, read-only policy, metadata report, and focused tests.
2. Add reactor/application/image assembly entries and run module-scoped unit tests plus the relevant assembly checks.
3. When a Doris FE test environment is available, run the opt-in integration selector with a SELECT-only user and record the observed compatibility.
4. Deploy the application with a Doris connection configured through the existing business-config path; existing ontology mappings remain unchanged.
5. Roll back by removing the Doris module and assembly entries. No database migration or business-data cleanup is required because the adapter stores no Doris rows.

## Open Questions

- Which Doris version(s) must be supported in the first release? The implementation will target the FE MySQL protocol and record the version returned by the integration test.
- Does the existing ontology DSL contract expose all required column types and aggregate forms for Doris, or should unsupported type/function combinations be rejected until separately specified?
- Should the common connection-test JSON be extended later into a shared metadata schema, or should richer metadata remain an adapter-specific extension until another adapter needs it?
