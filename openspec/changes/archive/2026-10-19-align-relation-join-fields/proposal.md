# Proposal

## Why

The company Ontology Manager presents source and target join fields when creating or editing a relation, while the deployed open-source Manager image can show only the source class, target class, and description. This makes Doris/SQL ontology relationships impossible to review consistently and can lose join-field context when existing relations are loaded.

## What Changes

- Keep source-field and target-field inputs visible and required for SQL data adapters, including Doris.
- Preserve relation join fields in the Manager's typed metadata model and parse them when loading ontology metadata.
- Restore join-field values when an existing relation is opened for editing.
- Add regression coverage for relation metadata parsing and the existing add-relation request payload.
- Build the standalone Ontology Manager image from the updated public source so the deployed UI contains the same behavior.

## Capabilities

### New Capabilities

- `ontology-relation-join-fields`: SQL ontology relations expose and preserve the two fields used to join source and target classes.

### Modified Capabilities

## Impact

- `packages/ontology-manager` relation API types, metadata parsing, visual-modeling state, and tests.
- Standalone Ontology Manager image build and deployment artifact; no changes to the data engine, Doris adapter, or business data.
