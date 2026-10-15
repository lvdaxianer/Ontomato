# Repair persisted embedding vector configuration

The deployed settings page currently calls the vector rebuild status endpoint.
For an older domain, the persisted JSON has `embeddingVectorSource: null`; for
an unconfigured domain, the current embedding settings can also lack a
dimension. The backend must distinguish these cases without treating old
vectors as compatible by assumption.

The approved design is to normalize only a missing/null persisted vector source
to `{}` at the business-config DAO boundary. This is idempotent and preserves
the rebuild safety rule: once a usable embedding model is configured, the empty
source forces an explicit vector rebuild, and only a successful rebuild records
the actual model URL, name, and dimensions. The retrieval gate returns
`false` for an unusable embedding configuration so the settings page can load
normally before setup is complete.

No model credentials, dimensions, vectors, Doris data, or ontology data are
inferred or modified by this change.
