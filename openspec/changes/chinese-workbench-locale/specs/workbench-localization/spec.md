# Spec Delta

## Purpose

Provide a consistent Chinese-first language experience for the standalone
Ontomato workbench while preserving an explicit English option and keeping
server-generated messages aligned with the selected locale.

## ADDED Requirements

### Requirement: Standalone workbench defaults to Simplified Chinese

The standalone workbench MUST start in Simplified Chinese when no supported
locale has been selected.

#### Scenario: First visit uses Chinese

- **WHEN** a user opens the standalone workbench without a locale preference
- **THEN** navigation, empty states, forms, buttons, validation messages, and
  administration screens use Simplified Chinese

#### Scenario: Chinese resources cover current workbench messages

- **WHEN** a visible workbench message key is requested in Simplified Chinese
- **THEN** the Chinese resource provides a translated message or a safe locale
  fallback that does not expose an untranslated key

### Requirement: English remains selectable

The standalone workbench MUST allow a user to explicitly select English without
changing the Simplified Chinese default.

#### Scenario: User selects English

- **WHEN** the user selects English from the workbench language control
- **THEN** supported workbench screens and controls use the existing English
  resource

### Requirement: Selected locale reaches data-engine requests

The workbench MUST send the active supported locale to the data engine for
requests whose response or error messages are localized by that service.

#### Scenario: Chinese request language

- **WHEN** the workbench is using Simplified Chinese
- **THEN** its data-engine request includes `Accept-Language: zh-CN`

#### Scenario: English request language

- **WHEN** the workbench is using English
- **THEN** its data-engine request includes `Accept-Language: en`

### Requirement: Embedded workbench hosts retain locale authority

Embedded workbench instances MUST continue to use a locale supplied by the
host when the host provides one.

#### Scenario: Host supplies a supported locale

- **WHEN** an embedding host supplies English or Simplified Chinese
- **THEN** the workbench uses that locale rather than overwriting it with the
  standalone default
