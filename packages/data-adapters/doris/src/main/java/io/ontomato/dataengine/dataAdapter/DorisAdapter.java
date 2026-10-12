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

/** Doris FE adapter with live SELECT execution and read-only metadata discovery. */
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

	@Override
	public JSONObject testConnection() throws Exception {
		return new DorisConnectionTester(this).test();
	}

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

	/** Execute one already-compiled SELECT and close every JDBC resource before returning. */
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
		if (sandboxId != null && !sandboxId.isBlank()) {
			throw unsupported("sandbox query");
		}
		if (jsonRule == null) {
			throw new IllegalStateException("Doris ontology mapping is required for a query");
		}
		JSONArray dsls = JSONArray.of(dsl);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> classDefs = (List<Map<String, Object>>) jsonRule.get("classDef");
		String dslJson = DslUtil.normalizeClassNames(JSON.toJSONString(dsls, Feature.WriteMapNullValue), classDefs);
		if (dataRagConfig != null) {
			dslJson = DslUtil.setConditionTimeColumnToCorrectFormat(dslJson, dataRagConfig.getDslMightTimeFormats(), classDefs);
		}
		DslExecutionResult result = HttpRequestUtil.executeDsl(dataRagConfig, this, dslJson, null, true, jsonRule,
				vectorResourceDao, new UserDataPermission(), DslExecutionMode.OPTIMIZED, domainId)[1];
		JSONObject data = new JSONObject();
		if (result.data() != null) {
			data.putAll(result.data());
		}
		if (result.failed()) {
			data.put("error", MultiThreadAIChatService.rawErrorText(result.failure().cause()));
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
