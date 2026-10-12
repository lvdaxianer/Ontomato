## 1. Module and read-only contract

- [x] 1.1 Add the `packages/data-adapters/doris` Maven module, provider, JDBC connection handling, and module-scoped provider/read-only tests.
- [x] 1.2 Implement explicit unsupported-operation behavior for all Doris mutation and non-live-object paths, with tests covering every `DataAdapter` mutation family.
- [x] 1.3 Add root reactor, application dependency, runtime assembly, and image payload entries, with focused assembly verification.

## 2. Doris SQL compiler and live query execution

- [x] 2.1 Add failing unit tests for Doris identifier quoting, parameter binding, single-table DSL, joins, filters, aggregates, ordering, and pagination.
- [x] 2.2 Implement the dedicated Doris read-only DSL-to-SQL compiler and JDBC execution path until the compiler tests pass.
- [x] 2.3 Add live-query tests proving each request uses the current Doris connection/result path and does not copy or synchronize source rows.
- [x] 2.4 Preserve the existing two-step SQL-adapter DSL shape by compiling its first-step read as a Doris subquery.

## 3. Metadata and external compatibility

- [x] 3.1 Add failing tests for Doris connection reports containing visible databases, schemas, tables, views, and columns, including SELECT-only behavior.
- [x] 3.2 Implement bounded Doris metadata discovery using JDBC and read-only `information_schema` queries, normalizing table/view/column metadata.
- [x] 3.3 Add the opt-in real-Doris integration test controlled by `ONTOMATO_TEST_DORIS_URL`, `ONTOMATO_TEST_DORIS_USER`, and `ONTOMATO_TEST_DORIS_PASSWORD`; verify the exact FE/driver SQL behavior available in the environment.

## 4. Verification and handoff

- [x] 4.1 Run module-scoped tests, relevant core/assembly tests, secret scans, and the available strict OpenSpec verification; record pre-existing baseline failures separately.
- [x] 4.2 Perform code-review-spec and task-boundary audits, update this checklist with evidence, and commit the task with an English Conventional Commit message.
- [x] 4.3 Refactor all new Doris production classes to satisfy the repository code-review contract without changing the read-only query and metadata behavior.

### Task 4.3 boundary and execution plan

- Module-oriented owner: Doris adapter production code and its focused tests only.
- Allowed files: `packages/data-adapters/doris/**` and this OpenSpec checklist.
- Out of scope: public contracts, other adapters, application behavior, and any Doris write capability.
- Dependencies: existing Doris provider, compiler, metadata, assembly, and integration tests.
- Focused verification: Doris module tests, source-size/method-size audit, strict OpenSpec validation, and full changed-tree diff review.
- Execution fallback: direct main-agent execution is used because the strict review requires coordinated edits across the shared compiler, metadata reader, connection probe, and tests; delegating overlapping refactors would create unsafe merge churn.

## Verification evidence

- `./mvnw -pl packages/data-adapters/doris -am test`: core 186 passed; Doris 15 total, 14 passed and the opt-in FE test skipped because its three environment variables were absent.
- `./mvnw -pl apps/data-engine -am -Dtest=DataAdapterAssemblyTest -Dsurefire.failIfNoSpecifiedTests=false test`: passed.
- `node --test scripts/data-engine/build-images.node-test.mjs`: 15 passed.
- `openspec validate add-doris-readonly-adapter --strict`: passed; `git diff --check`: passed; the changed tree contains no committed credential material.
- The pre-change baseline had unrelated failures in the stale `DataAdapterAssemblyTest` expectation and `OssAssetLookupAssemblyTest` missing `VectorRetrievalGate`; these were not used as Doris acceptance evidence.
- No Doris FE server or test credentials were available locally, so real FE SQL/driver compatibility remains an opt-in verification step.
