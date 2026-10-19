# Ontology Manager localization

## Purpose

Provide consistent Simplified Chinese and English language resources for the
Ontology Manager so standalone and embedded deployments use the same terminology.

## Requirements

### Requirement: Standalone Manager defaults to Simplified Chinese

The standalone Ontology Manager MUST start in Simplified Chinese and MUST use
Chinese translations for all current Manager-owned and shared visible messages.

#### Scenario: Open the standalone Manager without a locale selection

- **WHEN** a user opens the standalone Manager without a locale parameter or stored locale
- **THEN** navigation, pages, dialogs, validation messages, operation feedback, and Element Plus controls display in Simplified Chinese

#### Scenario: Chinese resource covers current message keys

- **WHEN** a message key is requested by the Manager
- **THEN** the Simplified Chinese resource provides a translation or the existing safe fallback is used without displaying an untranslated key

### Requirement: English remains selectable

The standalone Manager MUST support selecting English explicitly without changing
the Chinese default or the host-supplied locale contract.

#### Scenario: Select English through the startup locale

- **WHEN** a user starts the standalone Manager with the supported English locale
- **THEN** the Manager displays the existing English resource and Element Plus English controls

### Requirement: Embedded hosts retain locale authority

Embedded Manager instances MUST continue to use the locale and message bundle
provided by the host when those resources are supplied.

#### Scenario: Host supplies Chinese resources

- **WHEN** the host provides a locale and message bundle for a Manager instance
- **THEN** the instance uses the host bundle and does not overwrite it with standalone defaults
