package io.ontomato.dataengine.dataAdapter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter.Feature;

import io.ontomato.dataengine.bean.DslExecutionResult;
import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.dao.VectorResourceDao;
import io.ontomato.dataengine.service.ai.MultiThreadAIChatService;
import io.ontomato.dataengine.service.sys.bean.permission.UserDataPermission;
import io.ontomato.dataengine.util.DslUtil;
import io.ontomato.dataengine.util.HttpRequestUtil;

/**
 * Doris FE adapter with live SELECT execution and read-only metadata discovery.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
public class DorisAdapter implements DataAdapter {

	private final DataAdapterConnection connection;
	private final Map<String, Object> jsonRule;
	private final DorisSqlCompiler compiler = new DorisSqlCompiler();

	public DorisAdapter(DataAdapterConnection connection, Map<String, Object> jsonRule) {
		this.connection = connection;
		this.jsonRule = jsonRule;
	}

	DataAdapterConnection connection() {
		return connection;
	}

	Map<String, Object> jsonRule() {
		return jsonRule;
	}

	@Override
	public Connection openConnection() throws SQLException {
		return DriverManager.getConnection(connection.getUrl(), connection.getUser(), connection.getPassword());
	}

	/**
	 * Tests the FE connection and returns visible metadata.
	 *
	 * @return bounded metadata report
	 * @throws Exception when the connection or metadata probe fails
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	@Override
	public JSONObject testConnection() throws Exception {
		return new DorisConnectionTester(this).test();
	}

	/**
	 * Compiles and executes one read-only Ontology DSL query.
	 *
	 * @param queryJson serialized Ontology DSL
	 * @param sessionId caller session identifier
	 * @param mode execution mode
	 * @param generatedMqls generated SQL collection
	 * @return serialized query result
	 * @throws Exception when compilation or JDBC execution fails
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	@Override
	public String executeDsl(String queryJson, String sessionId, DslExecutionMode mode, List<String> generatedMqls)
			throws Exception {
		JSONArray dsls = JSONArray.parseArray(queryJson);
		if (dsls == null || dsls.isEmpty()) {
			throw new IllegalArgumentException("Doris DSL query must contain one query");
		}
		DorisSql compiled = compiler.compile(dsls.getJSONObject(0), jsonRule);
		generatedMqls.add(compiled.sql());
		JSONArray data = executeSql(compiled);
		JSONObject answer = new JSONObject();
		answer.put("answer", data);
		answer.put("mqls", generatedMqls);
		return JSON.toJSONString(JSONArray.of(answer), Feature.WriteMapNullValue);
	}

	/**
	 * Executes one already-compiled SELECT and closes every JDBC resource.
	 *
	 * @param compiled parameterized SELECT
	 * @return result rows
	 * @throws SQLException when JDBC execution fails
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private JSONArray executeSql(DorisSql compiled) throws SQLException {
		try (Connection jdbc = openConnection(); PreparedStatement statement = jdbc.prepareStatement(compiled.sql())) {
			for (int index = 0; index < compiled.parameters().size(); index++) {
				statement.setObject(index + 1, compiled.parameters().get(index));
			}
			try (ResultSet rows = statement.executeQuery()) {
				return rowsToJson(rows);
			}
		}
	}

	/**
	 * Normalizes one ontology query through the existing DSL pipeline.
	 *
	 * @param dsl ontology DSL object
	 * @param domainId domain identifier
	 * @param vectorResourceDao vector resource DAO
	 * @param dataRagConfig data configuration
	 * @return query result
	 * @throws Exception when the shared query pipeline fails
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private DslExecutionResult executeOntologyQuery(JSONObject dsl, String domainId,
			VectorResourceDao vectorResourceDao, DataRagConfig dataRagConfig) throws Exception {
		List<Map<String, Object>> classDefs = classDefinitions();
		String normalized = DslUtil.normalizeClassNames(
				JSON.toJSONString(JSONArray.of(dsl), Feature.WriteMapNullValue), classDefs);
		String dslJson = dataRagConfig == null ? normalized
				: DslUtil.setConditionTimeColumnToCorrectFormat(normalized,
						dataRagConfig.getDslMightTimeFormats(), classDefs);
		return HttpRequestUtil.executeDsl(dataRagConfig, this, dslJson, null, true, jsonRule,
				vectorResourceDao, new UserDataPermission(), DslExecutionMode.OPTIMIZED, domainId)[1];
	}

	/**
	 * Reads ontology class definitions required by the shared DSL pipeline.
	 *
	 * @return mapped class definitions
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> classDefinitions() {
		return (List<Map<String, Object>>) jsonRule.get("classDef");
	}

	private JSONArray rowsToJson(ResultSet rows) throws SQLException {
		JSONArray data = new JSONArray();
		ResultSetMetaData metadata = rows.getMetaData();
		int columnCount = metadata.getColumnCount();
		while (rows.next()) {
			JSONObject row = new JSONObject();
			for (int column = 1; column <= columnCount; column++) {
				row.put(metadata.getColumnLabel(column), rows.getObject(column));
			}
			data.add(row);
		}
		return data;
	}

	@Override
	public String getSampleDataFileName(String sampleDataDirName, String className) {
		throw unsupported("sample data");
	}

	@Override
	public String querySampleDataByClassName(String className, List<String> attrs) {
		throw unsupported("sample data");
	}

	@Override
	public void validateClassNameFormat(String className) {
		if (className == null || !className.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid Doris class name: " + className);
		}
	}

	@Override
	public void validatePrimaryKeyName(String attrName) {
		if (attrName == null || !attrName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid Doris primary-key name: " + attrName);
		}
	}

	@Override
	public void createNamespace(String namespace) {
		throw unsupported("namespace creation");
	}

	@Override
	public void truncateNamespace(String namespace) {
		throw unsupported("namespace truncation");
	}

	@Override
	public void dropNamespace(String namespace) {
		throw unsupported("namespace deletion");
	}

	@Override
	public void createClass(String namespace, Map<String, Object> classDef) {
		throw unsupported("class creation");
	}

	@Override
	public void createEdgeType(String namespace, String edgeName) {
		throw unsupported("edge-type creation");
	}

	@Override
	public void insertObjects(String namespace, Map<String, Object> classDef, JSONArray objs) {
		throw unsupported("object insertion");
	}

	@Override
	public void updateObjects(String namespace, Map<String, Object> classDef, JSONObject setValues, JSONObject where) {
		throw unsupported("object update");
	}

	@Override
	public void deleteObjects(String namespace, Map<String, Object> classDef, JSONObject where) {
		throw unsupported("object deletion");
	}

	@Override
	public void createEdge(String namespace, String relationName, String sourceClassName, String sourceObjId,
			String targetClassName, String targetObjId) {
		throw unsupported("edge creation");
	}

	@Override
	public void deleteEdge(String namespace, String relationName, String sourceClassName, String sourceObjId,
			String targetClassName, String targetObjId) {
		throw unsupported("edge deletion");
	}

	@Override
	public Map<String, Object> query(JSONObject dsl, String sandboxId, String domainId,
			VectorResourceDao vectorResourceDao, DataRagConfig dataRagConfig) throws Exception {
		validateSandbox(sandboxId);
		if (jsonRule == null) {
			throw new IllegalStateException("Doris ontology mapping is required for a query");
		} else {
			return queryMappedDsl(dsl, domainId, vectorResourceDao, dataRagConfig);
		}
	}

	/**
	 * Rejects sandbox execution because Doris is an external live source.
	 *
	 * @param sandboxId sandbox identifier
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private void validateSandbox(String sandboxId) {
		if (sandboxId != null && !sandboxId.isBlank()) {
			throw unsupported("sandbox query");
		} else {
			return;
		}
	}

	/**
	 * Executes one mapped DSL query through the shared Ontology pipeline.
	 *
	 * @param dsl ontology DSL object
	 * @param domainId domain identifier
	 * @param vectorResourceDao vector resource DAO
	 * @param dataRagConfig data configuration
	 * @return mapped result
	 * @throws Exception when the shared query pipeline fails
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private Map<String, Object> queryMappedDsl(JSONObject dsl, String domainId,
			VectorResourceDao vectorResourceDao, DataRagConfig dataRagConfig) throws Exception {
		DslExecutionResult result = executeOntologyQuery(dsl, domainId, vectorResourceDao, dataRagConfig);
		JSONObject data = new JSONObject();
		data.putAll(result.data() == null ? Map.of() : result.data());
		if (result.failed()) {
			data.put("error", MultiThreadAIChatService.rawErrorText(result.failure().cause()));
		} else {
			return data;
		}
		return data;
	}

	@Override
	public JSONArray queryVectorAttr(Map<String, Object> classDef, String attrName, String objectId) {
		throw unsupported("vector attribute query");
	}

	@Override
	public String sandboxClassName(String sandboxId, String className) {
		throw unsupported("sandbox class resolution");
	}

	@Override
	public String sandboxObjectId(String sandboxId, String objectId) {
		throw unsupported("sandbox object resolution");
	}

	private UnsupportedDataAdapterOperationException unsupported(String operation) {
		return new UnsupportedDataAdapterOperationException("doris", operation);
	}
}
