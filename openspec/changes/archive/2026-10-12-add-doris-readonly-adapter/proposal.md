## Why

Ontomato currently has no first-class Doris data source, so users cannot connect a Doris FE, discover its visible schema, or query Doris-backed ontology classes through the existing DataAdapter contract. Doris is a live business-data source for this change: Ontomato must keep ontology definitions and mappings while reading current rows directly from Doris on every query.

## What Changes

- Add a `doris` DataAdapter provider with JDBC URL, user, and password fields.
- Add a Doris adapter module using the FE MySQL protocol connection, with compatibility verified against a real Doris FE rather than assumed from MySQL behavior.
- Expose visible Doris catalogs/databases, schemas, tables, views, and columns through connection metadata discovery.
- Translate the existing Ontology DSL into Doris SQL for single-table reads, joins, filters, aggregates, sorting, and pagination.
- Keep the adapter read-only and return explicit unsupported-operation errors for writes and other non-query paths.
- Add the adapter to Maven reactor, data-engine application assembly, and image payload construction.
- Add focused unit/contract tests and an opt-in real-Doris integration test covering read-only permissions and live-row visibility.

## Capabilities

### New Capabilities

- `doris-readonly-adapter`: Connect to Doris, discover visible source metadata, execute live ontology reads, and reject writes explicitly.

### Modified Capabilities

None.

## Impact

- Java: new `packages/data-adapters/doris` module and adapter/provider tests.
- Build: root Maven module list, data-engine application dependencies, and image build payload lists.
- Runtime: the data-engine registry and business-config adapter list will expose `doris` when the application assembly includes the module.
- Tests: real Doris tests will be opt-in through test-only environment variables and will not commit credentials or runtime data.
