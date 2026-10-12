package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

class DorisAdapterExecutionTest {

	@Test
	void executesCompiledSelectWithPreparedParametersAndReturnsRows() throws Exception {
		List<Object> bound = new ArrayList<>();
		List<String> sqls = new ArrayList<>();
		DorisAdapter adapter = new DorisAdapter(new DataAdapterConnection(), rule()) {
			@Override
			public Connection openConnection() {
				return DorisAdapterExecutionTest.this.connection(sqls, bound);
			}
		};
		List<String> mqls = new ArrayList<>();

		String response = adapter.executeDsl(dsl(), "session-1", DslExecutionMode.RAW, mqls);

		JSONArray answers = JSON.parseArray(response);
		assertEquals("row-1", answers.getJSONObject(0).getJSONArray("answer").getJSONObject(0).getString("id"));
		assertEquals(List.of("new"), bound);
		assertEquals(1, sqls.size());
		assertTrue(sqls.get(0).contains("`o`.`status` = ?"));
		assertEquals(sqls, mqls);
	}

	private Connection connection(List<String> sqls, List<Object> bound) {
		ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, args) -> switch (method.getName()) {
			case "getColumnCount" -> 1;
			case "getColumnLabel", "getColumnName" -> "id";
			default -> defaultValue(method.getReturnType());
		});
		ResultSet rows = proxy(ResultSet.class, new ProxyHandler() {
			private boolean returned;

			@Override
			public Object invoke(java.lang.reflect.Method method, Object[] args) {
				return switch (method.getName()) {
				case "next" -> {
					boolean hasRow = !returned;
					returned = true;
					yield hasRow;
				}
				case "getMetaData" -> metadata;
				case "getObject" -> "row-1";
				default -> defaultValue(method.getReturnType());
				};
			}
		});
		PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> {
			if ("setObject".equals(method.getName())) {
				bound.add(args[1]);
				return null;
			}
			if ("executeQuery".equals(method.getName())) {
				return rows;
			}
			if ("toString".equals(method.getName())) {
				return "prepared-doris-select";
			}
			return defaultValue(method.getReturnType());
		});
		return proxy(Connection.class, (method, args) -> {
			if ("prepareStatement".equals(method.getName())) {
				sqls.add((String) args[0]);
				return statement;
			}
			return defaultValue(method.getReturnType());
		});
	}

	@SuppressWarnings("unchecked")
	private <T> T proxy(Class<T> type, ProxyHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> handler.invoke(method, args));
	}

	private Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return '\0';
		return null;
	}

	private Map<String, Object> rule() {
		return Map.of("classDef", List.of(Map.of("className", "orders", "attrs", List.of(
				Map.of("name", "id", "type", "varchar"), Map.of("name", "status", "type", "varchar")))),
				"relationship_rule", Map.of());
	}

	private String dsl() {
		return """
				[{"problem":"","answer":{"steps":[{"graph":{"patterns":[{"objects":[
				{"idx":0,"variable":"o","class":"orders","conditions":{"properties":{"and":[{"field":"status","operator":"=","value":"new"}]}}}]}]},
				"output":{"fields":[{"variable":"o","field":"id","as":"id"}]}}]}}]""";
	}

	@FunctionalInterface
	private interface ProxyHandler {
		Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
	}
}
