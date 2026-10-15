# Embedding vector migration

## ADDED Requirements

### Requirement: Migrate an absent vector source without claiming compatibility
The persisted business configuration reader MUST replace an absent or null
`embeddingVectorSource` with an empty object and persist that change exactly
once. It MUST preserve every existing non-null source snapshot.

#### Scenario: Existing domain has a null source
- **WHEN** a domain configuration is read
- **THEN** the stored source becomes an empty object
- **AND** a valid current embedding configuration is treated as requiring a rebuild

#### Scenario: Existing domain has a source snapshot
- **WHEN** a domain configuration is read
- **THEN** the source snapshot is unchanged

### Requirement: An unconfigured embedding model must not break settings loading
The vector retrieval gate MUST report no rebuild requirement when the current
embedding configuration is absent or lacks a nonblank base URL, model name, or
positive dimension.

#### Scenario: Embedding configuration is not usable
- **WHEN** vector rebuild status is requested
- **THEN** the request succeeds with no rebuild requirement
- **AND** it does not report an upgrade-migration error
