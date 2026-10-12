package io.ontomato.dataengine.dataAdapter;

import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Compiles Ontology DSL into parameterized, read-only Doris SQL.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
public final class DorisSqlCompiler {

	/**
	 * Compiles one DSL envelope using the supplied ontology mapping.
	 *
	 * @param dsl one Ontology DSL object
	 * @param jsonRule ontology class and relationship mappings
	 * @return SQL text and JDBC parameters
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	public DorisSql compile(JSONObject dsl, Map<String, Object> jsonRule) {
		DorisSqlCompilerSupport.requireInputs(dsl, jsonRule);
		DorisSqlCompilerContext context = new DorisSqlCompilerContext(jsonRule);
		JSONArray steps = DorisSqlCompilerSupport.required(dsl.getJSONObject("answer"), "steps");
		DorisSqlCompilerSupport.validateStepCount(steps);
		return new DorisSqlStepCompiler(context).compile(steps);
	}
}
