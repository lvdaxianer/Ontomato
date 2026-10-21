# Ontology Version History and Diff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the task checklist in the OpenSpec change and execute each task with TDD, review, and verification gates.

**Goal:** Add safe ontology revision history, structured version comparison,
conflict-aware Ontomap import, and historical restore without changing live
Doris query semantics.

**Architecture:** Keep the active JSONRule compatibility document in PostgreSQL,
add an append-only domain revision table, and guard writes with canonical content
hashes plus optimistic revision checks. Add typed manager APIs and focused Vue
components; compare semantic ontology entities instead of array positions.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, PostgreSQL, Vue 3
Composition API with `<script setup lang="ts">`, TypeScript, Element Plus,
Vitest, and existing Maven/PNPM test runners.

---

## Task 1: Add revision persistence and semantic merge primitives

**Files:**

- Create `packages/data-engine-core/src/main/java/io/ontomato/dataengine/dao/OntologyRevisionDao.java`.
- Create `packages/data-engine-core/src/main/java/io/ontomato/dataengine/service/OntologyRevisionService.java`.
- Create focused Java tests under `packages/data-engine-core/src/test/java/io/ontomato/dataengine/dao/` and `.../service/`.
- Modify `packages/data-engine-core/src/main/java/io/ontomato/dataengine/dao/JSONRuleDao.java` only for active revision bootstrap compatibility.
- Add the OpenSpec files under `openspec/changes/ontology-version-history-diff/`.

- [ ] Write failing tests for revision 1 bootstrap, one revision per changed save, no-op detection, and conditional stale-write rejection.
- [ ] Run the focused Maven tests and confirm they fail because the revision DAO/service behavior is absent.
- [ ] Implement the idempotent revision schema and transaction-safe DAO operations using domain ID, monotonic revision, canonical content hash, source, actor, parent revision, and timestamp.
- [ ] Implement canonical JSON normalization and semantic three-way merge with explicit conflict paths for scalar changes, delete-versus-edit, and structural mismatches.
- [ ] Run the focused Java test selection again and confirm all new tests pass without running unrelated database integration tests unfiltered.

## Task 2: Add revision-aware admin APIs

**Files:**

- Modify `packages/data-engine-core/src/main/java/io/ontomato/dataengine/controller/AdminController.java`.
- Modify `packages/data-engine-core/src/main/java/io/ontomato/dataengine/service/impl/AdminServiceImpl.java` or add a dedicated service implementation boundary if existing wiring requires it.
- Add controller/service tests under `packages/data-engine-core/src/test/java/io/ontomato/dataengine/controller/` and `.../service/impl/`.

- [ ] Write failing tests for revision listing, historical export, import preview, resolved apply, restore, domain isolation, invalid payloads, and stable conflict errors.
- [ ] Run those focused tests and confirm they fail before endpoint/service implementation.
- [ ] Implement revision list/export/preview/apply/restore endpoints using the existing authenticated domain and response envelope.
- [ ] Route legacy schema saves through the revision service where revision metadata is available, preserve the active JSONRule shape, and ensure one transaction updates current content and history.
- [ ] Verify stale apply and restore requests return a conflict without changing `json_rule` or inserting a revision.
- [ ] Run focused tests and the applicable data-engine module test subset.

## Task 3: Add manager contracts and pure Diff model

**Files:**

- Modify `packages/ontology-manager/src/api.ts`.
- Create `packages/ontology-manager/src/ontology-versioning.ts`.
- Create `packages/ontology-manager/src/__tests__/ontology-versioning.test.ts`.
- Modify `packages/ontology-manager/src/__tests__/api.test.ts`.

- [ ] Write failing tests for revision API paths/payloads, stale-response handling, semantic matching, summary counts, and hidden unchanged entries.
- [ ] Run `pnpm --filter @ontomato/ontology-manager test -- ontology-versioning` and confirm RED failures.
- [ ] Add typed revision/export/preview/apply/restore client methods and a pure semantic diff function keyed by class, class-plus-attribute, and relation name.
- [ ] Run the focused Vitest suite and confirm GREEN; then run the existing manager test suite to catch contract regressions.

## Task 4: Build version history and Diff UI

**Files:**

- Create `packages/ontology-manager/src/components/OntologyVersionHistory.vue`.
- Create `packages/ontology-manager/src/components/OntologyVersionCompare.vue`.
- Create `packages/ontology-manager/src/components/OntologyDiffSection.vue`.
- Modify `packages/ontology-manager/src/ManagerWorkspace.vue` and relevant locale files under `packages/ontology-manager/src/locales/`.
- Add component tests under `packages/ontology-manager/src/__tests__/`.

- [ ] Write failing component tests for active version display, history loading/empty/error states, two-version selection, summary badges, semantic field highlighting, and restore confirmation.
- [ ] Run the focused component tests and confirm RED failures.
- [ ] Implement Composition API components with typed props/emits and existing Element Plus dialogs, tables, tags, alerts, buttons, and loading states.
- [ ] Keep compare state and mutation orchestration in the workspace container; children emit selection and resolution events rather than mutating parent state.
- [ ] Verify slow requests, repeated clicks, retry behavior, keyboard focus, and stale response recovery in component tests.

## Task 5: Build import preview and conflict resolution UI

**Files:**

- Create `packages/ontology-manager/src/components/OntologyImportDialog.vue`.
- Create `packages/ontology-manager/src/components/OntologyConflictResolver.vue`.
- Modify `packages/ontology-manager/src/ManagerWorkspace.vue` and Chinese/English locale resources.
- Add focused component tests under `packages/ontology-manager/src/__tests__/`.

- [ ] Write failing tests for valid no-conflict import, malformed file rejection, conflict summary, per-path active/incoming/manual selection, unresolved-conflict blocking, stale revision recovery, and successful refresh.
- [ ] Run the focused tests and confirm RED failures.
- [ ] Implement file parsing, preview, conflict resolution, disabled submit state, success feedback, and reload/re-preview recovery without silent data loss.
- [ ] Add export actions for current and selected historical revisions and safe restore that creates a new revision.
- [ ] Run the full ontology-manager Vitest suite and `pnpm --filter @ontomato/ontology-manager typecheck`.

## Task 6: Final verification and audit

**Files:**

- Modify API/user documentation only where the new endpoints and UI workflow are documented.
- Keep deployment files unchanged because deployment is explicitly out of scope for this request.

- [ ] Run `openspec validate ontology-version-history-diff --strict`.
- [ ] Run focused Java tests for revision persistence/API behavior, focused Vue tests, manager typecheck, and the applicable broader Maven/PNPM checks.
- [ ] Inspect `git diff`, review backend reliability and frontend interaction gates, and verify live Doris query code still uses the active JSONRule without reading revision history into business data.
- [ ] Mark each OpenSpec task complete only after its own tests, review, and audit pass; commit each completed atomic task with the project-required English commit format.
- [ ] Merge verified task branches back into `features/doris`, confirm no deployment was performed, and archive the OpenSpec change after final audit.
