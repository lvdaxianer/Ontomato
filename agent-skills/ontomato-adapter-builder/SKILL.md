---
name: ontomato-adapter-builder
description: Build and prove a new Ontomato data adapter. Create the Maven module and DataAdapterProvider registration, implement the DataAdapter read and write paths, translate DSL to the engine's native query language under the fail-closed execution contract, and validate it with the shared contract suite against a real engine. Use when adding support for a database, search engine, graph store or proprietary store; completing a read-only adapter's write path; fixing silently dropped or truncated DSL; or changing how namespaces, edges, time values or vector attributes are handled.
---

# Ontomato adapter builder

An adapter is the only place that knows how one data source stores and queries objects. Everything above it speaks DSL; everything below it speaks the engine's native language. Build the adapter so that boundary stays honest.

## Non-negotiables

1. **Never silently drop, ignore or truncate a DSL construct.** A valid construct is either rendered faithfully or rejected with a located `DslContractViolationException`. "I did not understand this condition, so I ran without it" is a wrong answer that looks correct.
2. **The planner stays adapter-blind.** Do not put adapter capabilities into prompts or make the ABC programmer choose per database. Capability differences are enforced in the adapter (throw) or declared to the test suite, never negotiated in natural language.
3. **Compiling is not done.** An adapter is done only after the shared contract suite passes against a real engine, with the run counts recorded.
4. **The application pom decides what is installed.** A module in the root pom only builds; a dependency in `apps/data-engine/pom.xml` is what makes the type appear in a deployment.
5. **One representation per concept.** Edges are either a key field on one side or a real relationship. Pick one, declare it through capabilities, and never let the read and write paths disagree.
6. **Do not patch around an adapter in core.** If behavior belongs to one engine, it belongs in that adapter module.

## 1. Classify the adapter first

The implementation shape follows the source's nature. Identify it before writing code:

| Kind | Examples | Read path | Write path | Template |
| --- | --- | --- | --- | --- |
| Relational, full | PostgreSQL, MySQL, SQL Server, DuckDB | DSL → SQL | DDL + DML, real tables | `PostgreAdapter` |
| Relational, read-only | GaussDB, Oracle, DB2, DM | DSL → SQL against existing tables | throw `UnsupportedDataAdapterOperationException` | `GaussDBAdapter` |
| Search engine | Elasticsearch, OpenSearch | DSL → REST search, bounded joins | index/delete documents | `EsAdapter` + `EsDialect` |
| Graph store | Neo4j | DSL patterns → Cypher paths | nodes + real relationships | `Neo4jAdapter` |
| Proprietary store | M3 | DSL → MQL / script call | MQL writes | `M3Adapter` (enterprise) |
| Composite | multiple sources | route per class, delegate | route per class, delegate | `MultiDataSourceAdapter` |

Read the closest existing adapter end to end before writing anything. The full SQL adapters are near-copies with dialect differences; the non-SQL adapters are where design decisions are made.

## 2. Module and registration

Create `packages/data-adapters/<name>/`:

```text
pom.xml
src/main/java/io/ontomato/dataengine/dataAdapter/
  <Name>Adapter.java
  <Name>AdapterProvider.java
src/test/java/io/ontomato/dataengine/dataAdapter/
  <Name>AdapterTest.java
```

Copy `packages/data-adapters/postgresql/pom.xml` and change `artifactId` to `data-adapter-<name>`. Keep `data-engine-core` as a compile dependency and `data-adapter-test-support` as test scope; add the driver and Testcontainers module as needed. The parent is the repository root with `<relativePath>../../../pom.xml</relativePath>` and version `${revision}`.

Register in three places, or the adapter is invisible:

1. Root `pom.xml` `<modules>`: `packages/data-adapters/<name>`.
2. `apps/data-engine/pom.xml` `<dependencies>`: `data-adapter-<name>`. This list alone decides which types the edition reports and offers; a root module that is not listed still builds but is never installed (gaussdb is currently in that position in the open-source build).
3. The driver, in the root pom's `dependencyManagement` if no adapter owns it yet.

The provider is a Spring `@Component`; `DataAdapterRegistry` collects all providers and fails fast on a duplicate `type()`. No manual registration list exists.

Checklist before writing the adapter:

- `type()` is the stable API value stored in `BusinessConfig.dataAdapter`; never rename it after release.
- `label()` is the user-facing name.
- `requiresJoinFields()` says whether relationships name `fromField`/`toField` that this engine joins on. Key-field engines return `true`; graph engines and search engines that join by document key return `false`.
- `connectionFields()` returns only `url`, `company`, `user`, `password`.
- `connectionExamples()` gives hints, not secrets.
- `create(...)` receives the caller's real `jsonRule`. Do not swallow it and do not build the adapter from a cached/empty rule: SQL adapters read `classDef` from `jsonRule`, so a null rule fails at the first DSL query.

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
        return Map.of("url", "jdbc:foo://host:port/db");
    }

    @Override public DataAdapter create(DataAdapterConnection connection,
            DataRagConfig config, Map<String, Object> jsonRule) {
        return new FooAdapter(connection, jsonRule);
    }
}
```

## 3. Implement `DataAdapter`

`io.ontomato.dataengine.dataAdapter.DataAdapter` has three groups of methods.

**Connection and metadata**

- `openConnection()`: JDBC connection. Non-JDBC adapters throw; do not fake a `Connection`.
- `testConnection()`: default probes JDBC. Override for non-JDBC with a real protocol probe (Bolt, HTTP root/health) and return the same result shape as `DataAdapterConnectionTester`.
- `getSampleDataFileName`, `querySampleDataByClassName`: sample-data support. Implement when the adapter owns storage; return `UnsupportedDataAdapterOperationException` when it cannot.
- `validateClassNameFormat`, `validatePrimaryKeyName`: refuse names this engine cannot quote, case-fold or key on.
- `timeConditionsAsEpochMillis()`, `timeConditionZone()`: see time handling below. Leave the defaults unless the engine truly requires epoch millis.
- `validateOntoMap(jsonRule)`: declare constraints the adapter imposes on the semantic layer, returning `ConfigurationProblem`s. Default empty is correct for a single-source adapter.
- `sandboxClassName`, `sandboxObjectId`: keep vector-table names and ids aligned with the adapter's own sandbox naming. Relational adapters prefix class names and return ids unchanged; M3 returns `/sandbox/Class` and prefixes ids.

**Query path**

- `executeDsl(queryJson, sessionId, mode, generatedMqls)`: parse DSL and run it. This is the hot path; it must be total over the DSL grammar.
- `query(dsl, sandboxId, domainId, vectorResourceDao, dataRagConfig)`: object-graph query used by higher layers; same contract.
- `queryVectorAttr(classDef, attrName, objectId)`: read one vector attribute as a `JSONArray`. Missing object is `IllegalArgumentException`.

**Write path**

- `createNamespace` / `truncateNamespace` / `dropNamespace`
- `createClass(namespace, classDef)`, `createEdgeType(namespace, edgeName)`
- `insertObjects` / `updateObjects` / `deleteObjects`
- `createEdge` / `deleteEdge`

For read-only adapters every write method throws `new UnsupportedDataAdapterOperationException(type, "createClass")` and similar. Do not leave empty methods that report success.

## 4. Read path: DSL to native

Read the DSL model before coding: `steps[]`, each with `objects[]` (class, alias, conditions), `relationships[]` (type, from/to classes, min/max hops), `output` (attributes, aggregates, aliases, group-by flags), and optional sort/limit. A two-step DSL projects in step 0 and aggregates in step 1.

Implementation rules:

1. **Preserve every construct or throw.** Walk all conditions and outputs. If an operator, type pair, nesting or join arity cannot be rendered, throw with a precise `dslPath`. Never use `if (condition != null)` as a reason to skip.
2. **Let contract exceptions escape.** Adapters commonly wrap everything in `catch (Exception e)`. Put `catch (DslContractViolationException e) { throw e; }` first in every such block, or the violation is flattened into a generic "failed to convert DSL" and tests fail with the wrong type.
3. **Fail closed on unknown constructs.** An unknown operator, a missing `field`, a `timeseries` block this adapter does not implement, or a condition keyed by field name instead of `field` must throw `INVALID_GRAMMAR`/`UNSUPPORTED_CONSTRUCT`. Do not guess.
4. **Enforce the row limit.** The engine caps a terminal step without an explicit `limit` at `DslRowLimit` (default 20000, overridable by `ontomato.dsl.defaultRowLimit`). Query `cap + 1` rows and report `truncated` / `rowLimit` rather than returning a silently short answer. Only the terminal step is capped; an inner materialization uses the adapter's own materialization budget. An explicit `limit` is not a truncation.
5. **Keep class naming consistent.** Read paths generally use the namespace-prefixed table name from the DSL; write paths use the plain class name from `classDef`. Do not mix them: a write that looks up `classDef` with a prefixed name silently finds nothing. Use the existing `graphRule()` / `prefixedGraphRule()` helpers where the template does.
6. **Escape and type-map deliberately.** Quote identifiers per dialect, escape string literals, and map DSL/scalar types to engine types in one place. `like` semantics differ between read and write paths in the SQL templates: the read path adds `%...%`; the write path does not. Match the contract suite, not intuition.
7. **Time values are literals by default.** Do not convert a date/timestamp condition to epoch millis unless the engine requires it. For SQL engines, format as `yyyy-MM-dd HH:mm:ss` in the JVM's zone; for engines that store epoch millis, override `timeConditionsAsEpochMillis()` and define the zone. Read values back in the source's own textual form so a value can be fed back as a condition. `DuckDB`'s `INT32` overflow and GaussDB's millis-as-seconds bug are the known failure modes of getting this wrong.
8. **Two steps are not one query with a different name.** Step 0 produces the projection set, step 1 aggregates it. SQL uses a nested subquery; Neo4j uses `WITH` then `RETURN`; ES materializes. Step 1's `variable` names the intermediate table and its `class` is not read by any current adapter. If your engine has no notion of a subquery, materialize with a hard budget and throw when it is exceeded.

### Violation shape

```java
throw new DslContractViolationException(
    DslContractViolationException.Code.UNSUPPORTED_OPERATOR_FOR_TYPE,
    "steps[0].objects[p].conditions",
    "operator '" + op + "' is not supported for field '" + field + "' (" + type + ")");
```

Codes are deliberately few and stable: `INVALID_GRAMMAR`, `UNSUPPORTED_CONSTRUCT`, `UNSUPPORTED_OPERATOR_FOR_TYPE`, `UNSUPPORTED_JOIN_ARITY`. Callers switch on them.

## 5. Write path

Mirror the relational template unless the storage model forbids it.

**Namespaces.** `createNamespace` creates the schema/database prefix. `truncateNamespace` removes data but keeps the schema. `dropNamespace` removes both. Verify success: several engines reject a drop while data exists and still return HTTP 200 or a success payload. `truncate` then `drop`, and surface the engine's error message if it still refuses.

**Classes.** `createClass` creates the table/collection/index from the `classDef` attribute list. Map every scalar the semantic layer allows, including `vector` as text (PostgreSQL `text`, MySQL/SQL Server `nvarchar(max)`, DuckDB `VARCHAR`). A primary-key attribute is mandatory and must be a string type; `AdminServiceImpl` validates this before the adapter sees it, but the adapter should still reject a definition it cannot key.

**Objects.** `insertObjects` writes only attributes declared by `classDef`. Do not invent columns and do not strip fields ad hoc; a relationship-only property has no column because the semantic layer does not declare it. `updateObjects` must reject changes to the primary key and unknown fields, and must translate the same condition grammar as the read path. `deleteObjects` without a condition empties the class, as the contract requires.

**Edges.** Two legal representations:

- **Key field** (relational, search): the relationship is stored as a field on one side, selected by `fromField`/`toField`. `createEdge`/`deleteEdge` update that field. `requiresJoinFields()` returns `true`. Do not declare `RELATIONSHIP_EDGES`.
- **Real relationship** (graph): `createEdge` creates an actual edge; `deleteEdge` removes it; `fromField`/`toField` are ignored, and a relationship definition without them must not fail. Declare `RELATIONSHIP_EDGES` and override `seedEdges()` in the test.

Never mix the two. The contract suite asserts edges through the read path when `RELATIONSHIP_EDGES` is declared and through the foreign-key column otherwise.

**Vectors.** A vector attribute is a text column holding a JSON array `[{"path":"...","text":"..."},...]`. The write API's insert/update deliberately excludes vector attributes; vectors arrive through `appendVector`/`updateVector`/`deleteVector`, which call `queryVectorAttr` and write back. The adapter must keep the text format exact and return an empty array for null/blank.

## 6. Non-JDBC adapters

The JDBC-shaped methods still exist; make their unsupported status explicit.

- `openConnection()` throws `UnsupportedDataAdapterOperationException`.
- `testConnection()` performs the engine's own probe and returns a meaningful reachable/version message.
- `JDBC_CONNECTION` is absent from the test capabilities; the suite skips the JDBC probe.
- Do not construct a non-JDBC adapter through `createForTest`: it passes `jsonRule = null`, and adapters that read class definitions from `jsonRule` will `NullPointerException` on their first DSL query. Build with the caller's real rule.

**Elasticsearch / OpenSearch.** One adapter, two types, dialect object (`EsDialect`) for PIT open/close, pit id field and sort tiebreaker. Constraints that shaped the implementation:

- `index.max_result_window` (default 10000) is below the engine row limit, so use PIT + `search_after`; never `from`/`size` for deep paging.
- `_shard_doc` is the correct tiebreaker but is unavailable on older ES (for example 7.10.2). Probe once per cluster URL + dialect, fall back to a sortable keyword primary key only on the exact "no mapping for `_shard_doc`" error, and cache the capability. Do not fall back on every 400.
- Joins are bounded by a materialization budget (`ontomato.es.maxMaterialize`); exceed it with a clear error instead of risking OOM. Prefer `terms` pushdown for a small side, bounded application-side hash join as the fallback, and streaming aggregation.
- OpenSearch's PIT API and sort rules differ from Elasticsearch's. Put the difference in the dialect, not in `if (isOpenSearch)` branches scattered through the adapter.

**Neo4j.** Edges are relationships. Translate each pattern into a directed path fragment; a shared center becomes a shared variable. Only the first pattern is used, matching the current planner. Cap `max_hops = -1` at a documented finite value (currently 10). Step 0 emits `MATCH ... WHERE ... WITH`, step 1 emits `RETURN ... [ORDER BY] [SKIP/LIMIT]`; using `RETURN` in step 0 loses the projection. Apply a materialization cap so a large result is rejected rather than loaded into heap.

**M3.** Class names are paths and the parent namespace must exist before a class. Reads need `output.to_user = true` or the answer is `null` with `status: ok`. Object ids are namespace-prefixed on read; normalize on the test side. `dropNamespace` and `createEdge` can report success while doing nothing (undeclared relation field); verify by reading back, and treat a mismatch as a defect rather than as test noise.

## 7. Composite adapters

Only build one when the product asks for multi-source routing. Keep it a facade: it implements `DataAdapter` and delegates, so no downstream call site changes. The hard rules learned from `MultiDataSourceAdapter`:

- Route from `BusinessConfig.multiDataSource`, not from the ontomap: `SandboxServiceImpl` constructs the adapter with `jsonRule = null`.
- Build a delegate per call with the caller's real `jsonRule`; do not cache by source id and do not use `createForTest`.
- Routing is decided by step 0's object classes. Cross-source joins are not a graph query: throw `UNSUPPORTED_CONSTRUCT` with the class-to-source mapping in the detail. Never split a join silently.
- Rewrite physical edge names on the read path as well as the write path, or a configured `realEdge` name makes graph reads return empty.
- Domain-level flags (`requiresJoinFields`, `timeConditionsAsEpochMillis`, `timeConditionZone`) must be zero-cost values; each delegate applies its own time conversion internally, so the facade must not convert twice.
- Validate configuration up front: duplicate routes, unknown sources, nested composites, uninstalled types.

## 8. Prove it with the contract suite

Every adapter test extends `AbstractDataAdapterTest`. The suite supplies the semantics; the concrete test supplies the environment and the fixture.

```java
class FooAdapterTest extends AbstractDataAdapterTest {

    @Override protected DataAdapter newAdapter(JSONObject jsonRule) {
        return new FooAdapter(connection(), jsonRule);
    }

    @Override protected Set<DataAdapterCapability> capabilities() {
        return DataAdapterCapability.FULL;           // or READ_ONLY, or a custom set
    }

    @Override protected String namespace() { return "ontomato_test_foo"; }

    @Override protected JSONObject classDef() { /* one primary key + every scalar type */ }

    @Override protected JSONArray testObjects() { /* rows used by CRUD tests */ }

    // Graph fixtures: three classes, two relationships, seed objects.
    @Override protected List<JSONObject> graphClassDefs() { ... }
    @Override protected JSONObject relationshipRule() { ... }
    @Override protected Map<String, JSONArray> graphSeed() { ... }
}
```

Capability declaration is a promise, not a convenience. `FULL` includes `JDBC_CONNECTION`, `READ_QUERY`, `SAMPLE_DATA`, `NAMESPACE_LIFECYCLE`, `CLASS_SCHEMA`, `OBJECT_CRUD`, `EDGE_CRUD`, `GRAPH_QUERY`, `VECTOR_ATTR`; it deliberately excludes `RELATIONSHIP_EDGES`. `READ_ONLY` is `JDBC_CONNECTION`, `READ_QUERY`, `SAMPLE_DATA`. Declaring `EDGE_CRUD` when the engine cannot create the relationship field makes the suite fail — correctly.

Environment contract:

- `newAdapter` decides where the engine comes from: Testcontainers, an external URL from `ONTOMATO_TEST_<ENGINE>_URL` / `_USER` / `_PASSWORD`, or an embedded file (DuckDB). Missing external configuration is a **skip**, not a failure.
- `namespace()` must be unique per run; the suite creates and drops its own tables.
- The fixture must cover every scalar type the adapter claims, a date and a timestamp column, and populated join fields.
- Keep adapter-specific tests outside the shared suite: protocol/HTTP behavior, dialect differences, scale bounds, capability probing. The suite proves semantics; it does not prove your engine adapter.

Commands (root has the Maven wrapper; the enterprise repository has no wrapper, so point `-f` at its pom):

```bash
# one adapter, with its dependencies
./mvnw -pl packages/data-adapters/foo -am test -Dtest=FooAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# whole module, including extra tests
./mvnw -pl packages/data-adapters/foo -am test

# against an external engine instead of a container
ONTOMATO_TEST_FOO_URL=jdbc:foo://127.0.0.1:1234/db \
ONTOMATO_TEST_FOO_USER=test ONTOMATO_TEST_FOO_PASSWORD=test \
./mvnw -pl packages/data-adapters/foo -am test -Dtest=FooAdapterTest
```

Read `target/surefire-reports/TEST-*.xml` for the real counts. Report `tests run / failures / errors / skipped`; a skip caused by a missing engine is not a pass.

## 9. Field notes and traps

- **`information_schema` labels.** MySQL and SQL Server return `TABLE_NAME`, not `table_name`. `getString("table_name")` returns null and the loop silently does nothing. Alias every metadata column to the exact lowercase name you read.
- **Stale test classes.** Deleting a test source does not remove its `.class` from `target/test-classes`; Surefire still runs it. `rm -rf target` after moving or deleting tests.
- **Masked environment values.** Long generated Java can contain `***` where a secret-shaped literal was masked during editing. Grep for `\*\*\*` before compiling.
- **`-pl` without `-am`.** The parent uses `${revision}`, so a single-module build cannot resolve siblings. Always `-am`; install first if you switch to `-pl` alone.
- **`-Dtest` with `-am`.** Modules without a matching test fail the reactor. Add `-Dsurefire.failIfNoSpecifiedTests=false`.
- **Container registries.** Docker Hub is unreachable in the development network; use `docker.m.daocloud.io` for PostgreSQL/MySQL/Neo4j, `docker.elastic.co` for Elasticsearch, `mcr.microsoft.com` for SQL Server (slow without VPN). Remove test containers and images afterwards; disk is limited.
- **Long runs look dead.** An absent Surefire report right after a poll does not mean the process was killed. Wait for completion and check the XML; ES image pulls and M3 suites take minutes.
- **Generated MQL/DSL debugging.** When a real environment fails, capture the exact request and response before changing code. For M3, a portal HTML response almost always means wrong auth headers, not a missing endpoint.
- **Read/write naming split.** The single most common silent bug: read path uses the prefixed class name, write path looks up the plain one. Assert both directions in a real query.
- **Version drift on the engine.** Capability probing belongs in the adapter (ES `_shard_doc`, M3 `to_user`), not in deployment notes. A supported version range stated only in documentation will be violated.

## 10. Review gate

Before declaring an adapter finished:

- [ ] Module registered in the root pom **and** the application pom.
- [ ] `type()` stable, provider has no duplicate, `create` forwards the real `jsonRule`.
- [ ] Every `DataAdapter` method is implemented or throws a typed unsupported error; no empty success.
- [ ] Read path handles every condition/output construct or throws a located violation; contract exceptions pass through `catch`.
- [ ] Terminal row limit returns `truncated` rather than a short row set.
- [ ] Time conditions are literals unless the engine requires epoch millis, with the zone defined.
- [ ] Edge representation is single and declared; relationship-edge adapters ignore `fromField`/`toField`.
- [ ] Write path verifies engine responses; no success assumed from HTTP 200 alone.
- [ ] Contract suite run against a real engine; counts recorded, skips explained.
- [ ] Adapter-specific tests cover the engine's dialect and bounds.
- [ ] README or adapter javadoc states the supported engine versions and the known limits.
