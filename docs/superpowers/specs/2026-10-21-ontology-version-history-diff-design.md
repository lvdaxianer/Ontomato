# Ontology Version History and Diff Design

## Decision

Add immutable, domain-scoped ontology revisions around the existing PostgreSQL
`json_rule` current-state record. Every logical save or confirmed import creates
one revision only when the canonical ontology content changes. Revision-aware
writes require the caller's base revision and content hash; stale writes return a
stable conflict result without modifying the active ontology.

The import workflow uses a server-side three-way merge of the exported base,
current server revision, and incoming document. Objects match by class name,
attributes by class plus attribute name, and relations by relation name. The
manager shows a structured diff first and keeps raw JSON comparison as a
secondary diagnostic view. Restoring an old revision creates a new revision and
does not delete history or touch the independent visual graph layout.

## Component map

- `OntologyVersionHistory.vue`: lists revisions and exposes compare/export/restore actions.
- `OntologyVersionCompare.vue`: selects two revisions and renders grouped change summaries.
- `OntologyDiffSection.vue`: renders one object/attribute/relation change group with old/new values.
- `OntologyImportDialog.vue`: owns file selection, preview, validation, and apply states.
- `OntologyConflictResolver.vue`: emits explicit active/incoming/manual resolutions for each conflict.
- `ontology-versioning.ts`: pure semantic diff and merge utilities with no Vue or network dependencies.
- `api.ts`: typed manager client methods for revision endpoints; request orchestration remains in the workspace container.

## Backend boundaries

- `OntologyRevisionDao` owns revision rows and current-revision conditional updates.
- `OntologyRevisionService` owns canonicalization, validation, bootstrap, three-way merge, transaction boundaries, and stable errors.
- `AdminController` only authenticates the current domain, validates request shape, and maps service results to the existing response envelope.
- Existing `JSONRuleDao` remains the compatibility reader for the active `json_rule` content; live DataAdapter/Doris query paths continue receiving the current JSONRule.

## Interaction states

Every version list, compare load, import preview, apply, and restore action has
loading, empty, permission/error, retry, disabled, and success states. Apply and
restore buttons become disabled while requests are active. A 409-style stale
revision response keeps the user's local import/selection available and offers
reload and re-preview actions; it never silently discards or overwrites work.
