# Spec Delta

## Purpose

Provides a durable, reviewable version history for ontology definitions so that
imports and concurrent edits cannot silently overwrite semantic mappings.

## ADDED Requirements

### Requirement: Persist logical ontology revisions

The system SHALL persist one immutable revision for each successful logical ontology save or confirmed import, scoped to the authenticated domain, and SHALL keep the active revision identifiable.

#### Scenario: Save a changed ontology once

- **WHEN** a user saves a batch containing multiple ontology edits
- **THEN** the system stores one new revision containing the complete resulting ontology and makes it active

#### Scenario: Save without semantic changes

- **WHEN** a save request produces content identical to the active ontology
- **THEN** the system SHALL NOT create another revision and SHALL return the existing active revision

#### Scenario: Revision content is isolated from live data

- **WHEN** a revision is exported or restored
- **THEN** it contains ontology definitions and mappings only, and does not contain Doris business rows, connection secrets, vector resources, or visual graph layout

### Requirement: Export a revision with its base identity

The system SHALL export a selected ontology revision as a portable document containing the document format version, ontology revision number, content hash, and ontology definitions required for import.

#### Scenario: Export the active revision

- **WHEN** an authorized user exports the current ontology
- **THEN** the response identifies the exported revision and includes its complete portable ontology document

#### Scenario: Export a historical revision

- **WHEN** an authorized user selects an older revision and exports it
- **THEN** the response contains that historical revision without changing the active revision

### Requirement: Preview imports before changing the active ontology

The system SHALL provide an import preview that validates the document format, ontology invariants, base revision identity, and conflicts against the current active revision without modifying production ontology content.

#### Scenario: Import is based on the active revision

- **WHEN** the incoming document base revision equals the active revision and validation succeeds
- **THEN** the preview reports no concurrency conflict and shows the resulting additions, removals, and modifications

#### Scenario: Import is based on an older revision

- **WHEN** the incoming document base revision differs from the active revision
- **THEN** the preview compares the base, active, and incoming documents and returns field-level conflicts plus non-conflicting changes

#### Scenario: Invalid import is rejected

- **WHEN** the document is malformed, has unsupported format metadata, or violates ontology invariants
- **THEN** the preview returns a stable validation error and does not create a revision

### Requirement: Resolve conflicts explicitly

The system SHALL require every conflicting ontology path to have an explicit resolution before applying an import, and SHALL support choosing the active value, incoming value, or an edited merged value.

#### Scenario: Apply a fully resolved merge

- **WHEN** all reported conflicts have resolutions and the active revision is unchanged
- **THEN** the system creates one new revision from the resolved ontology and returns its revision identity

#### Scenario: Active revision changes after preview

- **WHEN** another save changes the active revision after preview but before import confirmation
- **THEN** the apply request is rejected with a concurrency conflict and no revision is created

#### Scenario: Unresolved conflict is submitted

- **WHEN** the apply request omits a resolution for any conflict
- **THEN** the system rejects the request and leaves the active ontology unchanged

### Requirement: Compare arbitrary revisions in the manager

The Ontology Manager SHALL let an authorized user compare any two revisions using semantic ontology identities rather than array positions, and SHALL summarize and filter additions, removals, and modifications.

#### Scenario: Compare initial and current revisions

- **WHEN** the user selects the first revision and the active revision
- **THEN** the manager shows counts and field-level differences for object types, attributes, and relations while hiding unchanged entries by default

#### Scenario: Inspect a changed object

- **WHEN** the user selects a changed object type
- **THEN** the manager shows the old and new values for its description, attributes, primary key, and mapping flags

#### Scenario: Inspect a changed relation

- **WHEN** the user selects a changed relation
- **THEN** the manager shows the old and new source class, target class, join fields, description, and relationship name

### Requirement: Restore by creating a new revision

The system SHALL restore a historical ontology by creating a new active revision from that content, while preserving all existing revisions and leaving visual graph layout independent.

#### Scenario: Restore a historical revision

- **WHEN** an authorized user confirms restoration of revision 1 while revision 15 is active
- **THEN** the system creates revision 16 with revision 1 content, makes revision 16 active, and retains revisions 1 and 15

#### Scenario: Restore after a concurrent edit

- **WHEN** the active revision changes before restoration is confirmed
- **THEN** restoration is rejected with a concurrency conflict rather than overwriting the newer active revision

### Requirement: Protect versioned actions with domain authorization

The system SHALL apply the current domain authentication and authorization checks to revision listing, export, preview, apply, and restore operations, and SHALL not expose ontology content across domains.

#### Scenario: Access another domain's revision

- **WHEN** a request attempts to use a revision belonging to another domain
- **THEN** the system rejects the request without returning the revision content
