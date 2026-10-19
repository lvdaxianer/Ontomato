package io.ontomato.dataengine.dataAdapter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.config.BusinessConfig;
import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.config.multisource.DataSourceEntry;
import io.ontomato.dataengine.config.multisource.MultiDataSourceDefinition;
import io.ontomato.dataengine.config.multisource.RelationshipRoute;
import io.ontomato.dataengine.dao.VectorResourceDao;
import lombok.extern.slf4j.Slf4j;

/**
 * One domain spread over several data sources, routed by ontology name.
 *
 * <p>This adapter touches no database itself. It does exactly two things: route each call to the
 * delegate adapter of the source that owns the named class or relationship, and reject a call that
 * cannot be routed uniquely. Because it is an ordinary {@link DataAdapter}, every caller above it is
 * unchanged, and a domain that is not configured with {@code multiDataSource} never reaches it.</p>
 *
 * <p><b>What it deliberately does not do.</b> A graph query whose classes span sources is not a
 * supported construct here — the same kind of limitation as {@code timeseries} on an adapter that has
 * not implemented it — so it is rejected with a located {@link DslContractViolationException#Code#UNSUPPORTED_CONSTRUCT},
 * never silently answered with partial rows. Only the first step names classes (later steps aggregate
 * over the first step's result), so routing looks at the first step alone; the whole element is then
 * handed to that one source.</p>
 */
@Slf4j
public class MultiDataSourceAdapter implements DataAdapter {

	private final DataAdapterRegistry registry;
	private final DataRagConfig dataRagConfig;
	private final BusinessConfig businessConfig;
	private final Map<String, Object> jsonRule;

	private final MultiDataSourceDefinition definition;
	private final DataSourceRouter router;
	private final DelegatingAdapterFactory factory;

	public MultiDataSourceAdapter(DataAdapterRegistry registry, BusinessConfig businessConfig,
			DataRagConfig dataRagConfig, Map<String, Object> jsonRule) {
		this.registry = registry;
		this.businessConfig = businessConfig;
		this.dataRagConfig = dataRagConfig;
		this.jsonRule = jsonRule;
		this.definition = businessConfig == null ? null : businessConfig.getMultiDataSource();
		if (this.definition == null) {
			throw new IllegalStateException(
					"The multi data adapter needs businessConfig.multiDataSource; an unset block means the domain is single-source");
		}
		MultiDataSourceValidator.validate(this.definition, registry);
		this.router = new DataSourceRouter(this.definition);
		this.factory = new DelegatingAdapterFactory(registry, dataRagConfig, businessConfig);
	}

	// ----------------------------------------------------------------------------------
	// Routing
	// ----------------------------------------------------------------------------------

	/**
	 * The one source a configuration-time class name belongs to.
	 *
	 * <p>When a single source is configured, an unrouted name falls back to it: there is no ambiguity to
	 * resolve, and this keeps a one-source {@code multi} domain behaving exactly like that source alone.</p>
	 *
	 * @throws IllegalArgumentException when the class cannot be routed
	 */
	private String configSourceOfClass(String className) {
		String sourceId = resolveSourceOfClass(className);
		if (sourceId == null) {
			throw new IllegalArgumentException("The class `" + className
					+ "` is not routed to any data source in this domain's multi-source configuration");
		}
		return sourceId;
	}

	private String resolveSourceOfClass(String className) {
		String sourceId = router.sourceOfClass(className);
		return sourceId != null ? sourceId : singleConfiguredSource();
	}

	private String singleConfiguredSource() {
		List<DataSourceEntry> sources = definition.getDataSources();
		return sources != null && sources.size() == 1 ? sources.get(0).getId() : null;
	}

	private DataAdapter delegateOfClass(String className) {
		return factory.create(definition.source(configSourceOfClass(className)), jsonRule);
	}

	private String configSourceOfRelationship(String relationName) {
		RelationshipRoute route = router.routeOfRelationship(relationName);
		if (route != null) {
			return route.getSourceId();
		}
		String fallback = singleConfiguredSource();
		if (fallback == null) {
			throw new IllegalArgumentException("The relationship `" + relationName
					+ "` is not routed to any data source in this domain's multi-source configuration");
		}
		return fallback;
	}

	private static String classNameOf(Map<String, Object> classDef) {
		return classDef == null ? null : (String) classDef.get("className");
	}

	/**
	 * The single source a DSL element resolves to.
	 *
	 * <p>Only the first step's objects are examined: every adapter ignores a later step's class (it
	 * aggregates over the first step's result), so those classes never name a table and must not drive
	 * routing.</p>
	 *
	 * @throws DslContractViolationException when the element names no routed class, or spans sources
	 */
	private String singleSourceOf(JSONObject element, String path) {
		JSONObject answer = element.getJSONObject("answer");
		JSONArray steps = answer == null ? null : answer.getJSONArray("steps");
		if (steps == null || steps.isEmpty()) {
			throw new DslContractViolationException(DslContractViolationException.Code.UNSUPPORTED_CONSTRUCT,
					path + ".steps", "the DSL has no step to route");
		}
		JSONObject step0 = steps.getJSONObject(0);
		JSONObject graph = step0.getJSONObject("graph");
		JSONArray patterns = graph == null ? null : graph.getJSONArray("patterns");

		Map<String, String> sourceToClass = new TreeMap<>();
		List<String> unrouted = new ArrayList<>();
		if (patterns != null) {
			for (int p = 0; p < patterns.size(); p++) {
				JSONArray objects = patterns.getJSONObject(p).getJSONArray("objects");
				if (objects == null) {
					continue;
				}
				for (int o = 0; o < objects.size(); o++) {
					String className = objects.getJSONObject(o).getString("class");
					String sourceId = resolveSourceOfClass(className);
					if (sourceId == null) {
						unrouted.add(className);
					} else {
						sourceToClass.putIfAbsent(sourceId, className);
					}
				}
			}
		}

		String objectPath = path + ".steps[0].patterns[].objects[].class";
		if (!unrouted.isEmpty()) {
			throw new DslContractViolationException(DslContractViolationException.Code.UNSUPPORTED_CONSTRUCT, objectPath,
					"these classes are not routed to any data source: " + String.join(", ", unrouted));
		}
		if (sourceToClass.isEmpty()) {
			throw new DslContractViolationException(DslContractViolationException.Code.UNSUPPORTED_CONSTRUCT, objectPath,
					"the first step names no object class, so this query cannot be routed");
		}
		if (sourceToClass.size() > 1) {
			StringBuilder detail = new StringBuilder("a graph query may not span data sources: ");
			sourceToClass.forEach((sourceId, className) -> detail.append("`").append(className)
					.append("` is in `").append(sourceId).append("`; "));
			throw new DslContractViolationException(DslContractViolationException.Code.UNSUPPORTED_CONSTRUCT, objectPath,
					detail.toString().trim());
		}
		return sourceToClass.keySet().iterator().next();
	}

	/**
	 * Apply one write operation to every writable source.
	 *
	 * <p>There is no cross-source transaction, so this is best-effort: every source is attempted, and a
	 * partial failure is reported afterwards rather than hiding the sources that did succeed.</p>
	 */
	private void fanOut(String operation, Consumer<DataAdapter> action) {
		List<String> failures = new ArrayList<>();
		for (DataSourceEntry source : definition.getDataSources()) {
			if (source.isReadOnly()) {
				log.info("Skipping read-only data source {} for {}", source.getId(), operation);
				continue;
			}
			try {
				action.accept(factory.create(source, jsonRule));
			} catch (Exception e) {
				failures.add(source.getId() + ": " + e.getMessage());
				log.warn("{} failed on data source {}: {}", operation, source.getId(), e.getMessage());
			}
		}
		if (!failures.isEmpty()) {
			throw new IllegalStateException(operation + " partially failed (there is no cross-source transaction): "
					+ String.join("; ", failures));
		}
	}

	// ----------------------------------------------------------------------------------
	// DataAdapter
	// ----------------------------------------------------------------------------------

	@Override
	public Connection openConnection() throws SQLException {
		throw new UnsupportedDataAdapterOperationException("multi", "openConnection");
	}

	@Override
	public JSONObject testConnection() {
		JSONArray sources = new JSONArray();
		for (DataSourceEntry source : definition.getDataSources()) {
			JSONObject entry = new JSONObject();
			entry.put("id", source.getId());
			entry.put("adapterType", source.getAdapterType());
			entry.put("readOnly", source.isReadOnly());
			try {
				entry.put("report", factory.create(source, jsonRule).testConnection());
				entry.put("ok", true);
			} catch (Exception e) {
				entry.put("ok", false);
				entry.put("error", DataAdapterConnectionTester.failureMessage(e));
			}
			sources.add(entry);
		}
		return new JSONObject().fluentPut("kind", "multi-source").fluentPut("sources", sources);
	}

	/**
	 * {@code false}: pass a time condition through verbatim here.
	 *
	 * <p>Each delegate re-applies the time formatting inside its own query path using its own flag, so a
	 * no-op at this level lets every source keep its own convention without a second rewrite. The flag is a
	 * per-domain scalar today, which cannot describe a domain whose sources disagree — this is the value
	 * that is safe for all of them at once.</p>
	 */
	@Override
	public boolean timeConditionsAsEpochMillis() {
		return false;
	}

	@Override
	public TimeZone timeConditionZone() {
		return null;
	}

	@Override
	public String executeDsl(String queryJson, String sessionId, DslExecutionMode mode, List<String> generatedMqls)
			throws Exception {
		JSONArray elements = JSON.parseArray(queryJson);
		JSONArray merged = new JSONArray();
		for (int i = 0; i < elements.size(); i++) {
			JSONObject element = elements.getJSONObject(i);
			String sourceId = singleSourceOf(element, "[" + i + "]");
			DataAdapter delegate = factory.create(definition.source(sourceId), jsonRule);
			String result = delegate.executeDsl(JSONArray.of(withPhysicalEdgeNames(element)).toJSONString(), sessionId, mode,
					generatedMqls);
			if (result != null && !result.isBlank()) {
				merged.addAll(JSON.parseArray(result));
			}
		}
		return merged.toJSONString();
	}

	@Override
	public String getSampleDataFileName(String sampleDataDirName, String className) {
		return delegateOfClass(className).getSampleDataFileName(sampleDataDirName, className);
	}

	@Override
	public String querySampleDataByClassName(String className, List<String> attrs) {
		return delegateOfClass(className).querySampleDataByClassName(className, attrs);
	}

	@Override
	public void validateClassNameFormat(String className) throws Exception {
		delegateOfClass(className).validateClassNameFormat(className);
	}

	/**
	 * <p>A primary key belongs to a class, but this signature carries only the attribute name, so there is
	 * no source to pick: only the source-independent check (it must not be blank) is applied here.</p>
	 */
	@Override
	public void validatePrimaryKeyName(String attrName) throws Exception {
		if (attrName == null || attrName.isBlank()) {
			throw new IllegalArgumentException("A primary key attribute name must not be blank");
		}
	}

	@Override
	public void createNamespace(String namespace) {
		fanOut("createNamespace", adapter -> adapter.createNamespace(namespace));
	}

	@Override
	public void truncateNamespace(String namespace) {
		fanOut("truncateNamespace", adapter -> adapter.truncateNamespace(namespace));
	}

	@Override
	public void dropNamespace(String namespace) {
		fanOut("dropNamespace", adapter -> adapter.dropNamespace(namespace));
	}

	@Override
	public void createClass(String namespace, Map<String, Object> classDef) {
		String className = classNameOf(classDef);
		delegateOfClass(className).createClass(namespace, classDef);
	}

	@Override
	public void createEdgeType(String namespace, String edgeName) {
		String sourceId = configSourceOfRelationship(edgeName);
		RelationshipRoute route = router.routeOfRelationship(edgeName);
		String physicalName = route != null && route.getRealEdge() != null && !route.getRealEdge().isBlank()
				? route.getRealEdge()
				: edgeName;
		factory.create(definition.source(sourceId), jsonRule).createEdgeType(namespace, physicalName);
	}

	@Override
	public void insertObjects(String namespace, Map<String, Object> classDef, JSONArray objs) throws Exception {
		delegateOfClass(classNameOf(classDef)).insertObjects(namespace, classDef, objs);
	}

	@Override
	public void updateObjects(String namespace, Map<String, Object> classDef, JSONObject setValues, JSONObject where)
			throws Exception {
		delegateOfClass(classNameOf(classDef)).updateObjects(namespace, classDef, setValues, where);
	}

	@Override
	public void deleteObjects(String namespace, Map<String, Object> classDef, JSONObject where) throws Exception {
		delegateOfClass(classNameOf(classDef)).deleteObjects(namespace, classDef, where);
	}

	@Override
	public void createEdge(String namespace, String relationName, String sourceClassName, String sourceObjId,
			String targetClassName, String targetObjId) throws Exception {
		assertSameSource(relationName, sourceClassName, targetClassName);
		String sourceId = configSourceOfRelationship(relationName);
		factory.create(definition.source(sourceId), jsonRule).createEdge(namespace, relationName, sourceClassName,
				sourceObjId, targetClassName, targetObjId);
	}

	@Override
	public void deleteEdge(String namespace, String relationName, String sourceClassName, String sourceObjId,
			String targetClassName, String targetObjId) throws Exception {
		assertSameSource(relationName, sourceClassName, targetClassName);
		String sourceId = configSourceOfRelationship(relationName);
		factory.create(definition.source(sourceId), jsonRule).deleteEdge(namespace, relationName, sourceClassName,
				sourceObjId, targetClassName, targetObjId);
	}

	/** An edge whose two ends live in different sources is a configuration error, not a query to guess at. */
	private void assertSameSource(String relationName, String sourceClassName, String targetClassName) {
		String from = resolveSourceOfClass(sourceClassName);
		String to = resolveSourceOfClass(targetClassName);
		if (from != null && to != null && !from.equals(to)) {
			throw new IllegalArgumentException("The relationship `" + relationName + "` spans data sources (`"
					+ sourceClassName + "` is in `" + from + "`, `" + targetClassName + "` is in `" + to + "`)");
		}
	}

	@Override
	public Map<String, Object> query(JSONObject dsl, String sandboxId, String domainId,
			VectorResourceDao vectorResourceDao, DataRagConfig dataRagConfig) throws Exception {
		String sourceId = singleSourceOf(dsl, "query");
		return factory.create(definition.source(sourceId), jsonRule).query(withPhysicalEdgeNames(dsl), sandboxId, domainId,
				vectorResourceDao, dataRagConfig);
	}

	/**
	 * Replace every relationship type that has a configured physical edge name, on a copy.
	 *
	 * <p>A graph source traverses the name the DSL carries, so when the ontology relationship name is not
	 * the physical edge name (or two graph sources would clash on one), the route's {@code realEdge} has to
	 * be applied on the read path too — otherwise the traversal looks for an edge that does not exist. The
	 * element is copied, so the caller's object is never mutated and an element with no configured edge name
	 * is forwarded byte for byte.</p>
	 */
	private JSONObject withPhysicalEdgeNames(JSONObject element) {
		JSONObject copy = JSON.parseObject(element.toJSONString());
		JSONObject answer = copy.getJSONObject("answer");
		JSONArray steps = answer == null ? null : answer.getJSONArray("steps");
		if (steps == null) {
			return copy;
		}
		for (int s = 0; s < steps.size(); s++) {
			JSONObject graph = steps.getJSONObject(s) == null ? null : steps.getJSONObject(s).getJSONObject("graph");
			JSONArray patterns = graph == null ? null : graph.getJSONArray("patterns");
			if (patterns == null) {
				continue;
			}
			for (int p = 0; p < patterns.size(); p++) {
				JSONArray relationships = patterns.getJSONObject(p) == null ? null
						: patterns.getJSONObject(p).getJSONArray("relationship");
				if (relationships == null) {
					continue;
				}
				for (int r = 0; r < relationships.size(); r++) {
					JSONArray types = relationships.getJSONObject(r) == null ? null
							: relationships.getJSONObject(r).getJSONArray("type");
					if (types == null) {
						continue;
					}
					for (int t = 0; t < types.size(); t++) {
						RelationshipRoute route = router.routeOfRelationship(types.getString(t));
						if (route != null && route.getRealEdge() != null && !route.getRealEdge().isBlank()) {
							types.set(t, route.getRealEdge());
						}
					}
				}
			}
		}
		return copy;
	}

	@Override
	public JSONArray queryVectorAttr(Map<String, Object> classDef, String attrName, String objectId) throws Exception {
		return delegateOfClass(classNameOf(classDef)).queryVectorAttr(classDef, attrName, objectId);
	}

	/**
	 * <p>A blank class name yields the bare platform prefix, which is what a prefix scan over sandbox vector
	 * tables needs; a named class routes to its source so the table name stays aligned with that source's
	 * own naming.</p>
	 */
	@Override
	public String sandboxClassName(String sandboxId, String className) {
		if (className == null || className.isBlank()) {
			return sandboxId == null ? "" : sandboxId;
		}
		return delegateOfClass(className).sandboxClassName(sandboxId, className);
	}

	/**
	 * <p>Carries no class name, so no source can be picked. Every non-M3 adapter returns the id unchanged,
	 * which is what this does; an adapter that prefixes ids per source cannot be honoured through this
	 * signature (its own overload carrying the class name would be needed).</p>
	 */
	@Override
	public String sandboxObjectId(String sandboxId, String objectId) {
		return objectId;
	}

	/**
	 * Report the ontomap shapes this routing cannot honour: a class with no source, and a relationship whose
	 * two ends land in different sources. Both are found from the routes alone, so this runs before any query
	 * and also when the domain switches to {@code multi} with an ontomap already configured.
	 */
	@Override
	public List<OntoMapViolation> validateOntoMap(Map<String, Object> jsonRule) {
		List<OntoMapViolation> violations = new ArrayList<>();
		if (jsonRule == null) {
			return violations;
		}
		List<Map<String, Object>> classDefs = (List<Map<String, Object>>) jsonRule.get("classDef");
		if (classDefs != null) {
			for (Map<String, Object> classDef : classDefs) {
				String className = classNameOf(classDef);
				if (resolveSourceOfClass(className) == null) {
					violations.add(new OntoMapViolation("classDef[" + className + "]",
							"the class `" + className + "` is not routed to any data source"));
				}
			}
		}
		Map<String, Object> relationshipRule = (Map<String, Object>) jsonRule.get("relationship_rule");
		if (relationshipRule != null) {
			for (Map.Entry<String, Object> entry : relationshipRule.entrySet()) {
				Map<String, Object> relationshipDef = (Map<String, Object>) entry.getValue();
				if (relationshipDef == null) {
					continue;
				}
				String fromClass = (String) relationshipDef.get("fromclass");
				String toClass = (String) relationshipDef.get("toclass");
				String from = resolveSourceOfClass(fromClass);
				String to = resolveSourceOfClass(toClass);
				if (from != null && to != null && !from.equals(to)) {
					violations.add(new OntoMapViolation("relationshipDefs[" + entry.getKey() + "]",
							"the relationship `" + entry.getKey() + "` crosses data sources (`" + fromClass
									+ "` is in `" + from + "`, `" + toClass + "` is in `" + to
									+ "`), and a cross-source graph query is not supported"));
				}
			}
		}
		return violations;
	}
}
