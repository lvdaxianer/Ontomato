package io.ontomato.dataengine.dataAdapter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Reads only the connected user's visible Doris information_schema rows.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisMetadataReader {

	private static final int METADATA_ROW_LIMIT = 10000;

	private DorisMetadataReader() {
	}

	/**
	 * Reads bounded metadata visible to the current Doris user.
	 *
	 * @param connection open Doris JDBC connection
	 * @param startNanos probe start time
	 * @return metadata report
	 * @throws SQLException when Doris rejects a metadata query
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static JSONObject read(Connection connection, long startNanos) throws SQLException {
		DatabaseMetaData jdbcMetadata = connection.getMetaData();
		JSONObject report = new JSONObject();
		report.put("databaseProduct", "Doris");
		report.put("jdbcDatabaseProduct", jdbcMetadata.getDatabaseProductName());
		report.put("databaseVersion", jdbcMetadata.getDatabaseProductVersion());
		report.put("driverName", jdbcMetadata.getDriverName());
		report.put("driverVersion", jdbcMetadata.getDriverVersion());
		report.put("catalog", connection.getCatalog());
		report.put("schema", connection.getSchema());

		JSONArray schemas = query(connection,
				"SELECT CATALOG_NAME, SCHEMA_NAME FROM information_schema.schemata "
						+ "ORDER BY CATALOG_NAME, SCHEMA_NAME LIMIT " + METADATA_ROW_LIMIT,
				result -> {
					JSONObject schema = new JSONObject();
					schema.put("catalog", result.getString("CATALOG_NAME"));
					schema.put("name", result.getString("SCHEMA_NAME"));
					return schema;
				});
		report.put("schemas", schemas);
		report.put("catalogs", distinctBy(schemas, "catalog"));
		report.put("databases", distinctBy(schemas, "name"));

		JSONArray tables = query(connection,
				"SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE "
						+ "FROM information_schema.tables ORDER BY TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME LIMIT "
						+ METADATA_ROW_LIMIT,
				result -> {
					String type = normalizeTableType(result.getString("TABLE_TYPE"));
					if (type.isEmpty()) {
						return new JSONObject();
					}
					JSONObject table = new JSONObject();
					table.put("catalog", result.getString("TABLE_CATALOG"));
					table.put("schema", result.getString("TABLE_SCHEMA"));
					table.put("name", result.getString("TABLE_NAME"));
					table.put("type", type);
					return table;
				});
		JSONArray columns = query(connection,
				"SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, ORDINAL_POSITION "
						+ "FROM information_schema.columns ORDER BY TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION LIMIT "
						+ METADATA_ROW_LIMIT,
				result -> {
					JSONObject column = new JSONObject();
					column.put("catalog", result.getString("TABLE_CATALOG"));
					column.put("schema", result.getString("TABLE_SCHEMA"));
					column.put("table", result.getString("TABLE_NAME"));
					column.put("name", result.getString("COLUMN_NAME"));
					column.put("type", result.getString("DATA_TYPE"));
					column.put("ordinal", result.getInt("ORDINAL_POSITION"));
					return column;
				});
		report.put("columns", columns);
		attachColumns(tables, columns);
		report.put("tables", tables);
		report.put("elapsedMs", Math.max(0, (System.nanoTime() - startNanos) / 1_000_000));
		return report;
	}

	private static void attachColumns(JSONArray tables, JSONArray columns) {
		Map<String, JSONArray> columnsByTable = new LinkedHashMap<>();
		for (Object value : columns) {
			JSONObject column = (JSONObject) value;
			String key = key(column.getString("catalog"), column.getString("schema"), column.getString("table"));
			columnsByTable.computeIfAbsent(key, ignored -> new JSONArray()).add(column);
		}
		for (Object value : tables) {
			JSONObject table = (JSONObject) value;
			String key = key(table.getString("catalog"), table.getString("schema"), table.getString("name"));
			table.put("columns", columnsByTable.getOrDefault(key, new JSONArray()));
		}
	}

	private static JSONArray distinctBy(JSONArray source, String field) {
		Map<String, JSONObject> unique = new LinkedHashMap<>();
		for (Object value : source) {
			JSONObject object = (JSONObject) value;
			String name = object.getString(field);
			if (name != null) {
				JSONObject item = new JSONObject();
				item.put("name", name);
				unique.putIfAbsent(name, item);
			}
		}
		return new JSONArray(unique.values());
	}

	private static String key(String catalog, String schema, String table) {
		return String.valueOf(catalog) + "\u0000" + String.valueOf(schema) + "\u0000" + String.valueOf(table);
	}

	private static String normalizeTableType(String tableType) {
		if (tableType == null) {
			return "";
		}
		return switch (tableType.toUpperCase(java.util.Locale.ROOT)) {
		case "TABLE", "BASE TABLE", "PARTITIONED TABLE", "FOREIGN TABLE" -> "TABLE";
		case "VIEW", "MATERIALIZED VIEW" -> "VIEW";
		default -> "";
		};
	}

	private static JSONArray query(Connection connection, String sql, RowMapper mapper) throws SQLException {
		JSONArray rows = new JSONArray();
		try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
			while (result.next()) {
				JSONObject row = mapper.map(result);
				if (!row.isEmpty()) {
					rows.add(row);
				} else {
					continue;
				}
			}
		}
		return rows;
	}

	@FunctionalInterface
	private interface RowMapper {
		JSONObject map(ResultSet result) throws SQLException;
	}
}
