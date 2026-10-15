# Design

`BusinessConfigDao.query` remains the single boundary for persisted business
configuration. After legacy-shape validation, it calls an idempotent migration
helper. If `embeddingVectorSource` is absent or JSON null, the helper inserts an
empty object and the DAO persists the updated JSON. Existing non-null snapshots
are untouched. The empty object deliberately makes a valid current embedding
configuration differ from the recorded source, so the existing rebuild flow
remains the only path that publishes a real source snapshot.

`VectorRetrievalGate.isRebuildNeeded` first determines whether the current
embedding configuration is usable: a nonblank base URL and model name plus a
positive dimension. If it is not usable, it returns `false`, allowing the model
settings page to load without suggesting migration. If it is usable and the
source is still null (which can only occur when the gate is called outside the
DAO boundary), it retains the explicit migration error. Otherwise it compares
URL, model name, and dimensions as before.

The change is covered by unit tests for missing/null/present source migration,
unconfigured embeddings, and configured domains with an empty source requiring
rebuild.
