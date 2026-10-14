package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** Opt-in compatibility coverage for an externally supplied, read-only Doris FE. */
class DorisRealIntegrationTest {

	@Test
	void readsVisibleMetadataAndExecutesASelectAgainstDorisFe() throws Exception {
		String url = System.getenv("ONTOMATO_TEST_DORIS_URL");
		String user = System.getenv("ONTOMATO_TEST_DORIS_USER");
		String password = System.getenv("ONTOMATO_TEST_DORIS_PASSWORD");
		Assumptions.assumeTrue(hasText(url) && hasText(user) && password != null,
			"Set ONTOMATO_TEST_DORIS_URL, ONTOMATO_TEST_DORIS_USER, and ONTOMATO_TEST_DORIS_PASSWORD to run the real Doris test");

		DataAdapterConnection connection = new DataAdapterConnection();
		connection.setUrl(url);
		connection.setUser(user);
		connection.setPassword(password);
		DorisAdapter metadataAdapter = new DorisAdapter(connection, null);
		JSONObject report = metadataAdapter.testConnection();

		assertTrue(report.getString("databaseProduct").toLowerCase().contains("doris"), report.toJSONString());
		JSONArray tables = report.getJSONArray("tables");
		String currentDatabase = report.getString("catalog");
		JSONObject table = tables.stream().map(JSONObject.class::cast)
				.filter(candidate -> currentDatabase != null && currentDatabase.equals(candidate.getString("schema")))
				.filter(this::queryable).findFirst().orElse(null);
		Assumptions.assumeTrue(table != null,
			"The configured Doris user must expose at least one table or view with a simple column name");

		JSONArray columns = table.getJSONArray("columns");
		JSONObject column = columns.getJSONObject(0);
		String tableName = table.getString("name");
		String columnName = column.getString("name");
		Map<String, Object> classDef = new HashMap<>();
		classDef.put("className", tableName);
		classDef.put("attrs", List.of(Map.of("name", columnName, "type", "varchar")));
		Map<String, Object> rule = new HashMap<>();
		rule.put("classDef", List.of(classDef));
		rule.put("relationship_rule", Map.of());

		DorisAdapter adapter = new DorisAdapter(connection, rule);
		String dsl = "[{\"problem\":\"\",\"answer\":{\"steps\":[{\"graph\":{\"patterns\":[{\"objects\":[{\"idx\":0,\"variable\":\"row\",\"class\":\""
				+ tableName + "\",\"conditions\":{}}]}]},\"output\":{\"fields\":[{\"variable\":\"row\",\"field\":\""
				+ columnName + "\",\"as\":\"value\"}],\"limit\":{\"offset\":0,\"count\":1}}}]}}]";
		String response = adapter.executeDsl(dsl, "real-doris-test", DslExecutionMode.RAW, new ArrayList<>());

		JSONArray answers = JSON.parseArray(response);
		assertFalse(answers.isEmpty(), "Doris FE returned no answer envelope");
		assertTrue(answers.getJSONObject(0).containsKey("answer"), response);
	}

	private boolean queryable(JSONObject table) {
		if (!List.of("TABLE", "VIEW").contains(table.getString("type"))) {
			return false;
		}
		if (!table.getString("name").matches("[A-Za-z_][A-Za-z0-9_]*")) {
			return false;
		}
		JSONArray columns = table.getJSONArray("columns");
		return columns != null && !columns.isEmpty()
				&& columns.getJSONObject(0).getString("name").matches("[A-Za-z_][A-Za-z0-9_]*");
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
