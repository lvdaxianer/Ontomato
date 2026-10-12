package io.ontomato.dataengine.dataAdapter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.dao.VectorResourceDao;

/**
 * Doris FE adapter. Query compilation and metadata discovery are added in later tasks;
 * this boundary already keeps the adapter separate from the writable MySQL adapter.
 */
public class DorisAdapter implements DataAdapter {

	private final DataAdapterConnection connection;
	private final Map<String, Object> jsonRule;

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
	public String executeDsl(String queryJson, String sessionId, DslExecutionMode mode, List<String> generatedMqls)
			throws Exception {
		throw unsupported("DSL query execution");
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
		throw unsupported("class validation");
	}

	@Override
	public void validatePrimaryKeyName(String attrName) {
		throw unsupported("primary-key validation");
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
			VectorResourceDao vectorResourceDao, DataRagConfig dataRagConfig) {
		throw unsupported("stored object query");
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
