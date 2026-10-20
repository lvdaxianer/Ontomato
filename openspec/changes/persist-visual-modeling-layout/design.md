# Design

## Decision

Persist the visual-modeling layout in a dedicated, domain-scoped PostgreSQL table. Do not extend `json_rule`, because ontology imports and schema edits replace that document as a whole and should not overwrite presentation state.

## Data model

Create `ontology_graph_layout` idempotently during data-engine startup:

- `domain_id VARCHAR(64) PRIMARY KEY`
- `positions JSONB NOT NULL DEFAULT '{}'::jsonb`, keyed by stable ontology `className` with finite `x` and `y` values
- `viewport JSONB` reserved for the current canvas viewport without making it required for node restoration
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

The write operation replaces the complete position snapshot for the domain using PostgreSQL upsert semantics. The client sends only the classes currently present, so deleted classes disappear on the next successful save.

## Backend boundary

Add domain-scoped methods to the existing admin service and controller:

- `POST /admin/getGraphLayout` returns `{ positions, viewport }` for the authenticated user's domain, or empty values when no row exists.
- `POST /admin/saveGraphLayout` accepts `{ positions, viewport }`, validates that the payload is an object and every coordinate is finite, then replaces the authenticated domain's snapshot.

The controller obtains `domainId` from the current authenticated user, never from the request body. The existing authentication and admin authorization boundary remains in force. Invalid payloads return the established `{ success: false, message }` response shape.

## Frontend behavior

Extend the manager client with typed load/save methods. On visual-modeling load, fetch ontology metadata and the saved layout before rendering. Merge saved positions by class name; classes without a saved position receive the existing deterministic default.

Replace the visual-modeling `localStorage` functions with an in-memory layout snapshot and server operations:

- collect all current node positions after a drag settles;
- debounce writes by approximately 600 ms;
- keep only the newest pending snapshot;
- flush a pending snapshot when the component is leaving;
- show `saving`, `saved`, and `error` states near the graph toolbar;
- retain the newest snapshot in memory and expose a retry action after failure.

The initial load does not write back to the server. Existing nodes keep their saved positions when classes are added, while a class deleted through the page is omitted from subsequent snapshots. Zoom and pan are not required for the first version; the API accepts nullable viewport state so this can be added without changing the storage boundary.

## Alternatives rejected

1. Browser `localStorage`: survives refresh only for one browser and cannot provide shared layout.
2. A field inside `json_rule`: couples presentation state to ontology import/export and risks losing positions during full metadata replacement.
3. One row per node: normalizes coordinates but adds unnecessary write amplification and complicates atomic replacement of a whole diagram. A single domain row with JSONB keeps each save atomic and is sufficient for the expected graph size.

## Verification

- DAO tests cover table initialization, empty reads, valid upserts, and invalid coordinate rejection.
- Controller/service tests cover domain scoping and response shapes.
- Ontology Manager tests cover loading saved coordinates, debounced save requests, failed-save status, and retry behavior.
- Run focused Java and Vue test commands, then the relevant module build before integration.
