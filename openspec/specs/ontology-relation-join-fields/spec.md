# ontology-relation-join-fields Specification

## Purpose

Keep SQL ontology relationships aligned with the data engine's join definition.
The ontology Manager exposes and preserves the source and target fields that
connect two ontology classes, including Doris relationships.

## Requirements

### Requirement: SQL relations expose join fields

The ontology relation editor MUST display source-field and target-field inputs
when the active data adapter is SQL-capable, and MUST require non-empty values
for both fields before creating a relation.

#### Scenario: Create a Doris relation

- **WHEN** the active adapter is Doris and the user opens the relation editor
- **THEN** the editor shows source-field and target-field inputs and rejects submission if either value is blank

#### Scenario: Preserve non-SQL behavior

- **WHEN** the active adapter is not SQL-capable
- **THEN** the editor does not require or submit join fields and the existing three-field relation flow remains available

### Requirement: Existing relation join fields are restored

The ontology Manager MUST map persisted source and target join fields from
ontology metadata into the relation model so reopening an existing SQL relation
restores both values.

#### Scenario: Load a persisted relation

- **WHEN** ontology metadata contains a relation with `fromField` and `toField`
- **THEN** the relation model contains those values and the edit form displays them

#### Scenario: Load legacy relation metadata

- **WHEN** ontology metadata omits one or both join fields
- **THEN** the relation remains loadable and the missing values are treated as empty without breaking the relation list
