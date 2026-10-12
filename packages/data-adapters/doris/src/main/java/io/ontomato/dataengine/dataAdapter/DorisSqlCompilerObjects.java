package io.ontomato.dataengine.dataAdapter;

import java.util.HashSet;
import java.util.Set;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Extracts and validates object references and derived aliases from the DSL.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerObjects {

	private DorisSqlCompilerObjects() {
	}

	/**
	 * Adds all objects in a graph to the compilation context.
	 *
	 * @param step DSL step
	 * @param context compilation context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static void populate(JSONObject step, DorisSqlCompilerContext context) {
		JSONArray objects = objects(step);
		for (int position = 0; position < objects.size(); position++) {
			addObject(objects.getJSONObject(position), position, context);
		}
	}

	/**
	 * Resolves the only derived object alias in a two-step graph.
	 *
	 * @param step derived DSL step
	 * @return validated derived alias
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String derivedAlias(JSONObject step) {
		JSONArray values = objects(step);
		if (values.size() != 1) {
			throw new IllegalArgumentException("Doris two-step queries require one derived object");
		} else {
			return DorisSqlCompilerSupport.identifier(values.getJSONObject(0).getString("variable"), "derived alias");
		}
	}

	/**
	 * Collects output aliases and rejects duplicate names.
	 *
	 * @param output step output
	 * @return safe output aliases
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static Set<String> outputAliases(JSONObject output) {
		JSONArray fields = DorisSqlCompilerSupport.required(output, "fields");
		Set<String> aliases = new HashSet<>();
		for (Object value : fields) {
			String alias = DorisSqlCompilerSupport.identifier(((JSONObject) value).getString("as"), "output alias");
			if (!aliases.add(alias)) {
				throw new IllegalArgumentException("Duplicate DSL output alias: " + alias);
			} else {
				continue;
			}
		}
		return aliases;
	}

	/**
	 * Reads the first graph object's array.
	 *
	 * @param step DSL step
	 * @return graph objects
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static JSONArray objects(JSONObject step) {
		JSONObject graph = DorisSqlCompilerSupport.required(step, "graph");
		JSONArray patterns = DorisSqlCompilerSupport.required(graph, "patterns");
		JSONObject pattern = patterns.getJSONObject(0);
		JSONArray objects = DorisSqlCompilerSupport.required(pattern, "objects");
		if (objects.isEmpty()) {
			throw new IllegalArgumentException("DSL query must contain an object");
		} else {
			return objects;
		}
	}

	/**
	 * Validates and stores one graph object.
	 *
	 * @param object graph object
	 * @param position fallback object index
	 * @param context compilation context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void addObject(JSONObject object, int position, DorisSqlCompilerContext context) {
		Integer index = object.getInteger("idx");
		int resolvedIndex = index == null ? position : index;
		String alias = DorisSqlCompilerSupport.identifier(object.getString("variable"), "object alias");
		String className = DorisSqlCompilerSupport.identifier(object.getString("class"), "class name");
		context.objects.put(resolvedIndex, new DorisSqlCompilerContext.ObjectRef(resolvedIndex, alias,
				context.className(className)));
	}
}
