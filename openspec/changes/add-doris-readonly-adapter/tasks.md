## 1. Module and read-only contract

- [x] 1.1 Add the `packages/data-adapters/doris` Maven module, provider, JDBC connection handling, and module-scoped provider/read-only tests.
- [ ] 1.2 Implement explicit unsupported-operation behavior for all Doris mutation and non-live-object paths, with tests covering every `DataAdapter` mutation family.
- [ ] 1.3 Add root reactor, application dependency, runtime assembly, and image payload entries, with focused assembly verification.

## 2. Doris SQL compiler and live query execution

- [ ] 2.1 Add failing unit tests for Doris identifier quoting, parameter binding, single-table DSL, joins, filters, aggregates, ordering, and pagination.
- [ ] 2.2 Implement the dedicated Doris read-only DSL-to-SQL compiler and JDBC execution path until the compiler tests pass.
- [ ] 2.3 Add live-query tests proving each request uses the current Doris connection/result path and does not copy or synchronize source rows.

## 3. Metadata and external compatibility

- [ ] 3.1 Add failing tests for Doris connection reports containing visible databases, schemas, tables, views, and columns, including SELECT-only behavior.
- [ ] 3.2 Implement bounded Doris metadata discovery using JDBC and read-only `information_schema` queries, normalizing table/view/column metadata.
- [ ] 3.3 Add the opt-in real-Doris integration test controlled by `ONTOMATO_TEST_DORIS_URL`, `ONTOMATO_TEST_DORIS_USER`, and `ONTOMATO_TEST_DORIS_PASSWORD`; verify the exact FE/driver SQL behavior available in the environment.

## 4. Verification and handoff

- [ ] 4.1 Run module-scoped tests, relevant core/assembly tests, secret scans, and the available strict OpenSpec verification; record pre-existing baseline failures separately.
- [ ] 4.2 Perform code-review-spec and task-boundary audits, update this checklist with evidence, and commit the task with an English Conventional Commit message.
