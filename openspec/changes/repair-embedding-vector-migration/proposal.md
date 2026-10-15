# Repair persisted embedding vector configuration

Existing domains created before vector-source tracking can contain a null
`embeddingVectorSource`. The model configuration page then calls vector status
and receives an upgrade-migration error, even when embedding has not been
configured yet.

This change makes the persisted-config read path perform an idempotent,
non-destructive migration of a missing or null vector source to an empty source
snapshot. A configured model will therefore be marked for rebuild rather than
being falsely treated as compatible. Domains without a usable embedding
configuration report no rebuild requirement instead of failing page load.

## Non-goals

- Do not infer or write a model name, URL, API key, or vector dimension.
- Do not rebuild vectors automatically.
- Do not change Doris, ontology, or business-data persistence.
