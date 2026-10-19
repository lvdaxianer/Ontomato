# Design

## Context

The visual-modeling relation dialog already has state and inputs for source and target join fields, gated by the active adapter's `sql` capability. The relation API type and metadata normalization do not carry those fields consistently, and the visual-modeling loader currently drops them from persisted `relationship_rule` entries. See the proposal and capability spec for the user-visible behavior.

## Goals / Non-Goals

**Goals:**

- Keep SQL/Doris relation creation behavior capability-driven rather than tied to a vendor name.
- Carry `fromField` and `toField` through the Manager's relation model and metadata normalization.
- Preserve legacy metadata that has no join fields.
- Add focused API regression coverage and rebuild the standalone Manager image from this source.

**Non-Goals:**

- No changes to the data engine relation schema or SQL compiler.
- No new relation-edit API; existing relation editing remains description/local-model behavior unless the backend already exposes a compatible endpoint.
- No changes to non-SQL adapters, ontology storage, or business data.

## Decisions

1. **Use the adapter capability flag.** The dialog remains controlled by `currentType` plus the provider's `sql` flag. This supports Doris and existing SQL adapters without maintaining a vendor allow-list. An unconditional UI would expose fields that non-SQL backends cannot use.
2. **Normalize fields at the API boundary.** Add optional `fromField` and `toField` to `OntologyRelation` and map the persisted camel-case keys, defaulting absent values to empty/undefined. This keeps UI components independent of backend response-shape details and preserves legacy relations.
3. **Reuse existing dialog state and validation.** The current `VisualModelingDialogs` inputs, `showJoinFields` gate, and create validation remain the single UI path. Only the dropped metadata fields and corresponding tests are changed, avoiding duplicate relation-form logic.
4. **Ship the Manager image from the public checkout.** Build with `apps/ontology-manager/build.mjs --arch amd64`, verify the image identity and UI build, then update only the `ontology-manager` service on 31.37. The existing Nginx template mount and timeout configuration remain unchanged.

## Risks / Trade-offs

- **[Old deployed image]** The running 31.37 Manager image may not include the already-present relation-field UI. → Rebuild and deploy the standalone Manager image after source verification; verify the rendered bundle and service health.
- **[Legacy metadata]** Existing relations may not contain join fields. → Treat fields as optional at the API boundary and only require them for SQL relation creation.
- **[Edit persistence]** The current backend API exposes description editing but not a confirmed join-field update endpoint. → Do not invent a write contract; preserve fields on load/create and keep the existing edit semantics within scope.
