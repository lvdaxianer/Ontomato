# Ontology Manager Code Editor

## ADDED Requirements

### Requirement: Python source uses the formatted code editor

The Ontology Manager SHALL provide a Python-aware code editor for metric-view
and function source fields, including syntax highlighting and an explicit
format action.

#### Scenario: Edit Python source

- **WHEN** a user opens a metric view or function with Python source
- **THEN** the source is editable in the Python-aware editor
- **AND** the existing source is preserved until the user changes it

### Requirement: Formatting is server-side and failure-safe

The editor SHALL request formatting from `POST /python/format` and SHALL replace
the editor content only after a successful formatted-code response.

#### Scenario: Formatting succeeds

- **WHEN** the formatter returns formatted Python source
- **THEN** the editor displays the formatted source
- **AND** the user receives localized success feedback

#### Scenario: Formatting fails

- **WHEN** the formatter rejects invalid source or is unavailable
- **THEN** the editor keeps the original source unchanged
- **AND** the user receives localized failure feedback
