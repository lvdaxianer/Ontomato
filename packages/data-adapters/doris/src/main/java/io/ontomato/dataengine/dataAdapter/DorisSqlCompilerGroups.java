package io.ontomato.dataengine.dataAdapter;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Renders one-step GROUP BY and HAVING expressions.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerGroups {

	private DorisSqlCompilerGroups() {
	}

	/**
	 * Appends grouping and HAVING to a one-step query.
	 *
	 * @param sql SQL builder
	 * @param group grouping definition
	 * @param context compiler context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static void append(StringBuilder sql, JSONObject group, DorisSqlCompilerContext context) {
		if (group == null || group.getJSONArray("fields") == null || group.getJSONArray("fields").isEmpty()) {
			return;
		} else {
			sql.append("\nGROUP BY\n    ").append(fields(group.getJSONArray("fields"), context));
			appendHaving(sql, group.getJSONArray("having"), context);
		}
	}

	/**
	 * Renders one-step group fields without output aliases.
	 *
	 * @param fields group field definitions
	 * @param context compiler context
	 * @return comma-separated group fields
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String fields(JSONArray fields, DorisSqlCompilerContext context) {
		List<String> rendered = new ArrayList<>();
		for (Object value : fields) {
			rendered.add(field((JSONObject) value, context));
		}
		return String.join(",", rendered);
	}

	/**
	 * Renders one group field.
	 *
	 * @param field group field definition
	 * @param context compiler context
	 * @return qualified group field
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String field(JSONObject field, DorisSqlCompilerContext context) {
		String column = DorisSqlCompilerSupport.identifier(field.getString("field"), "group field");
		String variable = field.getString("variable");
		if (variable == null || variable.isBlank()) {
			return DorisSqlCompilerSupport.quote(column);
		} else {
			String alias = DorisSqlCompilerSupport.identifier(variable, "group alias");
			DorisSqlCompilerContext.ObjectRef object = context.objectByAlias(alias);
			context.attributeType(object.className(), column);
			return DorisSqlCompilerSupport.quote(alias) + "." + DorisSqlCompilerSupport.quote(column);
		}
	}

	/**
	 * Appends first-step HAVING predicates.
	 *
	 * @param sql SQL builder
	 * @param having HAVING definitions
	 * @param context compiler context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void appendHaving(StringBuilder sql, JSONArray having, DorisSqlCompilerContext context) {
		if (having == null || having.isEmpty()) {
			return;
		} else {
			List<String> clauses = new ArrayList<>();
			for (Object value : having) {
				clauses.add(havingClause((JSONObject) value, context));
			}
			sql.append("\nHAVING\n    ").append(String.join(" AND ", clauses));
		}
	}

	/**
	 * Renders one first-step HAVING predicate.
	 *
	 * @param value HAVING definition
	 * @param context compiler context
	 * @return parameterized HAVING predicate
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String havingClause(JSONObject value, DorisSqlCompilerContext context) {
		String function = DorisSqlCompilerSupport.function(value.getString("function"), "having function");
		String alias = DorisSqlCompilerSupport.identifier(value.getString("variable"), "having alias");
		DorisSqlCompilerContext.ObjectRef object = context.objectByAlias(alias);
		String field = DorisSqlCompilerSupport.identifier(value.getString("field"), "having field");
		context.attributeType(object.className(), field);
		String operator = DorisSqlCompilerSupport.operator(value.getString("operator"));
		String column = DorisSqlCompilerSupport.quote(alias) + "." + DorisSqlCompilerSupport.quote(field);
		return function + "(" + column + ") " + operator + " "
				+ DorisSqlCompilerSupport.parameter("long", value.get("value"), context.parameters);
	}
}
