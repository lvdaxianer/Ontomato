# Ontology revision API

The Ontology Manager stores ontology definitions in the data engine's PostgreSQL metadata store. Revision endpoints are scoped to the authenticated domain and keep business records in their original data source; exported revision documents contain ontology metadata only.

All requests use the Manager data-query proxy and the host credential. Successful responses use the existing `{ "success": true, "data": ... }` envelope. Failures include a stable `code` and a user-facing `message`.

## List history

```http
GET /admin/ontology/revisions?limit=20&offset=0
```

Returns newest-first metadata without repeating the ontology body:

```json
{
  "items": [
    {
      "revision": 4,
      "contentHash": "sha256...",
      "source": "manual",
      "actorId": "user-id",
      "createdAt": "2026-10-21T03:00:00Z"
    }
  ],
  "activeRevision": 4
}
```

`source` is normally `manual`, `import`, `restore`, or `migration`.

## Export one revision

```http
GET /admin/ontology/revisions/{revision}
```

The response includes `formatVersion`, revision metadata, and the ontology `content`. It never includes data rows from Doris, PostgreSQL, or another configured adapter.

## Preview an import

The exported document is sent as `content` together with the revision and hash from which it was exported:

```http
POST /admin/ontology/revisions/preview
Content-Type: application/json
```

```json
{
  "baseRevision": 2,
  "baseHash": "sha256...",
  "content": {
    "classDef": [],
    "relationship_rule": {}
  }
}
```

The result contains the current revision/hash, the incoming hash, the merged draft, and semantic conflicts. A conflict includes its semantic `path`, the base value, the current value, and the incoming value. Preview never changes stored state.

## Apply an import

```http
POST /admin/ontology/revisions/apply
Content-Type: application/json
```

Use the same `content`, `baseRevision`, and `baseHash`. Add `resolutions` only for conflicts the user has explicitly reviewed:

```json
{
  "baseRevision": 2,
  "baseHash": "sha256...",
  "content": { "classDef": [], "relationship_rule": {} },
  "resolutions": {
    "$.classDef[className=Order].showName": "incoming"
  }
}
```

Allowed conflict choices are `current`, `incoming`, or a manually supplied JSON object. Unresolved conflicts are rejected; the successful import is always appended as a new active revision. A stale base revision is rejected instead of overwriting a newer change.

## Restore a historical revision

```http
POST /admin/ontology/revisions/restore
Content-Type: application/json
```

```json
{
  "targetRevision": 1,
  "expectedCurrentRevision": 4,
  "expectedCurrentHash": "sha256..."
}
```

Restore creates a new revision containing the selected historical definition. It does not delete history and does not modify visual graph layout.

## Stable error codes

| Code | Meaning |
| --- | --- |
| `ONTOLOGY_REVISION_INVALID_REQUEST` | Missing or malformed revision/import input. |
| `ONTOLOGY_REVISION_NOT_FOUND` | The requested revision is not in the authenticated domain. |
| `ONTOLOGY_REVISION_STALE` | The active revision/hash changed since the operation was opened. |
| `ONTOLOGY_IMPORT_CONFLICT` | Import still contains unresolved semantic conflicts. |
| `ONTOLOGY_REVISION_INTERNAL` | Unexpected server failure; details are kept in server logs. |

The server logs endpoint context for unexpected failures, but never logs ontology request bodies, revision content, conflict values, or business rows.
