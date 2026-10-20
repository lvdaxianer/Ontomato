# Spec Delta

## Purpose

Persist the shared visual-modeling layout as ontology metadata so that the same ontology diagram is restored across page refreshes, browser sessions, users, and application restarts.

## ADDED Requirements

### Requirement: Domain-scoped layout persistence

The system SHALL persist one shared visual-modeling layout per ontology domain, independently from the ontology definition JSON.

#### Scenario: Read an existing layout

- **WHEN** an authenticated client requests the layout for its current domain
- **THEN** the system returns the saved node positions and optional viewport state for that domain

#### Scenario: Isolate layouts between domains

- **WHEN** two domains save different positions for a class with the same name
- **THEN** reading one domain's layout SHALL never return the other domain's positions

### Requirement: Restore persisted node positions

The visual-modeling page SHALL use persisted coordinates for matching ontology classes and SHALL assign deterministic default coordinates to classes without saved coordinates.

#### Scenario: Refresh restores positions

- **WHEN** a user refreshes the visual-modeling page after a successful layout save
- **THEN** existing classes SHALL render at their previously saved coordinates

#### Scenario: New class has no saved coordinate

- **WHEN** the ontology contains a class that is absent from the saved layout
- **THEN** that class SHALL render using a default position without changing the saved coordinates of existing classes

### Requirement: Automatic layout save

The visual-modeling page SHALL automatically persist the complete current node layout after a node drag settles, without requiring a separate save button.

#### Scenario: Save after dragging

- **WHEN** the user finishes dragging one or more nodes
- **THEN** the client SHALL submit the current layout after a short debounce and SHALL avoid sending one request for every pointer movement

#### Scenario: Save before leaving with pending changes

- **WHEN** the user leaves the page while a debounced layout save is pending
- **THEN** the client SHALL attempt to submit the latest layout before disposing the page state

### Requirement: Layout save feedback and failure handling

The visual-modeling page SHALL expose saving status and SHALL not silently discard a failed server save.

#### Scenario: Save succeeds

- **WHEN** the layout write succeeds
- **THEN** the page SHALL expose a saved state and the next load SHALL be able to restore that layout

#### Scenario: Save fails

- **WHEN** the layout write fails
- **THEN** the page SHALL expose a failure state with a retry action and SHALL keep the latest layout in memory for retry

### Requirement: Remove deleted class positions

The saved layout SHALL not retain positions for classes removed from the ontology after the layout is next persisted.

#### Scenario: Delete a class

- **WHEN** a class is deleted and the current layout is saved
- **THEN** the deleted class's position SHALL be absent from the persisted layout while remaining class positions are preserved
