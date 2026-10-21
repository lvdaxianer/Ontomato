# Design

## Context

The data engine currently stores the active ontology as one JSON document in
PostgreSQL `json_rule`. `/admin/getSchema` exports the pretty ontology shape and
`/admin/setSchema` replaces the active document. The Ontology Manager currently
loads object, attribute, and relation definitions through `/admin/getMetas`; live
business rows continue to flow through the configured data adapter, including
Doris. Visual graph layout already has a separate domain-scoped store and must
not be coupled to ontology replacement.

## Goals / Non-Goals

**Goals:**

- Add immutable, domain-scoped ontology revision history without breaking the
  current JSONRule query contract.
- Make imports previewable, three-way mergeable, and protected by optimistic
  concurrency checks.
- Provide a focused manager experience for history, structured Diff, conflict
  resolution, export, and safe restoration.
- Keep each logical save or confirmed import to one revision and avoid creating
  revisions for no-op saves.
- Validate all server-controlled invariants and preserve domain authorization.

**Non-Goals:**

- Copying or versioning Doris business rows.
- Synchronizing Doris schema changes into ontology definitions automatically.
- Versioning connection credentials, vector stores, audit logs, or graph layout.
- Adding a general-purpose raw JSON editor as the primary user experience.

## Decisions

### 1. Keep `json_rule` as the active compatibility record and add history

Add a domain-scoped `ontology_revisions` table containing revision number,
content, content hash, source, actor, parent revision, and creation time. Extend
`json_rule` with the active revision and hash, or keep those fields in a
domain-scoped current-state record if the migration proves safer. Every write
must update the active content and history in one transaction. This keeps the
existing DSL and adapter callers unchanged while making historical reads
explicit.

An append-only history table is preferred over storing an array of revisions in
`json_rule`: it supports bounded listing, independent export, auditability, and
safe retention without rewriting a large document.

### 2. Use optimistic concurrency and content hashes

Revision-aware writes require `baseRevision` and `baseHash`. The service locks
or conditionally updates the current domain record and rejects the request when
the active revision or hash no longer matches. A rejected write returns a stable
conflict code and no partial revision. Content hashes use canonical JSON so
equivalent key ordering does not create false changes.

### 3. Use three-way semantic merge

The server compares base, current, and incoming documents by semantic keys:

- object type: `classDefs[].name`
- attribute: object name plus `attrs[].name`
- relation: `relationshipDefs` key

For each scalar field, a change made by only one side is auto-merged. Different
changes to the same path, delete-versus-edit, and incompatible structural
changes become explicit conflicts. The preview returns a normalized change set
and conflict paths; apply requires a resolution for every conflict and repeats
the revision check.

### 4. Keep the existing endpoints and add revision-aware endpoints

Existing schema endpoints remain available for compatibility. The manager uses
new revision-aware endpoints for the new workflow:

- list revisions for the current domain
- get/export a selected revision
- preview an incoming document
- apply a resolved import with `baseRevision`
- restore a historical revision with `baseRevision`

Legacy `setSchema` remains a compatibility path but must create a revision and
use the same validation/concurrency service when it is called with revision
metadata. New manager actions never call an unconditional replacement endpoint.

### 5. Build the manager UI around a structured Diff

Use small Composition API components and typed props/events:

- `OntologyVersionHistory`: revision timeline, active badge, source, actor, and
  actions.
- `OntologyVersionCompare`: two revision selectors, summary counts, filters,
  and grouped change navigation.
- `OntologyDiffSection`: object/attribute/relation field-level old/new values.
- `OntologyImportDialog`: file parsing, preview state, validation summary, and
  apply action.
- `OntologyConflictResolver`: per-path active/incoming/manual choices.

The parent owns selected revisions, loaded snapshots, and mutation state; child
components emit selections and resolutions. Existing Element Plus primitives
are used for dialogs, tabs, tags, tables, alerts, buttons, and loading states.
No new UI dependency is required initially. A raw JSON view can be added as a
secondary read-only tab later without changing the structured contract.

### 6. Restore creates a new revision

Restoration reads a historical snapshot and commits it as a new revision under
the current active revision as parent. It never deletes or rewrites history,
and it never includes graph layout content.

## Risks / Trade-offs

- [Risk] Existing callers of `setSchema` do not send revision metadata. → Keep
  the legacy response shape, route writes through the revision service, and
  return a concurrency error when a caller supplies a stale base revision.
- [Risk] Existing JSON documents lack stable IDs for renames. → Treat rename as
  delete plus add in the first implementation and document the semantic-key
  behavior; introduce immutable IDs only in a future format revision.
- [Risk] Large ontology documents can make three-way comparison expensive. →
  Canonicalize once per request, cap revision list payloads, compare only the
  selected snapshots, and return grouped changes instead of duplicate full
  documents.
- [Risk] A failed import could leave active metadata and history inconsistent.
  → Use one database transaction for current-state update plus history insert,
  and add rollback/concurrency tests.
- [Risk] Users may confuse ontology differences with Doris data differences. →
  Label the page as definition/version comparison and explicitly exclude live
  business rows from the diff summary.

## Migration Plan

1. Add the history table and active revision/hash columns idempotently.
2. On first read of an existing domain with no history, create revision 1 from
   the current `json_rule` content without changing its ontology.
3. Deploy backend endpoints and manager UI together; old clients continue to use
   the legacy schema response shape.
4. Verify import preview, stale-write rejection, restore, and live Doris query
   behavior against a migrated domain.
5. Roll back application code if necessary; retain the additive history table.
   A repair script can rebuild the active `json_rule` from its latest revision.
