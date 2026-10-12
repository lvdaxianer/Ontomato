package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

class DorisSqlCompilerTest {

	@Test
	void compilesSingleTableFiltersOrderingAndDorisPagination() {
		DorisSql sql = compile(singleTableDsl("status", "new' OR 1=1"), rule(
				classDef("orders", attr("id", "varchar"), attr("status", "varchar"))));

		assertTrue(sql.sql().contains("from\n    `orders` as `o`"));
		assertTrue(sql.sql().contains("`o`.`status` = ?"));
		assertTrue(sql.sql().contains("order by\n    `id` desc"));
		assertTrue(sql.sql().contains("limit 20 offset 10"));
		assertEquals(List.of("new' OR 1=1"), sql.parameters());
		assertFalse(sql.sql().contains("OR 1=1"));
	}

	@Test
	void compilesRelationshipJoinsFromOntologyRules() {
		Map<String, Object> jsonRule = rule(
				classDef("orders", attr("id", "varchar"), attr("customer_id", "varchar")),
				classDef("customers", attr("id", "varchar"), attr("name", "varchar")));
		((Map<String, Object>) jsonRule.get("relationship_rule")).put("customer", Map.of(
				"fromField", "customer_id", "toField", "id"));
		String dsl = """
				[{"problem":"","answer":{"steps":[{"graph":{"patterns":[{"objects":[
				{"idx":0,"variable":"o","class":"orders","conditions":{}},
				{"idx":1,"variable":"c","class":"customers","conditions":{}}],
				"relationship":[{"from":0,"to":1,"type":["customer"]}]}]},
				"output":{"fields":[{"variable":"o","field":"id","as":"order_id"},{"variable":"c","field":"name","as":"customer_name"}]}}]}}]""";

		DorisSql sql = compile(dsl, jsonRule);

		assertTrue(sql.sql().contains("`orders` as `o` inner join `customers` as `c`"));
		assertTrue(sql.sql().contains("on `o`.`customer_id` = `c`.`id`"));
		assertTrue(sql.sql().contains("`o`.`id` as `order_id`,`c`.`name` as `customer_name`"));
	}

	@Test
	void compilesAggregateGroupHavingAndDescendingSort() {
		String dsl = """
				[{"problem":"","answer":{"steps":[{"graph":{"patterns":[{"objects":[
				{"idx":0,"variable":"o","class":"orders","conditions":{}}]}]},
				"output":{"fields":[{"variable":"o","field":"customer_id","as":"customer_id"},{"variable":"o","field":"id","function":"count","as":"order_count"}],
				"group_by":{"fields":[{"variable":"o","field":"customer_id"}],"having":[{"variable":"o","field":"id","function":"count","operator":">","value":1}]},
				"sort":{"fields":[{"field":"order_count","order":"desc"}]},"limit":{"offset":0,"count":5}}}]}}]""";

		DorisSql sql = compile(dsl, rule(classDef("orders", attr("id", "varchar"), attr("customer_id", "varchar"))));

		assertTrue(sql.sql().contains("count(`o`.`id`) as `order_count`"));
		assertTrue(sql.sql().contains("group by\n    `o`.`customer_id`"));
		assertTrue(sql.sql().contains("having\n    count(`o`.`id`) > ?"));
		assertTrue(sql.sql().contains("order by\n    `order_count` desc"));
		assertEquals(List.of(1L), sql.parameters());
	}

	@Test
	void rejectsUnsafeIdentifiersBeforeBuildingSql() {
		String dsl = singleTableDsl("id", "1");
		Map<String, Object> rule = rule(classDef("orders; drop table users", attr("id", "varchar")));

		assertThrows(IllegalArgumentException.class, () -> compile(dsl, rule));
	}

	@Test
	void compilesDateAndCollectionPredicatesWithBoundValues() {
		String dsl = """
				[{"problem":"","answer":{"steps":[{"graph":{"patterns":[{"objects":[
				{"idx":0,"variable":"o","class":"orders","conditions":{"properties":{"and":[
				{"field":"status","operator":"in","value":["new","done"]},
				{"field":"created_at","operator":">","value":1700000000},
				{"field":"amount","operator":"between","value":[10,20]}]}}}]}]},
				"output":{"fields":[{"variable":"o","field":"id","as":"id"}]}}]}}]""";

		DorisSql sql = compile(dsl, rule(classDef("orders", attr("id", "varchar"), attr("status", "varchar"),
				attr("created_at", "timestamp"), attr("amount", "int"))));

		assertTrue(sql.sql().contains("`o`.`status` in (?,?)"));
		assertTrue(sql.sql().contains("`o`.`created_at` > FROM_UNIXTIME(?)"));
		assertTrue(sql.sql().contains("`o`.`amount` >= ? and `o`.`amount` <= ?"));
		assertEquals(List.of("new", "done", 1700000000L, 10, 20), sql.parameters());
	}

	@Test
	void compilesTheExistingTwoStepAggregationShapeAsAReadOnlySubquery() {
		String dsl = """
				[{"problem":"","answer":{"steps":[
				{"graph":{"patterns":[{"objects":[{"idx":0,"variable":"o","class":"orders","conditions":{}}]}]},
				"output":{"fields":[{"variable":"o","field":"customer_id","as":"customer_id"},{"variable":"o","field":"id","as":"id"}]}},
				{"graph":{"patterns":[{"objects":[{"idx":0,"variable":"result","class":"derived"}]}]},
				"output":{"fields":[{"field":"customer_id","as":"customer_id"},{"field":"id","function":"count","as":"order_count"}],
				"group_by":{"fields":[{"field":"customer_id"}]},"sort":{"fields":[{"field":"order_count","order":"desc"}]},"limit":{"offset":5,"count":10}}}
				]}}]""";

		DorisSql sql = compile(dsl, rule(classDef("orders", attr("id", "varchar"), attr("customer_id", "varchar"))));

		assertTrue(sql.sql().contains("from (\nselect"));
		assertTrue(sql.sql().contains(") as `result`"));
		assertTrue(sql.sql().contains("count(`result`.`id`) as `order_count`"));
		assertTrue(sql.sql().contains("group by\n    `result`.`customer_id`"));
		assertTrue(sql.sql().contains("limit 10 offset 5"));
	}

	private DorisSql compile(String dsl, Map<String, Object> rule) {
		return new DorisSqlCompiler().compile(JSON.parseArray(dsl).getJSONObject(0), rule);
	}

	private String singleTableDsl(String field, String value) {
		return """
				[{"problem":"","answer":{"steps":[{"graph":{"patterns":[{"objects":[
				{"idx":0,"variable":"o","class":"orders","conditions":{"properties":{"and":[{"field":"%s","operator":"=","value":"%s"}]}}}]}]},
				"output":{"fields":[{"variable":"o","field":"id","as":"id"}],"sort":{"fields":[{"field":"id","order":"desc"}]},"limit":{"offset":10,"count":20}}}]}}]""".formatted(field, value.replace("\\", "\\\\").replace("\"", "\\\""));
	}

	private Map<String, Object> rule(Map<String, Object>... defs) {
		return new java.util.HashMap<>(Map.of("classDef", List.of(defs), "relationship_rule", new java.util.HashMap<>()));
	}

	private Map<String, Object> classDef(String name, Map<String, Object>... attrs) {
		return Map.of("className", name, "attrs", List.of(attrs));
	}

	private Map<String, Object> attr(String name, String type) {
		return Map.of("name", name, "type", type);
	}
}
