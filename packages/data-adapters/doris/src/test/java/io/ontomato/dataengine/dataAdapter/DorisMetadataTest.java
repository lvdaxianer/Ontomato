package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

class DorisMetadataTest {

	@Test
	void connectionReportContainsVisibleCatalogsSchemasTablesViewsAndColumns() throws Exception {
		DorisAdapter adapter = new DorisAdapter(new DataAdapterConnection(), null) {
			@Override
			public Connection openConnection() {
				return DorisMetadataTest.this.connection();
			}
		};

		JSONObject report = adapter.testConnection();

		assertEquals("Doris", report.getString("databaseProduct"));
		assertEquals("MySQL", report.getString("jdbcDatabaseProduct"));
		assertEquals(List.of("internal"), names(report.getJSONArray("catalogs")));
		assertEquals(List.of("analytics"), names(report.getJSONArray("databases")));
		assertEquals(List.of("analytics"), names(report.getJSONArray("schemas")));
		JSONArray tables = report.getJSONArray("tables");
		assertEquals(2, tables.size());
		assertEquals("TABLE", tables.getJSONObject(0).getString("type"));
		assertEquals("VIEW", tables.getJSONObject(1).getString("type"));
		assertEquals("id", report.getJSONArray("columns").getJSONObject(0).getString("name"));
		assertEquals("BIGINT", report.getJSONArray("columns").getJSONObject(0).getString("type"));
	}

	@Test
	void metadataQueriesAreReadOnlyAndOnlyReturnRowsVisibleToTheUser() throws Exception {
		List<String> executed = new ArrayList<>();
		DorisAdapter adapter = new DorisAdapter(new DataAdapterConnection(), null) {
			@Override
			public Connection openConnection() {
				return DorisMetadataTest.this.connection(executed);
			}
		};

		JSONObject report = adapter.testConnection();

		assertTrue(report.getJSONArray("tables").stream().map(JSONObject.class::cast)
				.noneMatch(table -> "hidden_orders".equals(table.getString("name"))));
		assertTrue(executed.stream().allMatch(sql -> sql.trim().toLowerCase().startsWith("select")));
		assertFalse(executed.stream().anyMatch(sql -> sql.toLowerCase().contains("insert")
				|| sql.toLowerCase().contains("update") || sql.toLowerCase().contains("create")));
	}

	private List<String> names(JSONArray values) {
		return values.stream().map(JSONObject.class::cast).map(value -> value.getString("name")).toList();
	}

	private Connection connection() {
		return connection(new ArrayList<>());
	}

	private Connection connection(List<String> executed) {
		DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (method, args) -> switch (method.getName()) {
		case "getDatabaseProductName" -> "MySQL";
		case "getDatabaseProductVersion" -> "2.1.7";
		case "getDriverName" -> "MySQL Connector/J";
		case "getDriverVersion" -> "9.0.0";
		default -> defaultValue(method.getReturnType());
		});
		return proxy(Connection.class, (method, args) -> switch (method.getName()) {
		case "isValid" -> true;
		case "getMetaData" -> metadata;
		case "getCatalog" -> "internal";
		case "getSchema" -> "analytics";
		case "prepareStatement" -> preparedStatement((String) args[0], executed);
		default -> defaultValue(method.getReturnType());
		});
	}

	private PreparedStatement preparedStatement(String sql, List<String> executed) {
		return proxy(PreparedStatement.class, (method, args) -> {
			if ("executeQuery".equals(method.getName())) {
				executed.add(sql);
				if (sql.contains("schemata")) {
					return rows(List.of(row("CATALOG_NAME", "internal", "SCHEMA_NAME", "analytics")));
				}
				if (sql.contains("information_schema.tables")) {
					return rows(List.of(
							row("TABLE_CATALOG", "internal", "TABLE_SCHEMA", "analytics", "TABLE_NAME", "orders", "TABLE_TYPE", "BASE TABLE"),
							row("TABLE_CATALOG", "internal", "TABLE_SCHEMA", "analytics", "TABLE_NAME", "v_orders", "TABLE_TYPE", "VIEW")));
				}
				return rows(List.of(
						row("TABLE_CATALOG", "internal", "TABLE_SCHEMA", "analytics", "TABLE_NAME", "orders", "COLUMN_NAME", "id", "DATA_TYPE", "BIGINT", "ORDINAL_POSITION", 1),
						row("TABLE_CATALOG", "internal", "TABLE_SCHEMA", "analytics", "TABLE_NAME", "orders", "COLUMN_NAME", "status", "DATA_TYPE", "VARCHAR", "ORDINAL_POSITION", 2)));
			}
			return defaultValue(method.getReturnType());
		});
	}

	private ResultSet rows(List<JSONObject> values) {
		return proxy(ResultSet.class, new ResultSetHandler(values));
	}

	@SuppressWarnings("unchecked")
	private <T> T proxy(Class<T> type, Invocation handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
				(proxy, method, args) -> handler.invoke(method, args));
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		return 0;
	}

	private JSONObject row(Object... values) {
		JSONObject row = new JSONObject();
		for (int index = 0; index < values.length; index += 2) row.put((String) values[index], values[index + 1]);
		return row;
	}

	@FunctionalInterface
	private interface Invocation {
		Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
	}

	private static final class ResultSetHandler implements Invocation {
		private final List<JSONObject> values;
		private int index = -1;

		private ResultSetHandler(List<JSONObject> values) {
			this.values = values;
		}

		@Override
		public Object invoke(java.lang.reflect.Method method, Object[] args) {
			return switch (method.getName()) {
			case "next" -> ++index < values.size();
			case "getString" -> String.valueOf(values.get(index).get((String) args[0]));
			case "getInt" -> ((Number) values.get(index).get((String) args[0])).intValue();
			default -> defaultValue(method.getReturnType());
			};
		}
	}
}
