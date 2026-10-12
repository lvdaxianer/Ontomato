package io.ontomato.dataengine.dataAdapter;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Renders validated fields, predicates, grouping, sorting, and pagination.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerExpressions {

	private DorisSqlCompilerExpressions() {
	}

	/**
	 * Renders first-step output fields.
	 *
	 * @param fields field definitions
	 * @param context compiler context
	 * @return SELECT field list
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String fields(JSONArray fields, DorisSqlCompilerContext context) {
		if (fields == null || fields.isEmpty()) {
			throw new IllegalArgumentException("DSL output fields are required");
		} else {
			return joinFields(fields, context);
		}
	}

	/**
	 * Joins first-step field expressions.
	 *
	 * @param fields field definitions
	 * @param context compiler context
	 * @return comma-separated expressions
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String joinFields(JSONArray fields, DorisSqlCompilerContext context) {
		List<String> rendered = new ArrayList<>();
		for (Object value : fields) {
			rendered.add(field((JSONObject) value, context));
		}
		return String.join(",", rendered);
	}

	/**
	 * Renders one first-step field.
	 *
	 * @param field field definition
	 * @param context compiler context
	 * @return quoted field expression
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String field(JSONObject field, DorisSqlCompilerContext context) {
		String column = DorisSqlCompilerSupport.identifier(field.getString("field"), "output field");
		String variable = field.getString("variable");
		String expression = variable == null || variable.isBlank() ? DorisSqlCompilerSupport.quote(column)
				: qualifiedField(variable, column, context);
		String function = field.getString("function");
		String rendered = function == null || function.isBlank() ? expression
				: DorisSqlCompilerSupport.function(function, "aggregate function") + "(" + expression + ")";
		String distinct = Boolean.TRUE.equals(field.getBoolean("distinct")) ? "DISTINCT " : "";
		return distinct + rendered + " AS " + DorisSqlCompilerSupport.quote(outputAlias(field));
	}

	/**
	 * Validates one output alias.
	 *
	 * @param field field definition
	 * @return safe output alias
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String outputAlias(JSONObject field) {
		return DorisSqlCompilerSupport.identifier(field.getString("as"), "output alias");
	}

	/**
	 * Resolves one class-bound field.
	 *
	 * @param variable object alias
	 * @param column field name
	 * @param context compiler context
	 * @return qualified field
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String qualifiedField(String variable, String column, DorisSqlCompilerContext context) {
		String alias = DorisSqlCompilerSupport.identifier(variable, "field alias");
		DorisSqlCompilerContext.ObjectRef object = context.objectByAlias(alias);
		context.attributeType(object.className(), column);
		return DorisSqlCompilerSupport.quote(alias) + "." + DorisSqlCompilerSupport.quote(column);
	}

	/**
	 * Renders object predicates.
	 *
	 * @param pattern graph pattern
	 * @param context compiler context
	 * @return WHERE expression or empty text
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String where(JSONObject pattern, DorisSqlCompilerContext context) {
		List<String> clauses = new ArrayList<>();
		JSONArray objects = DorisSqlCompilerSupport.required(pattern, "objects");
		for (Object value : objects) {
			addObjectCondition(clauses, (JSONObject) value, context);
		}
		return String.join(" AND ", clauses);
	}

	/**
	 * Adds one object's properties condition.
	 *
	 * @param clauses WHERE clause list
	 * @param object graph object
	 * @param context compiler context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void addObjectCondition(List<String> clauses, JSONObject object,
			DorisSqlCompilerContext context) {
		JSONObject conditions = object.getJSONObject("conditions");
		JSONObject properties = conditions == null ? null : conditions.getJSONObject("properties");
		if (properties == null) {
			return;
		} else {
			String alias = DorisSqlCompilerSupport.identifier(object.getString("variable"), "condition alias");
			DorisSqlCompilerContext.ObjectRef ref = context.objectByAlias(alias);
			String clause = condition(properties, ref, context);
			if (!clause.isEmpty()) {
				clauses.add(clause);
			} else {
				return;
			}
		}
	}

	/**
	 * Renders a recursive AND/OR tree or one leaf predicate.
	 *
	 * @param node condition node
	 * @param ref object reference
	 * @param context compiler context
	 * @return SQL predicate
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String condition(JSONObject node, DorisSqlCompilerContext.ObjectRef ref,
			DorisSqlCompilerContext context) {
		JSONArray and = node.getJSONArray("and");
		JSONArray or = node.getJSONArray("or");
		if (and != null) {
			return compound(and, " AND ", ref, context);
		} else if (or != null) {
			return compound(or, " OR ", ref, context);
		} else {
			return predicate(node, ref, context);
		}
	}

	/**
	 * Renders one compound condition.
	 *
	 * @param values child nodes
	 * @param joiner SQL connective
	 * @param ref object reference
	 * @param context compiler context
	 * @return parenthesized expression
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String compound(JSONArray values, String joiner, DorisSqlCompilerContext.ObjectRef ref,
			DorisSqlCompilerContext context) {
		List<String> children = new ArrayList<>();
		for (Object value : values) {
			children.add(condition((JSONObject) value, ref, context));
		}
		return children.isEmpty() ? "" : "(" + String.join(joiner, children) + ")";
	}

	/**
	 * Renders one leaf predicate.
	 *
	 * @param node predicate node
	 * @param ref object reference
	 * @param context compiler context
	 * @return parameterized predicate
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String predicate(JSONObject node, DorisSqlCompilerContext.ObjectRef ref,
			DorisSqlCompilerContext context) {
		String field = DorisSqlCompilerSupport.identifier(node.getString("field"), "condition field");
		String type = context.attributeType(ref.className(), field);
		String operator = DorisSqlCompilerSupport.operator(node.getString("operator"));
		String column = DorisSqlCompilerSupport.quote(ref.alias()) + "." + DorisSqlCompilerSupport.quote(field);
		return switch (operator) {
		case "IS", "IS NOT" -> column + " " + operator + " NULL";
		case "BETWEEN" -> between(node, column, type, context);
		case "IN" -> in(node, column, type, context);
		default -> column + " " + operator + " " + valueParameter(node, type, operator, context);
		};
	}

	/**
	 * Renders a BETWEEN predicate.
	 */
	private static String between(JSONObject node, String column, String type, DorisSqlCompilerContext context) {
		JSONArray values = node.getJSONArray("value");
		if (values == null || values.size() != 2) {
			throw new IllegalArgumentException("Between requires two values");
		} else {
			String left = DorisSqlCompilerSupport.parameter(type, values.get(0), context.parameters);
			String right = DorisSqlCompilerSupport.parameter(type, values.get(1), context.parameters);
			return column + " >= " + left + " AND " + column + " <= " + right;
		}
	}

	/**
	 * Renders an IN predicate.
	 */
	private static String in(JSONObject node, String column, String type, DorisSqlCompilerContext context) {
		JSONArray values = node.getJSONArray("value");
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("In requires at least one value");
		} else {
			List<String> placeholders = new ArrayList<>();
			for (Object value : values) placeholders.add(DorisSqlCompilerSupport.parameter(type, value, context.parameters));
			return column + " IN (" + String.join(",", placeholders) + ")";
		}
	}

	/**
	 * Renders a scalar value predicate parameter.
	 */
	private static String valueParameter(JSONObject node, String type, String operator,
			DorisSqlCompilerContext context) {
		Object value = node.get("value");
		if ("LIKE".equals(operator) || "NOT LIKE".equals(operator)) {
			value = likeValue(value);
		} else {
			value = value;
		}
		return DorisSqlCompilerSupport.parameter(type, value, context.parameters);
	}

	/**
	 * Normalizes a LIKE value without concatenating it into SQL.
	 */
	private static String likeValue(Object value) {
		String text = String.valueOf(value).trim();
		if (!text.startsWith("%") && !text.endsWith("%")) {
			return "%" + text + "%";
		} else {
			return text;
		}
	}

	/**
	 * Appends one-step sorting.
	 */
	static void appendSort(StringBuilder sql, JSONObject sort, DorisSqlCompilerContext context) {
		if (sort == null || sort.getJSONArray("fields") == null || sort.getJSONArray("fields").isEmpty()) {
			return;
		} else {
			sql.append("\nORDER BY\n    ").append(sortFields(sort.getJSONArray("fields"), context));
		}
	}

	/**
	 * Renders one-step sort fields.
	 */
	private static String sortFields(JSONArray fields, DorisSqlCompilerContext context) {
		List<String> rendered = new ArrayList<>();
		for (Object value : fields) {
			JSONObject field = (JSONObject) value;
			String name = DorisSqlCompilerSupport.identifier(field.getString("field"), "sort field");
			String variable = field.getString("variable");
			String expression = variable == null || variable.isBlank() ? DorisSqlCompilerSupport.quote(name)
					: qualifiedField(variable, name, context);
			rendered.add(expression + sortOrder(field));
		}
		return String.join(",", rendered);
	}

	/**
	 * Returns a normalized sort direction.
	 */
	private static String sortOrder(JSONObject field) {
		return "desc".equalsIgnoreCase(field.getString("order")) ? " DESC" : " ASC";
	}

	/**
	 * Appends a bounded pagination clause.
	 */
	static void appendLimit(StringBuilder sql, JSONObject limit) {
		int offset = limit == null || limit.getInteger("offset") == null ? 0 : limit.getInteger("offset");
		int count = limit == null || limit.getInteger("count") == null ? DorisSqlCompilerSupport.DEFAULT_QUERY_LIMIT
				: limit.getInteger("count");
		if (offset < 0 || count < 0) {
			throw new IllegalArgumentException("Limit offset and count must be non-negative");
		} else {
			sql.append("\nLIMIT ").append(count).append(" OFFSET ").append(offset);
		}
	}

}
