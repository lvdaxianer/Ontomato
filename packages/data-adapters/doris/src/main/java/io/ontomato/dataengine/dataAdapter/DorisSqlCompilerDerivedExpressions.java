package io.ontomato.dataengine.dataAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Renders expressions for the second step of a derived Doris query.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerDerivedExpressions {

	private DorisSqlCompilerDerivedExpressions() {
	}

	/**
	 * Renders second-step output fields.
	 *
	 * @param fields field definitions
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the first step
	 * @return SELECT field list
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String fields(JSONArray fields, String alias, Set<String> sourceAliases) {
		if (fields == null || fields.isEmpty()) {
			throw new IllegalArgumentException("DSL output fields are required");
		} else {
			List<String> rendered = new ArrayList<>();
			for (Object value : fields) {
				rendered.add(field((JSONObject) value, alias, sourceAliases));
			}
			return String.join(",", rendered);
		}
	}

	/**
	 * Renders one second-step output field.
	 *
	 * @param field field definition
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the first step
	 * @return field expression
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String field(JSONObject field, String alias, Set<String> sourceAliases) {
		String name = DorisSqlCompilerSupport.identifier(field.getString("field"), "derived field");
		validateSourceAlias(name, sourceAliases, "output");
		validateVariable(field.getString("variable"), alias);
		String expression = column(alias, name);
		String function = field.getString("function");
		String aggregate = function == null || function.isBlank() ? expression
				: DorisSqlCompilerSupport.function(function, "aggregate function") + "(" + expression + ")";
		String distinct = Boolean.TRUE.equals(field.getBoolean("distinct")) ? "DISTINCT " : "";
		return distinct + aggregate + " AS " + DorisSqlCompilerSupport.quote(outputAlias(field));
	}

	/**
	 * Validates an optional derived object variable.
	 *
	 * @param variable requested object variable
	 * @param alias derived object alias
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void validateVariable(String variable, String alias) {
		if (variable != null && !variable.isBlank()) {
			String normalized = DorisSqlCompilerSupport.identifier(variable, "derived field alias");
			if (!alias.equals(normalized)) {
				throw new IllegalArgumentException("Two-step output must reference the derived object");
			} else {
				return;
			}
		} else {
			return;
		}
	}

	/**
	 * Appends second-step grouping and HAVING.
	 *
	 * @param sql SQL builder
	 * @param group grouping definition
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the first step
	 * @param context compiler context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static void appendGroup(StringBuilder sql, JSONObject group, String alias, Set<String> sourceAliases,
			DorisSqlCompilerContext context) {
		if (group == null || group.getJSONArray("fields") == null || group.getJSONArray("fields").isEmpty()) {
			return;
		} else {
			sql.append("\nGROUP BY\n    ").append(groupFields(group.getJSONArray("fields"), alias, sourceAliases));
			appendHaving(sql, group.getJSONArray("having"), alias, sourceAliases, context);
		}
	}

	/**
	 * Renders second-step group fields.
	 *
	 * @param fields group field definitions
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the first step
	 * @return comma-separated group fields
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String groupFields(JSONArray fields, String alias, Set<String> sourceAliases) {
		List<String> rendered = new ArrayList<>();
		for (Object value : fields) {
			String name = DorisSqlCompilerSupport.identifier(((JSONObject) value).getString("field"),
					"derived group field");
			validateSourceAlias(name, sourceAliases, "group");
			rendered.add(column(alias, name));
		}
		return String.join(",", rendered);
	}

	/**
	 * Appends second-step HAVING predicates.
	 *
	 * @param sql SQL builder
	 * @param having HAVING definitions
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the first step
	 * @param context compiler context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void appendHaving(StringBuilder sql, JSONArray having, String alias,
			Set<String> sourceAliases, DorisSqlCompilerContext context) {
		if (having == null || having.isEmpty()) {
			return;
		} else {
			List<String> clauses = new ArrayList<>();
			for (Object value : having) {
				clauses.add(havingClause((JSONObject) value, alias, sourceAliases, context));
			}
			sql.append("\nHAVING\n    ").append(String.join(" AND ", clauses));
		}
	}

	/**
	 * Renders one second-step HAVING predicate.
	 *
	 * @param value HAVING definition
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the first step
	 * @param context compiler context
	 * @return parameterized HAVING predicate
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String havingClause(JSONObject value, String alias, Set<String> sourceAliases,
			DorisSqlCompilerContext context) {
		String name = DorisSqlCompilerSupport.identifier(value.getString("field"), "derived having field");
		validateSourceAlias(name, sourceAliases, "having");
		String function = DorisSqlCompilerSupport.function(value.getString("function"), "having function");
		String operator = DorisSqlCompilerSupport.operator(value.getString("operator"));
		return function + "(" + column(alias, name) + ") " + operator + " "
				+ DorisSqlCompilerSupport.parameter("long", value.get("value"), context.parameters);
	}

	/**
	 * Appends second-step sorting.
	 *
	 * @param sql SQL builder
	 * @param sort sort definition
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the first step
	 * @param output output definition
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static void appendSort(StringBuilder sql, JSONObject sort, String alias, Set<String> sourceAliases,
			JSONObject output) {
		if (sort == null || sort.getJSONArray("fields") == null || sort.getJSONArray("fields").isEmpty()) {
			return;
		} else {
			Set<String> outputAliases = DorisSqlCompilerObjects.outputAliases(output);
			sql.append("\nORDER BY\n    ").append(sortFields(sort.getJSONArray("fields"), sourceAliases, outputAliases));
		}
	}

	/**
	 * Renders second-step sort fields.
	 *
	 * @param fields sort field definitions
	 * @param sourceAliases aliases exposed by the first step
	 * @param outputAliases aliases produced by the second step
	 * @return comma-separated sort fields
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String sortFields(JSONArray fields, Set<String> sourceAliases, Set<String> outputAliases) {
		List<String> rendered = new ArrayList<>();
		for (Object value : fields) {
			JSONObject field = (JSONObject) value;
			String name = DorisSqlCompilerSupport.identifier(field.getString("field"), "derived sort field");
			validateSortField(name, sourceAliases, outputAliases);
			rendered.add(DorisSqlCompilerSupport.quote(name) + sortOrder(field));
		}
		return String.join(",", rendered);
	}

	/**
	 * Validates one second-step sort field.
	 *
	 * @param name sort field name
	 * @param sourceAliases aliases exposed by the first step
	 * @param outputAliases aliases produced by the second step
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void validateSortField(String name, Set<String> sourceAliases, Set<String> outputAliases) {
		if (!sourceAliases.contains(name) && !outputAliases.contains(name)) {
			throw new IllegalArgumentException("Two-step sort field is not present in the query output: " + name);
		} else {
			return;
		}
	}

	/**
	 * Validates a field against the first-step aliases.
	 *
	 * @param name field name
	 * @param sourceAliases aliases exposed by the first step
	 * @param label validation label
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void validateSourceAlias(String name, Set<String> sourceAliases, String label) {
		if (!sourceAliases.contains(name)) {
			throw new IllegalArgumentException("Two-step " + label + " field is not present in the first step: " + name);
		} else {
			return;
		}
	}

	/**
	 * Quotes a derived column reference.
	 *
	 * @param alias derived table alias
	 * @param name column name
	 * @return quoted column reference
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String column(String alias, String name) {
		return DorisSqlCompilerSupport.quote(alias) + "." + DorisSqlCompilerSupport.quote(name);
	}

	/**
	 * Reads and validates an output alias.
	 *
	 * @param field output field definition
	 * @return output alias
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String outputAlias(JSONObject field) {
		return DorisSqlCompilerSupport.identifier(field.getString("as"), "output alias");
	}

	/**
	 * Renders a normalized sort direction.
	 *
	 * @param field sort field definition
	 * @return SQL sort direction
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String sortOrder(JSONObject field) {
		return "desc".equalsIgnoreCase(field.getString("order")) ? " DESC" : " ASC";
	}
}
