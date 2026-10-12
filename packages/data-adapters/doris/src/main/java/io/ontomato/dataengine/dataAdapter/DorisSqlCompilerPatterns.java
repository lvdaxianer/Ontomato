package io.ontomato.dataengine.dataAdapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Reads the first graph pattern from a DSL step.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerPatterns {

	private DorisSqlCompilerPatterns() {
	}

	/**
	 * Returns the first required graph pattern.
	 *
	 * @param step DSL step
	 * @return graph pattern
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static JSONObject first(JSONObject step) {
		JSONObject graph = DorisSqlCompilerSupport.required(step, "graph");
		JSONArray patterns = DorisSqlCompilerSupport.required(graph, "patterns");
		return patterns.getJSONObject(0);
	}
}
