# Proposal

## Why

The Ontology Manager visual-modeling page currently restores node positions from browser-local storage only. This makes a carefully arranged ontology diagram unavailable after changing browsers, clearing site data, or opening the same ontology from another user session.

The layout is shared ontology metadata and must therefore be persisted with the Ontomato PostgreSQL data rather than with an individual browser.

## What Changes

- Add a domain-scoped PostgreSQL store for visual-modeling node positions and optional viewport state.
- Add authenticated data-engine endpoints to read and replace the current domain's graph layout.
- Load persisted positions when the visual-modeling page opens and use defaults only for classes without saved positions.
- Automatically save the complete layout after a drag settles, with visible saving/saved/failed feedback and retry behavior.
- Remove visual-modeling layout persistence from `localStorage`.
- Keep layout data independent from ontology JSON imports and preserve existing positions when classes are added.

## Capabilities

### New Capabilities

- `ontology-graph-layout`: Persist and restore the shared visual-modeling graph layout for each ontology domain.

### Modified Capabilities

## Impact

- `packages/data-engine-core`: PostgreSQL DAO, domain-scoped admin endpoints, input validation, and focused tests.
- `packages/ontology-manager`: layout API client, server-backed loading/saving, debounced drag-end persistence, and save-state feedback.
- Ontomato PostgreSQL: one new table initialized idempotently by the data engine.
- No change to Doris business data, ontology query semantics, or the existing ontology definition JSON contract.
