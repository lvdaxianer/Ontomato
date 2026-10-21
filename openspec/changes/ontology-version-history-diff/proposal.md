# Proposal

## Why

Ontology definitions are currently stored as one mutable JSON document. The existing schema export and import endpoints can replace that document without recording a revision or detecting concurrent edits, so importing an older Ontomap can silently overwrite changes made in the Ontology Manager. Users also cannot inspect how the current ontology differs from the initial import or restore a previous definition safely.

## What Changes

- Add domain-scoped ontology revisions with immutable history and a current revision pointer.
- Record one revision per successful logical save or confirmed import, not per keystroke or individual field request.
- Add revision-aware export and import preview APIs with optimistic concurrency checks.
- Detect and report field-level conflicts between the imported base, the current server revision, and the incoming ontology.
- Add a Chinese Ontology Manager workflow for revision history, structured ontology diff, import conflict resolution, and safe restoration by creating a new revision.
- Keep Doris business rows, adapter connection secrets, vector resources, and visual graph layout outside ontology revision content.
- Preserve existing ontology query behavior: the current revision remains the mapping used for live Doris queries.

## Capabilities

### New Capabilities

- `ontology-versioning`: Store, compare, import, conflict-resolve, and restore domain-scoped ontology definitions without silent overwrite.

### Modified Capabilities

- None.

## Impact

- Data Engine: PostgreSQL schema/migration, JSONRule persistence, admin APIs, validation, transactions, authorization, and audit behavior.
- Ontology Manager: typed API client, version history and diff components, import workflow, conflict resolution, loading/error/empty states, and focused tests.
- Existing `/admin/getSchema` and `/admin/setSchema` behavior must remain compatible for callers that do not use revision-aware import, while new revision-aware endpoints provide the safe workflow.
- No new runtime dependency is required for the first structured diff implementation; the comparator will match ontology entities by semantic keys rather than array position.
