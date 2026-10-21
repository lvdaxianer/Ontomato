# Tasks

## 1. Revision storage and migration

- [x] 1.1 Add an idempotent PostgreSQL ontology revision schema and repository operations for listing, reading, hashing, and atomically promoting a domain revision; verify with focused DAO/migration tests that an existing `json_rule` domain is bootstrapped as revision 1 without content changes.
- [x] 1.2 Add canonical ontology JSON normalization and semantic three-way diff/merge utilities; verify additions, removals, scalar edits, delete-versus-edit conflicts, no-op detection, and stable entity matching with unit tests.

## 2. Revision-aware Data Engine APIs

- [x] 2.1 Add authenticated domain-scoped endpoints for revision listing, revision export, import preview, resolved import apply, and historical restore; verify request validation and stable error codes with controller/service tests.
- [ ] 2.2 Route logical ontology saves and legacy schema writes through one transactional revision service with optimistic `baseRevision`/hash checks; verify one revision per logical change, no-op saves, stale-write rejection, and rollback behavior.
- [ ] 2.3 Add backend audit-safe diagnostics for revision operations without logging ontology secrets or full business data; verify authorization and cross-domain rejection tests.

## 3. Manager contracts and structured Diff model

- [x] 3.1 Extend the typed manager client with revision, export, preview, apply, restore, and conflict contracts while preserving the existing metadata/query client; verify request paths, payloads, stale-response handling, and error mapping in API tests.
- [x] 3.2 Add a pure TypeScript ontology Diff model that groups object, attribute, and relation changes by semantic key and exposes summary counts; verify first-versus-current comparisons and unchanged filtering with unit tests.

## 4. Ontology Manager version UI

- [ ] 4.1 Add the version-history surface using existing Element Plus components, showing active revision, source, actor, timestamp, export action, and compare/restore actions; verify loading, empty, permission-error, and retry states with component tests.
- [ ] 4.2 Add the two-version structured Diff view with revision selectors, summary counts, change filters, grouped navigation, and field-level old/new highlighting; verify object, attribute, relation, and deletion rendering with component tests.
- [ ] 4.3 Add the import dialog with file validation, preview summary, conflict resolution controls, repeated-submit protection, and successful import refresh; verify no-conflict import, unresolved-conflict blocking, stale-revision recovery, and keyboard-accessible actions.
- [ ] 4.4 Add safe historical restore confirmation that creates a new revision and refreshes the current ontology without changing graph layout; verify confirmation, failure recovery, and current-version refresh behavior.

## 5. Integration verification and documentation

- [ ] 5.1 Update the manager API documentation and user-facing Chinese labels for revision history, Diff, import preview, conflict choices, and restore behavior; verify all new endpoints and UI actions are represented.
- [ ] 5.2 Run OpenSpec validation, focused Java and Vue tests, type checks, and the applicable broader build checks; verify that existing live Doris query tests and current Ontology Manager tests remain green.

## Workflow follow-up

- Review each task against the backend, frontend interaction, code review, logging, and TDD gates before marking it complete.
- Merge completed task branches into `features/doris` only after focused verification and audit; do not deploy this change during this work.
- Archive the OpenSpec change only after the final integration audit passes.
