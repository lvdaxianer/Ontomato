---
name: ontomato-adapter-builder
description: Build, test and expose a new Ontomato data adapter. Create the Maven module and DataAdapterProvider, implement the DataAdapter read and write paths against the real DSL and response contract, add the shared contract-suite test and provider test, and make the adapter configurable in the existing frontend without writing Vue code. Use when adding a database, search engine, graph store or proprietary store; completing a read-only adapter's write path; fixing dropped or truncated DSL results; or changing namespaces, edges, time values, vectors or connection fields.
---

# Ontomato adapter builder

An adapter is the only layer that knows how one data source stores and queries objects. Everything
above it speaks DSL; everything below it speaks the engine's native language. This guide gives the
exact contracts so a general-purpose coding agent can implement, test and expose an adapter without
reading the existing adapters first.

## Non-negotiables

1. **Never silently drop, ignore or truncate a DSL construct.** Render it faithfully or throw a
   located `DslContractViolationException`. A query that ran without an unrenderable condition is a
   wrong answer that looks correct.
2. **The planner stays adapter-blind.** Do not put adapter capabilities into prompts. Enforce
   differences in the adapter (throw) or declare them to the test suite (capabilities).
3. **Compiling is not done.** The shared contract suite must pass against a real engine, and the
   run counts must be recorded. A skipped engine is not a pass.
4. **The application pom decides what is installed.** A module in the root pom only builds; a
   dependency in `apps/data-engine/pom.xml` makes the type appear in a deployment.
5. **One edge representation per engine.** A key field on one side or a real relationship. Never
   let the read and write paths disagree.
6. **No per-adapter frontend code.** The configuration page is metadata-driven from the provider.
   If you find yourself editing Vue for one adapter, the provider contract is wrong.
7. **One module owns one engine family.** Copy the closest adapter module; do not extract a shared
   base class across modules. A dialect difference that leaks into a shared base will eventually
   break an unrelated engine.

## 1. Contract map

| File | Role |
| --- | --- |
| `packages/data-engine-core/src/main/java/io/ontomato/dataengine/dataAdapter/DataAdapter.java` | The interface every adapter implements |
| `.../dataAdapter/DataAdapterProvider.java` | One installed type: metadata + factory |
| `.../dataAdapter/DataAdapterRegistry.java` | Collects providers by `type()`, rejects duplicates |
| `.../dataAdapter/DataAdapterConnection.java` | Saved connection: `url`, `company`, `user`, `password` |
| `.../dataAdapter/DslContractViolationException.java` | Located, machine-readable rejection |
| `.../dataAdapter/DslRowLimit.java` | Default row cap and truncation reporting |
| `.../dataAdapter/UnsupportedDataAdapterOperationException.java` | Read-only/unsupported operation |
| `.../dataAdapter/ConfigurationProblem.java` | Located ontomap/configuration problem |
| `packages/data-adapter-test-support/.../AbstractDataAdapterTest.java` | Shared contract suite |
| `.../DataAdapterCapability.java` | Capability gates for the suite |
| `packages/data-adapters/<engine>/` | The adapter module |
| `apps/data-engine/pom.xml` | The installed-adapter list |
| `packages/workbench-ui/src/features/system/components/business-config/DataAdapterConfigCard.vue` | The metadata-driven configuration card |

## 2. Module and registration

Create `packages/data-adapters/<engine>/`:

```text
pom.xml
src/main/java/io/ontomato/dataengine/dataAdapter/
  <Engine>Adapter.java
  <Engine>AdapterProvider.java
src/test/java/io/ontomato/dataengine/dataAdapter/
  <Engine>AdapterTest.java
  <Engine>AdapterProviderTest.java
```

Copy `packages/data-adapters/postgresql/pom.xml` and change only `artifactId` to
`data-adapter-<engine>`. Keep:

- parent: repository root, `<relativePath>../../../pom.xml</relativePath>`, version `${revision}`;
- `data-engine-core` compile dependency;
- `data-adapter-test-support` test dependency;
- the engine driver, version managed in the root pom;
- the Testcontainers module for the engine, plus `testcontainers:junit-jupiter`.

Root pom: add `<module>packages/data-adapters/<engine></module>`. Application pom: add the
`data-adapter-<engine>` dependency. A module that is not in the application pom builds but is never
installed (`gaussdb` is currently in that position in the open-source build).

Version-managed drivers already in the root pom:

| Engine | Coordinates | Version |
| --- | --- | --- |
| PostgreSQL | `org.postgresql:postgresql` | 42.7.3 |
| SQL Server | `com.microsoft.sqlserver:mssql-jdbc` | 12.8.1.jre11 |
| DuckDB | `org.duckdb:duckdb_jdbc` | 1.5.5.1 |
| Neo4j | `org.neo4j.driver:neo4j-java-driver` | 5.26.3 |
| Elasticsearch REST | `org.elasticsearch.client:elasticsearch-rest-client` | 9.5.5 |

MySQL uses the MySQL connector declared in the module/root; keep its version there if it is not in
the table.

## 3. Provider: metadata + factory

The provider is a Spring `@Component`. `DataAdapterRegistry` injects every provider and keys it by
`type()`. The registry fails fast on a duplicate type. There is no separate registration list.

```java
@Component
public class FooAdapterProvider implements DataAdapterProvider {

    @Override public String type() { return "foo"; }
    @Override public String label() { return "FooDB"; }
    @Override public boolean requiresJoinFields() { return true; }

    @Override public List<String> connectionFields() {
        return List.of("url", "user", "password");
    }

    @Override public Map<String, String> connectionExamples() {
        return Map.of("url", "jdbc:foo://host:5432/db", "user", "foo");
    }

    @Override public DataAdapter create(DataAdapterConnection connection,
            DataRagConfig config, Map<String, Object> jsonRule) {
        return new FooAdapter(connection, jsonRule);
    }
}
```

Contract details:

- `type()` is the stable value stored in `BusinessConfig.dataAdapter`. Never rename it after a
  release. Multiple `type()` values may share one adapter class (Elasticsearch/OpenSearch).
- `label()` is the user-facing name shown in the configuration select.
- `requiresJoinFields()` is `true` when relationships name `fromField`/`toField` that the engine
  joins on (relational, search); `false` for graph engines whose edges are real relationships.
- `connectionFields()` only ever contains `url`, `company`, `user`, `password`. The controller
  reads only declared fields, so an undeclared field is silently discarded.
- `connectionExamples()` supplies input hints, never secrets.
- `create(...)` must pass the caller's real `jsonRule` to the adapter. Do not cache an adapter per
  connection and do not substitute an empty rule: SQL adapters read `classDef` from `jsonRule`.
- The default `create(BusinessConfig, ...)` picks the connection saved under `type()`. Override it
  only for a type that needs more than one connection (the multi-source adapter).

Connection fields by engine:

| Engine | `connectionFields()` |
| --- | --- |
| PostgreSQL, MySQL, SQL Server, Neo4j | `url`, `user`, `password` |
| DuckDB | `url` only |
| Elasticsearch, OpenSearch | `url`, `user`, `password` |
| M3 | `url`, `company`, `user`, `password` |
| Multi-source | empty; it overrides `create(BusinessConfig, ...)` |

### Provider test

Every provider gets a small test that pins the metadata the frontend consumes:

```java
class FooAdapterProviderTest {
    private final FooAdapterProvider provider = new FooAdapterProvider();

    @Test void metadata() {
        assertEquals("foo", provider.type());
        assertEquals("FooDB", provider.label());
        assertTrue(provider.requiresJoinFields());
        assertEquals(List.of("url", "user", "password"), provider.connectionFields());
        assertEquals("jdbc:foo://host:5432/db", provider.connectionExamples().get("url"));
    }
}
```

## 4. The `DataAdapter` interface

Implement every method. A read-only adapter throws `UnsupportedDataAdapterOperationException` for
every write method; it must not leave an empty method that reports success.

### Connection, metadata and limits

| Method | Contract |
| --- | --- |
| `Connection openConnection() throws SQLException` | JDBC connection. Non-JDBC adapters throw `UnsupportedDataAdapterOperationException`. |
| `JSONObject testConnection() throws Exception` | Probe without touching saved config. JDBC default uses `DataAdapterConnectionTester`; non-JDBC overrides with a native probe. |
| `boolean timeConditionsAsEpochMillis()` | Default `false`: pass the DSL's time value through as a literal. Override only when the store requires epoch millis (M3). |
| `TimeZone timeConditionZone()` | Zone used only when the previous method is `true`; `null` = deployment zone. |
| `String getSampleDataFileName(...)` | Sample-data support, or throw unsupported. |
| `String querySampleDataByClassName(...)` | Sample-data support, or throw unsupported. |
| `void validateClassNameFormat(String)` | Reject names the engine cannot quote/case-fold. |
| `void validatePrimaryKeyName(String)` | Reject an attribute the engine cannot key on. The admin layer already enforces exactly one `varchar` primary key; most adapters no-op this. |
| `String sandboxClassName(String sandboxId, String className)` | The physical class/table name under a sandbox. Relational: `sandboxId + className`; M3: `/sandbox/Class`. |
| `String sandboxObjectId(String sandboxId, String objectId)` | M3 prefixes ids; relational returns the id unchanged. |
| `List<ConfigurationProblem> validateOntoMap(Map<String,Object> jsonRule)` | Default empty. Only a composite adapter imposes ontomap constraints. |

### Query

| Method | Contract |
| --- | --- |
| `String executeDsl(String queryJson, String sessionId, DslExecutionMode mode, List<String> generatedMqls)` | Translate and run one DSL. Append the generated native query to `generatedMqls`. |
| `Map<String,Object> query(JSONObject dsl, String sandboxId, String domainId, VectorResourceDao vectorResourceDao, DataRagConfig dataRagConfig)` | Object-graph path used by higher layers. Same semantics as `executeDsl`. |
| `JSONArray queryVectorAttr(Map<String,Object> classDef, String attrName, String objectId)` | Read one vector attribute; `IllegalArgumentException` when the object is missing. |

### Write

| Method | Contract |
| --- | --- |
| `createNamespace`, `truncateNamespace`, `dropNamespace` | Create the schema/prefix; clear data; remove schema and data. Verify the engine's response. |
| `createClass(String namespace, Map<String,Object> classDef)` | Create the table/collection/index from `classDef.className` + `classDef.attrs`. |
| `createEdgeType(String namespace, String edgeName)` | Only meaningful for relationship-edge engines; no-op for key-field engines. |
| `insertObjects(String namespace, Map<String,Object> classDef, JSONArray objs)` | Write only declared, enabled attributes. |
| `updateObjects(String namespace, Map<String,Object> classDef, JSONObject setValues, JSONObject where)` | Reject primary-key changes and unknown fields. |
| `deleteObjects(String namespace, Map<String,Object> classDef, JSONObject where)` | No condition clears the class. |
| `createEdge`, `deleteEdge` | Key-field engines update the side named by `fromField`; graph engines create/delete a real relationship. |

## 5. Read path: the exact DSL and response

### Request envelope

`queryJson` is a JSON **array** of DSL documents. Take element 0. The DSL is under `answer`:

```json
[
  {
    "answer": {
      "steps": [
        {
          "graph": {
            "patterns": [
              {
                "objects": [
                  {
                    "class": "person",
                    "variable": "p",
                    "idx": 0,
                    "conditions": {
                      "properties": {
                        "field": "age",
                        "operator": ">",
                        "value": 18
                      }
                    }
                  },
                  {
                    "class": "org",
                    "variable": "o",
                    "idx": 1
                  }
                ],
                "relationship": [
                  { "from": 0, "to": 1, "type": ["works_at"] }
                ]
              }
            ]
          },
          "output": {
            "fields": [
              { "variable": "p", "field": "name", "as": "name" },
              { "variable": "o", "field": "id", "function": "count", "as": "orgs" }
            ],
            "group_by": {
              "fields": [{ "variable": "p", "field": "name" }],
              "having": [
                { "variable": "o", "field": "id", "function": "count", "operator": ">", "value": 1 }
              ]
            },
            "sort": { "fields": [{ "field": "orgs", "order": "desc" }] },
            "limit": { "count": 100, "offset": 0 }
          }
        }
      ]
    }
  }
]
```

Rules that follow from this shape:

- `objects[].class` is the table/collection name the read path resolves. `variable` is the alias.
  `idx` identifies the object for relationship endpoints.
- `relationship[].from` and `to` are `idx` values, not class names. `type` is an array; the first
  entry is the relation name. `type:["*"]` means any type in the graph engines.
- An omitted `relationship` means a single-object query.
- A two-step DSL has `steps[1]`. Step 0 projects; step 1 aggregates over those projected columns.
  SQL wraps step 0 as a subquery; Neo4j chains `WITH` then `RETURN`; ES materializes.

### Conditions

The condition tree lives at `conditions.properties`:

```json
{ "and": [ {"field":"age","operator":">","value":18},
           {"or": [ {"field":"name","operator":"like","value":"Ada"},
                    {"field":"name","operator":"is","value":null} ]} ] }
```

- `and` / `or` / `not` are arrays of condition objects.
- A leaf is `{field, operator, value}`. `field` is mandatory; a condition keyed by field name is
  invalid DSL and must throw `INVALID_GRAMMAR`.
- Operators are exactly: `=`, `!=`, `like`, `not like`, `>`, `>=`, `<=`, `<`, `between`, `in`,
  `is`, `is not`.
- `conditions.timeseries` is a construct this adapter does not implement (unless the engine does,
  as M3 does). Throw `UNSUPPORTED_CONSTRUCT` before walking the property tree.
- `conditions.text.query` is a full-text shortcut over every enabled string attribute. Implement
  it or reject it; never ignore it.
- Type-directed rendering: string operators need quoting; numeric operators need no quotes;
  date/timestamp operators go through the engine's time literal helper. A (operator, type) pair the
  adapter cannot render is `UNSUPPORTED_OPERATOR_FOR_TYPE` naming the operator, field and type.

### Output

- `output.fields[]` entries: `variable`, `field`, `function` (optional), `distinct` (optional),
  `as` (the result alias). A field without `variable` is an expression in step 1.
- `output.group_by.fields[]` uses `{variable, field}`; `having[]` uses `{variable, field,
  function, operator, value}`.
- `output.sort.fields[]` uses `{field, order}`; `order` is `desc` or omitted/ascending.
- `output.limit` is `{count, offset}`. It is not an integer.
- `distinct` applies to the field it is set on; only the first distinct field in a projection is
  rendered as `distinct`.

### Sample data

If the adapter claims `SAMPLE_DATA`, implement both methods against the same convention:

- `getSampleDataFileName(sampleDataDirName, className)` returns
  `conf/<sampleDataDirName>/<className>.json` (a path relative to the deployment).
- `querySampleDataByClassName(className, attrs)` selects the requested attributes from the class
  with `limit 1`, returns the row as a JSON string, and truncates any string value longer than 100
  characters to `first 100 + "..."`. An empty class returns `<className> class has no data`.

If the engine cannot support it, do not declare `SAMPLE_DATA`.

### Response envelope

`executeDsl` returns a JSON **array**:

```json
[
  {
    "answer": [ { "name": "Ada", "orgs": 2 } ],
    "mqls": [ "select ..." ]
  }
]
```

When the default row cap truncated the result, the same answer object also carries:

```json
{ "truncated": true, "rowLimit": 20000, "truncationHint": "The result was capped ..." }
```

Serialize with `WriteMapNullValue` so null attributes survive. Do not return `{"data": ...}` and do
not return a bare array of rows.

### Row limit

Use `DslRowLimit`, do not hard-code 20000:

1. If the terminal step has no `output.limit`, apply `DslRowLimit.defaultRowLimit()` and record
   `tracker.terminalLimit(cap, true)`; query `cap + 1` rows.
2. If the result has more than `cap` rows, remove the probe row and set `truncated`, `rowLimit`
   and `truncationHint` on the answer, and log a WARN.
3. If `output.limit` exists, apply `count`/`offset` and `tracker.terminalLimit(count, false)`.
   Only a defaulted cap can be reported as truncation.
4. Cap only the terminal step. An inner materialization is bounded by the adapter's own budget,
   not by the DSL row cap.

### Time values

- Default: pass the DSL's temporal value through as a literal, formatted in the deployment zone.
  Do not convert to epoch millis unless the engine requires it.
- SQL: format `date` as `yyyy-MM-dd`, `timestamp` as `yyyy-MM-dd HH:mm:ss`.
- Read temporal columns back in the source's textual form (`yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss`)
  so a returned value can feed straight back as a condition.
- Override `timeConditionsAsEpochMillis()`/`timeConditionZone()` only for an engine whose store
  language requires millis (M3). Never reinterpret an existing customer's stored convention by
  guessing.

### Contract errors

```java
throw new DslContractViolationException(
    DslContractViolationException.Code.UNSUPPORTED_OPERATOR_FOR_TYPE,
    "steps[0].graph.patterns[0].objects[0].conditions.properties",
    "operator 'like' on field 'age' of type 'int' is not supported by FooAdapter");
```

Codes: `INVALID_GRAMMAR`, `UNSUPPORTED_CONSTRUCT`, `UNSUPPORTED_OPERATOR_FOR_TYPE`,
`UNSUPPORTED_JOIN_ARITY`. The `dslPath` must point at the offending node.

Adapters commonly wrap translation in `catch (Exception)`. Put
`catch (DslContractViolationException e) { throw e; }` first in every such block, or the violation
is flattened into a generic conversion error and the planner cannot retry.

## 5b. The legacy `query()` path

`query()` is not a synonym for `executeDsl()`. It is the object-graph entry used by the planner
and it has its own pipeline. Implement it as follows, in this order:

1. Wrap the single DSL in a JSON array and serialize with `WriteMapNullValue`:
   `JSONArray.of(dsl)`.
2. `DslUtil.normalizeClassNames(dslStr, classDefs)` — resolves the DSL's class names against the
   domain's `classDef` list.
3. `DslUtil.setConditionTimeColumnToCorrectFormat(dslStr, dataRagConfig.getDslMightTimeFormats(),
   classDefs, timeConditionsAsEpochMillis(), timeConditionZone())` — the only place time conditions
   are normalized for this path. It is why an adapter must not convert again in `query()`.
4. If `sandboxId` is non-empty, prefix every `classDef.className` with the sandbox id **and**
   rewrite every `steps[0].graph.patterns[0].objects[].class` with the same prefix
   (`addSandboxId` behavior). Do not mutate the caller's original map; copy it.
5. Call `HttpRequestUtil.executeDsl(dataRagConfig, this, dslStr, null, true, jsonRule,
   vectorResourceDao, userDataPermission, DslExecutionMode.OPTIMIZED, domainId)[1]` to get a
   `DslExecutionResult`.
6. Put `result.data()` into the returned map. If `result.failed()`, put
   `error = MultiThreadAIChatService.rawErrorText(result.failure().cause())` instead of throwing.

`query()` therefore has **two** outcomes: a data map or a data map with `error`. `executeDsl()`
throws. Do not collapse them.

The `mode` argument of `executeDsl` selects the execution path (`OPTIMIZED` for `query()`);
`sessionId` is for log correlation; `generatedMqls` is an output list the adapter appends to.

## 6. Write path: `jsonRule`, objects and edges

`jsonRule` is the domain ontomap:

```json
{
  "classDef": [
    {
      "className": "person",
      "attrs": [
        { "name": "id", "type": "varchar", "primaryKey": true, "enable": true },
        { "name": "name", "type": "varchar", "enable": true },
        { "name": "age", "type": "int", "enable": true },
        { "name": "bio", "type": "text", "enable": true },
        { "name": "org_id", "type": "varchar", "enable": true }
      ]
    }
  ],
  "relationship_rule": {
    "works_at": { "fromclass": "person", "toclass": "org", "fromField": "org_id", "toField": "id" }
  }
}
```

- `classDef[].className` is the plain class name. The read path resolves physical names through
  the namespace/sandbox; the write path looks up `classDef` by the plain name. Do not pass a
  prefixed name to a write lookup.
- `attrs[]` uses `name`, `type`, `primaryKey`, `enable`. Types are `varchar`, `text`, `int`,
  `long`, `double`, `date`, `timestamp`, `vector` (and the engine's exact scalar names where the
  semantic layer allows them).
- `relationship_rule` is keyed by the relation name; each entry has `fromclass`, `toclass`, and,
  for key-field engines, `fromField`/`toField`. Graph engines ignore the fields and must not fail
  when they are absent.

### Namespace is a table-name prefix, not a schema

This is the single most common wrong assumption. In the relational, search and graph adapters,
**namespace is a prefix prepended to the class/table name in the current schema or database**. It
is not a schema, keyspace or database that the adapter creates.

- `createNamespace` is a **no-op** for PostgreSQL, MySQL, SQL Server, DuckDB, Elasticsearch and
  Neo4j. Do not create a schema or database.
- `truncateNamespace` reads the current schema (`SELECT current_schema()`, `SELECT DATABASE()`, or
  `SELECT SCHEMA_NAME()`), lists `information_schema.tables` where `table_type = 'BASE TABLE'`, and
  deletes rows from every table whose lower-cased name starts with the lower-cased namespace.
- `dropNamespace` does the same enumeration and drops those tables.
- `createClass` computes `fullClassName = namespace + classDef.className` **even outside a
  sandbox**. `insertObjects`, `updateObjects`, `deleteObjects`, `createEdge` and `deleteEdge` use
  the same rule.
- `sandboxClassName`/`sandboxObjectId` are the single source of truth for those names. Do not
  duplicate the prefix rule in `createClass`, `insertObjects` and the read path.
- For M3 the namespace is a path segment and the parent must exist; for ES it is an index prefix;
  for Neo4j it is applied through the label/naming helper.

Type maps differ by dialect and are part of the contract, not an implementation detail:

| DSL type | PostgreSQL | MySQL | SQL Server | DuckDB |
| --- | --- | --- | --- | --- |
| `varchar` | `varchar(200)` | `varchar(255)` | `nvarchar(255)` | `varchar` |
| `text` | `text` | `longtext` | `nvarchar(max)` | `varchar` |
| `int` | `int4` | `int` | `int` | `integer` |
| `long` | `int8` | `bigint` | `bigint` | `bigint` |
| `double` | `float8` | `double` | `float` | `double` |
| `date` | `date` | `date` | `date` | `date` |
| `timestamp` | `timestamp` | `datetime` | `datetime2` | `timestamp` |
| `vector` | `text` | `longtext` | `nvarchar(max)` | `varchar` |

### Objects: exact recipes

**`createClass`**

1. `fullClassName = namespace + classDef.className`.
2. Iterate `classDef.attrs`; skip `enable == false`; skip types outside the engine's supported set;
   map each type with the dialect table above.
3. Emit `create table <fullClassName> (<field> <type>, ...)`.
4. Vector is stored as text in every SQL adapter.

**`insertObjects`**

1. `inSandbox = namespace != null && !namespace.isBlank()`;
   `fullClassName = inSandbox ? namespace + classDef.className : classDef.className`.
2. Build the field list from **enabled** attrs with supported types; record the index of the
   `primaryKey` attribute.
3. If there is no primary key, throw. If an object's primary-key value is null/blank, throw:
   `The insert statement must contain the primary key [<pk>] field`.
4. Write only those fields; `null` stays `null`; strings escape single quotes; date/timestamp go
   through `toSqlValue` with `yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss`.
5. Execute the batch; surface the engine's error, do not swallow it.

**`updateObjects`**

1. Same `inSandbox`/`fullClassName` rule.
2. Reject a `setValues` key that is the primary key, and any key not in the enabled attrs.
3. Build the WHERE with the same condition grammar as the read path (`generateWhere` /
   `buildWhere`), using the attribute type map so literals are quoted correctly.
4. Date/timestamp use the same two formats; no epoch conversion.
5. A zero-row update is a valid outcome; do not turn it into an error unless the calling contract
   says so.

**`deleteObjects`**

1. Same `inSandbox`/`fullClassName` rule.
2. `where == null` or empty means delete all rows in the class.
3. Otherwise build the WHERE with the same condition grammar.

**`createEdge` / `deleteEdge`**

1. Resolve the rule by relation name from `relationship_rule`.
2. `createEdge` updates the source table row identified by `sourceObjId`, setting `fromField` to
   `targetObjId`; `deleteEdge` clears that field scoped to the exact pair.
3. The update predicate is the source primary key, not `toField`; use the rule's field names.
4. Verify the affected-row count where the driver reports it. A delete of a non-existent edge is
   idempotent; a create of an edge into a missing row is a lost write and should surface.

### Edges

**Key-field engines** (relational, search):

- A relationship is stored as a field on the source side (`fromField`) pointing at the target's
  key (`toField`).
- `createEdge`: update the source row identified by the source id, setting `fromField` to the
  target id (or update the side the schema names).
- `deleteEdge`: clear that field, scoped to the exact pair.
- `requiresJoinFields()` is `true`; do not declare `RELATIONSHIP_EDGES`.

**Relationship-edge engines** (graph):

- `createEdge` creates a real edge; `deleteEdge` deletes it.
- `fromField`/`toField` are ignored; a relationship definition without them is valid.
- `requiresJoinFields()` is `false`; declare `RELATIONSHIP_EDGES`; override `seedEdges()` in the
  test.

### Vectors

A vector attribute is a text column holding `[{"path":"...","text":"..."},...]`. The ordinary
insert/update path does not write it; `appendVector`/`updateVector`/`deleteVector` call
`queryVectorAttr` and write the text back. Keep the JSON array format exact; null/blank reads as
an empty array.

## 7. Non-JDBC engines

### Elasticsearch and OpenSearch

One adapter, two types, one dialect object. `EsAdapterProvider` is abstract with
`ElasticsearchAdapterProvider` and `OpensearchAdapterProvider` subclasses pinning the dialect.
`EsDialect` owns every difference:

| Concern | Elasticsearch | OpenSearch |
| --- | --- | --- |
| Open PIT | `POST /<index>/_pit` | `POST /<index>/_search/point_in_time` |
| PIT id key | `id` | `pit_id` |
| Close PIT | `DELETE /_pit` body `{"id": ...}` | `DELETE /_search/point_in_time` body `{"pit_id": [...]}` |
| Sort tiebreaker | `_shard_doc`, or a keyword primary key when `_shard_doc` is unavailable | keyword primary key (`_shard_doc` is rejected on older versions) |

Implementation rules:

- `index.max_result_window` (default 10000) is below the engine row cap. Use PIT +
  `search_after`; never `from`/`size` for deep paging.
- `_shard_doc` is the official tiebreaker but is absent on older ES (7.10.2 is a known case).
  Probe once per cluster URL + dialect; on the exact "No mapping found for [_shard_doc]" error,
  fall back to a sortable `keyword` primary key and cache the capability. Do not retry every 400.
- Bound join materialization with `ontomato.es.maxMaterialize` (default 100000) and throw a clear
  error when exceeded. Prefer `terms` pushdown for a small side; use a bounded application-side
  hash join only as a fallback; stream aggregation.
- A multi-hop/variable-length relationship is out of scope for the first version; one hop is.
- `truncateNamespace`/`dropNamespace` enumerate indices by prefix and delete them; do not assume
  the engine has schemas.
- `createClass` creates an index with explicit mappings; keyword fields for ids and join keys.

#### Concrete PIT / fallback algorithm

1. Resolve the class primary key from `classDef`; verify it is a `keyword` field with
   `_field_caps`. If it is not, a `_shard_doc` failure has no safe fallback — throw a clear error.
2. Open a PIT for the index with `keep_alive=1m`; use `EsDialect.openPitPath` and `parsePitId`.
3. First page: `sort = EsDialect.pitSort(null)` (ES `_shard_doc`; OpenSearch primary key).
4. On `HTTP 400` whose body contains exactly `No mapping found for [_shard_doc]`, mark the cluster
   URL + dialect as `_shard_doc`-less, **reopen the PIT**, and retry the same page with
   `EsDialect.pitSort(primaryKey)`. Do not rethrow the probe error.
5. Any other 400 is a real error: rethrow with the body.
6. Cache the capability in a static `ConcurrentHashMap` keyed by URL + dialect; a later query skips
   the failing probe.
7. Walk pages with `search_after`; stop at the requested size or an empty page; always close the
   PIT in `finally`.

#### Join budget pipeline

1. Single object: one `_search` with the condition `bool`.
2. Before a join, `_count` each side to estimate cardinality.
3. Order joins small-side-first; build a hash set on the small side.
4. If the small side fits `index.max_terms_count` (default 65536), push `terms` into the large side
   and avoid materializing it. Otherwise use the bounded application-side hash join.
5. The materialized row budget is `ontomato.es.maxMaterialize` (default 100000). Exceeding it is a
   clear error, never an OOM.
6. Aggregations stream: hold only the grouping key and the accumulator, not all rows.
7. A multi-hop relationship is one hop in v1; anything else is `UNSUPPORTED_CONSTRUCT`.

### Neo4j

- `openConnection()` throws; `testConnection()` runs `RETURN 1` and `CALL db.labels()` and returns
  a catalog report (`kind`, `databaseProduct`, `catalog`, `schema`, `tables`, `elapsedMs`).
- Classes map to node labels, objects to nodes, relationships to real edges.
- Read path: each object becomes a node pattern; each relationship becomes a directed path
  fragment; a star sharing a center uses the same variable. `type:["*"]` is any type; multiple
  types use `-[:a|b*min..max]->`; `max_hops = -1` is capped at 10.
- Two steps: step 0 emits `MATCH ... WHERE ... WITH <projected aliases>`, step 1 emits
  `RETURN <aggregation> [ORDER BY] [SKIP/LIMIT]`. Using `RETURN` in step 0 loses the projection.
- Bound materialization (`ontomato.neo4j.maxMaterialize`, default 100000); throw rather than load
  a huge result into heap.
- `insertObjects` writes only `classDef` attributes; there is no separate edge column.
- Capabilities: `FULL` minus `JDBC_CONNECTION`, plus `RELATIONSHIP_EDGES` (and `SAMPLE_DATA`,
  `EDGE_CRUD`, `GRAPH_QUERY`, `VECTOR_ATTR`).
- Relationship details: `type` may contain several names. Render one type as `-[:a]->`, several as
  `-[:a|b]->`, and `["*"]` as `-[]->` (any type). `min_hops`/`max_hops` default to 1; `max_hops =
  -1` becomes `*min..10` (`MAX_HOPS = 10`).
- A star pattern shares the center variable: the first occurrence carries the label, later
  occurrences reuse the variable without repeating it.
- Labels: the class name is the label; the namespace is applied through the sandbox naming helper,
  not as a `_ns` property. Do not invent a property-based namespace.
- Two-step: step 0 projects aliases with `WITH`; step 1 aggregates with `RETURN`. In step 1, a field
  whose `variable` equals the intermediate object name has that prefix stripped before rendering.

### M3

- Class names are paths (`/<ns>/<class>`); the parent namespace must exist before `createClass`.
- Reads require `output.to_user = true`; otherwise the response is `status: ok` with `answer: null`.
- Object ids are namespace-prefixed on read; normalize in the test.
- `dropNamespace` and `createEdge` can report success while doing nothing (a drop with data, or an
  edge into an undeclared relation field). Read back and treat a mismatch as a defect.
- `timeConditionsAsEpochMillis()` is `true`; define the zone.
- Capabilities: `FULL` minus `JDBC_CONNECTION` and `EDGE_CRUD`; `sourceRendersTimeseries()` is
  `true`.
- The row cap must still be applied and reported.

## 8. Unit tests: the shared contract suite

Every adapter test extends `AbstractDataAdapterTest`. Supply the environment and the fixture; the
suite supplies the semantics.

```java
class FooAdapterTest extends AbstractDataAdapterTest {

    private static final String EXTERNAL_URL = System.getenv("ONTOMATO_TEST_FOO_URL");
    private static String url;
    private final String ns = "it_" + System.nanoTime() + "_";

    @BeforeAll
    static void startDatabase() {
        if (EXTERNAL_URL != null) {
            url = EXTERNAL_URL;
            return;
        }
        assumeTrue(dockerAvailable(),
                "Docker is not running and ONTOMATO_TEST_FOO_URL is not set; skipping Foo contract tests");
        // start the engine's Testcontainers container, assign url/user/password
    }

    @Override protected DataAdapter newAdapter(JSONObject jsonRule) {
        DataAdapterConnection c = new DataAdapterConnection();
        c.setUrl(url);
        c.setUser(user);
        c.setPassword(password);
        return new FooAdapter(c, jsonRule);
    }

    @Override protected Set<DataAdapterCapability> capabilities() {
        return DataAdapterCapability.FULL;
    }

    @Override protected String namespace() { return ns; }
    @Override protected String primaryKey() { return "id"; }

    @Override protected JSONObject classDef() {
        JSONObject def = new JSONObject();
        def.put("className", "person");
        JSONArray attrs = new JSONArray();
        attrs.add(attr("id", "varchar", true));
        attrs.add(attr("name", "varchar", false));
        attrs.add(attr("bio", "text", false));
        attrs.add(attr("age", "int", false));
        attrs.add(attr("score", "long", false));
        attrs.add(attr("height", "double", false));
        attrs.add(attr("birthday", "date", false));
        attrs.add(attr("created_at", "timestamp", false));
        def.put("attrs", attrs);
        return def;
    }

    @Override protected JSONArray testObjects() { return /* five rows */; }
    @Override protected List<JSONObject> graphClassDefs() { return /* three classes */; }
    @Override protected JSONObject relationshipRule() { return /* relation -> fromclass/toclass/fromField/toField */; }
    @Override protected Map<String, JSONArray> graphSeed() { return /* per class rows */; }
    // Relationship-edge adapter only:
    @Override protected List<SeedEdge> seedEdges() {
        return List.of(new SeedEdge("works_at", 0, "p1", 1, "c1"));
    }
}
```

Fixture contract:

- `namespace()` must be unique per test instance (`"it_" + System.nanoTime() + "_"`), not a fixed
  literal. The suite creates and drops its own tables.
- `classDef()` uses `className` + `attrs`; one primary key (`varchar`) and one attribute of every
  scalar the adapter supports. `attr(name, type, primaryKey)` is a suite helper.
- `testObjects()` must include a null string, a quoted string, every scalar type, a date and a
  timestamp.
- `graphClassDefs()` is three classes in `person → org → project` order. `graphSeed()` carries
  populated join fields for key-field adapters. Relationship-edge adapters return
  `relationshipRule()` without `fromField`/`toField` and override `seedEdges()`.
- `graphClassDefsWithTime()`, `firstRelation()`, `secondRelation()`, `graphRule()`,
  `prefixedGraphRule()` and `seedGraph()` are provided by the suite; do not duplicate them.
- Missing Docker/external URL is a **skip**, never a failure.

Capability sets:

| Kind | Capabilities |
| --- | --- |
| Full relational | `DataAdapterCapability.FULL` |
| Read-only relational | `READ_ONLY` (`JDBC_CONNECTION`, `READ_QUERY`, `SAMPLE_DATA`) |
| Neo4j | `FULL` − `JDBC_CONNECTION` + `RELATIONSHIP_EDGES` |
| Elasticsearch/OpenSearch | `FULL` − `JDBC_CONNECTION` (no `RELATIONSHIP_EDGES`) |
| M3 | `FULL` − `JDBC_CONNECTION` − `EDGE_CRUD`; `sourceRendersTimeseries() = true` |

Add adapter-specific tests outside the shared suite for protocol behavior, dialect differences,
scale bounds and capability probing. The suite proves semantics; it does not prove your protocol.

### Test environment matrix

| Engine | External URL override | Container image |
| --- | --- | --- |
| PostgreSQL | `ONTOMATO_TEST_PG_URL`, `_USER`, `_PASSWORD` | `postgres:16-alpine` |
| MySQL | `ONTOMATO_TEST_MYSQL_URL`, `_USER`, `_PASSWORD` | MySQL 8 |
| SQL Server | `ONTOMATO_TEST_MSSQL_URL`, `_USER`, `_PASSWORD` | `mcr.microsoft.com/mssql/server` |
| DuckDB | embedded file; no external URL | none |
| Neo4j | `ONTOMATO_TEST_NEO4J_URL`, `_USER`, `_PASSWORD` | `neo4j:5.26.0` |
| Elasticsearch | `ONTOMATO_TEST_ES_URL`, `_USER`, `_PASSWORD` | `ONTOMATO_TEST_ES_IMAGE` (default ES 9.x) |
| OpenSearch | same ES variables + `ONTOMATO_TEST_ES_DIALECT=opensearch` | OpenSearch 2.19 |

The JDBC URL for SQL Server testing needs
`encrypt=false;trustServerCertificate=true`. DuckDB uses `jdbc:duckdb:/absolute/path/file.duckdb`
(a bare path fails with `No suitable driver`).

Commands:

```bash
# one adapter with its dependencies
./mvnw -pl packages/data-adapters/foo -am test -Dtest=FooAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# whole module
./mvnw -pl packages/data-adapters/foo -am test

# external engine
ONTOMATO_TEST_FOO_URL=... ONTOMATO_TEST_FOO_USER=... ONTOMATO_TEST_FOO_PASSWORD=... \
./mvnw -pl packages/data-adapters/foo -am test -Dtest=FooAdapterTest
```

The enterprise repository has no Maven wrapper; run the public wrapper with `-f` pointing at the
enterprise pom. Read `target/surefire-reports/TEST-*.xml` for counts. Report
`tests run / failures / errors / skipped`; explain every skip.

## 9. Frontend configuration

The configuration page is entirely metadata-driven. A new adapter requires **no Vue edit**.

| Endpoint | Purpose |
| --- | --- |
| `GET /businessConfig/dataAdapters` | Returns `{currentType, adapters:[{type,label,requiresJoinFields,fields,examples}]}` |
| `POST /businessConfig/useDataAdapter` | Saves `{type,url,company,user,password}` for the selected type; only declared fields are read |
| `POST /businessConfig/testDataAdapter` | Builds a throwaway adapter from the posted values and returns `data` from `testConnection()` |

What the page does with your provider metadata:

- `BusinessConfig.vue` fills the adapter select from `label` and shows the saved type.
- `DataAdapterConfigCard.vue` renders one input per entry in `fields`, with `examples` as hints and
  `requiresJoinFields` controlling the relationship-field guidance.
- `adapterDisplay` (`labelWidth`, `urlSpan`, `credentialSpan`) is an edition-level layout prop; do
  not add per-adapter layout code.
- `testConnection()` drives the "Test connection" result panel. Return a useful report:
  `kind`, `databaseProduct`, `databaseVersion`, `driverName`, `driverVersion`, `catalog`,
  `schema`, `tables:[{name,type}]`, `elapsedMs` for catalog-style sources; the JDBC tester already
  does this for JDBC adapters.

Checklist for the UI:

- `type` is stable and lowercase; `label` is human-readable and distinct.
- `connectionFields` covers exactly what the input must collect; no hidden fields.
- `connectionExamples` are realistic and contain no credentials.
- For a search engine, the test report lists indices as `tables` so the user can see the catalog.
- For a graph engine, `CALL db.labels()` supplies the table list.
- No adapter-specific branch is added to `BusinessConfig.vue` or `DataAdapterConfigCard.vue`.

## 10. Field notes and traps

- **DSL root**: `answer.steps`, `graph.patterns[0]`, `objects[].variable`, relationship `from`/`to`
  indices, `type` array. The request is an array.
- **Response**: `[{answer, mqls, truncated?, rowLimit?, truncationHint?}]`, `WriteMapNullValue`.
- **`catch` flattening**: always rethrow `DslContractViolationException` first.
- **`information_schema` labels**: MySQL/SQL Server return `TABLE_NAME`; alias to lowercase
  `table_name` or the loop silently does nothing.
- **Read/write class naming**: read path uses the resolved physical name; write path looks up
  `classDef` by the plain class name. Mixing them silently finds nothing.
- **`like`**: the read path adds `%...%` when the value has no wildcard; the write path's `buildWhere`
  does not. Match the suite.
- **`distinct`**: only the first distinct projection field is rendered as `distinct`.
- **Row cap**: terminal step only, `cap+1` probe, report `truncated`; an explicit limit is not a
  truncation.
- **Time**: literals by default; textual read-back; epoch millis only for M3; DuckDB `INT32`
  overflow and GaussDB millis-as-seconds are known failure modes.
- **Stale test classes**: deleting a test source leaves its `.class` in `target/test-classes`;
  `rm -rf target` after moving/deleting tests.
- **Masked values**: grep for `***` after generating large Java; a masked secret-shaped literal
  becomes a compile error.
- **`-pl` without `-am`**: the parent uses `${revision}`; always `-am`.
- **`-Dtest` with `-am`**: add `-Dsurefire.failIfNoSpecifiedTests=false`.
- **Registries**: Docker Hub is unreachable in the development network; use
  `docker.m.daocloud.io` for PostgreSQL/MySQL/Neo4j, `docker.elastic.co` for Elasticsearch,
  `mcr.microsoft.com` for SQL Server. Remove containers and images after testing.
- **Long runs look dead**: an absent Surefire report immediately after a poll is not a killed
  process. Wait for completion and read the XML.
- **M3**: `to_user=true`; parent namespace first; drop/createEdge may report success without doing
  anything; ids are prefixed.
- **ES**: PIT + `search_after`; `_shard_doc` capability probe and keyword fallback; OpenSearch PIT
  path/id/close body differ; `max_result_window` is 10000.
- **Neo4j**: one directed path per relationship; `WITH` in step 0; `max_hops=-1` capped at 10.
- **Multi-source**: route from `BusinessConfig`, build delegates with the caller's real `jsonRule`,
  route step 0 only, reject cross-source joins with `UNSUPPORTED_CONSTRUCT`, rewrite physical edge
  names on read and write.

## 11. Review gate

- [ ] Module in root pom **and** application pom.
- [ ] `type()` stable; provider test asserts type/label/join-fields/fields/examples.
- [ ] Provider `create` forwards the real `jsonRule`.
- [ ] Every `DataAdapter` method implemented or throws a typed unsupported error.
- [ ] Request envelope, step shape, conditions, operators, output and response envelope match the
      contract above.
- [ ] Contract exceptions pass through `catch`; unknown operators/fields/timeseries are rejected.
- [ ] Terminal row cap uses `DslRowLimit` and reports `truncated`.
- [ ] Time values use literals by default, with textual read-back.
- [ ] One edge representation, declared through capabilities; relationship-edge adapters ignore
      `fromField`/`toField` and override `seedEdges()`.
- [ ] Write path writes only declared attributes and rejects primary-key/unknown-field updates.
- [ ] Contract suite run against a real engine; counts recorded; skips explained.
- [ ] Provider metadata is sufficient for the configuration page; no Vue change.
- [ ] Adapter-specific tests cover the protocol, dialect and bounds.
