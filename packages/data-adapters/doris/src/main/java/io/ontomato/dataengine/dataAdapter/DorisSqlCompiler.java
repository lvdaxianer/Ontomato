package io.ontomato.dataengine.dataAdapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Compiles the existing Ontology DSL into a bounded, parameterized Doris SELECT.
 * The compiler has no JDBC or mutation capabilities by design.
 */
public final class DorisSqlCompiler {

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Set<String> OPERATORS = Set.of("=", "!=", "like", "not like", ">", ">=", "<", "<=",
			"between", "in", "is", "is not");
	private static final Set<String> FUNCTIONS = Set.of("count", "sum", "avg", "min", "max");

	public DorisSql compile(JSONObject dsl, Map<String, Object> jsonRule) {
		if (dsl == null || jsonRule == null) {
			throw new IllegalArgumentException("DSL and ontology mapping are required");
		}
		CompilerContext context = new CompilerContext(jsonRule);
		JSONArray steps = required(dsl.getJSONObject("answer"), "steps");
		if (steps.isEmpty() || steps.size() > 2) {
			throw new IllegalArgumentException("Doris supports one or two DSL query steps");
		}
		return steps.size() == 1 ? compileSingleStep(steps.getJSONObject(0), context)
				: compileTwoStep(steps.getJSONObject(0), steps.getJSONObject(1), context);
	}

	private DorisSql compileSingleStep(JSONObject step, CompilerContext context) {
		populateObjects(step, context);
		JSONObject graph = required(step, "graph");
		JSONArray patterns = required(graph, "patterns");
		JSONObject pattern = patterns.getJSONObject(0);
		JSONArray objects = required(pattern, "objects");

		JSONObject output = required(step, "output");
		StringBuilder sql = new StringBuilder("select\n    ");
		sql.append(selectFields(output.getJSONArray("fields"), context));
		sql.append("\nfrom\n    ").append(fromClause(pattern.getJSONArray("relationship"), context));
		String where = whereClause(objects, context);
		if (!where.isEmpty()) {
			sql.append("\nwhere\n    ").append(where);
		}
		appendGroupBy(sql, output.getJSONObject("group_by"), context);
		appendSort(sql, output.getJSONObject("sort"), context);
		appendLimit(sql, output.getJSONObject("limit"));
		return new DorisSql(sql.toString(), context.parameters);
	}

	private DorisSql compileTwoStep(JSONObject firstStep, JSONObject secondStep, CompilerContext context) {
		populateObjects(firstStep, context);
		JSONObject firstOutput = required(firstStep, "output");
		String innerSql = baseSelect(firstStep, firstOutput, context);
		JSONObject secondGraph = required(secondStep, "graph");
		JSONArray secondPatterns = required(secondGraph, "patterns");
		JSONObject secondPattern = secondPatterns.getJSONObject(0);
		JSONArray secondObjects = required(secondPattern, "objects");
		if (secondObjects.size() != 1) {
			throw new IllegalArgumentException("Doris two-step queries require one derived object");
		}
		String derivedAlias = identifier(secondObjects.getJSONObject(0).getString("variable"), "derived alias");
		Set<String> firstAliases = outputAliases(firstOutput.getJSONArray("fields"));
		JSONObject secondOutput = required(secondStep, "output");
		Set<String> secondAliases = outputAliases(secondOutput.getJSONArray("fields"));
		StringBuilder sql = new StringBuilder("select\n    ")
				.append(secondSelectFields(secondOutput.getJSONArray("fields"), derivedAlias, firstAliases))
				.append("\nfrom (\n")
				.append(innerSql)
				.append("\n) as ")
				.append(quote(derivedAlias));
		appendSecondGroupBy(sql, secondOutput.getJSONObject("group_by"), derivedAlias, firstAliases, context);
		appendSecondSort(sql, secondOutput.getJSONObject("sort"), derivedAlias, firstAliases, secondAliases);
		appendLimit(sql, secondOutput.getJSONObject("limit"));
		return new DorisSql(sql.toString(), context.parameters);
	}

	private String baseSelect(JSONObject step, JSONObject output, CompilerContext context) {
		JSONObject graph = required(step, "graph");
		JSONArray patterns = required(graph, "patterns");
		JSONObject pattern = patterns.getJSONObject(0);
		JSONArray objects = required(pattern, "objects");
		StringBuilder sql = new StringBuilder("select\n    ")
				.append(selectFields(output.getJSONArray("fields"), context))
				.append("\nfrom\n    ").append(fromClause(pattern.getJSONArray("relationship"), context));
		String where = whereClause(objects, context);
		if (!where.isEmpty()) {
			sql.append("\nwhere\n    ").append(where);
		}
		return sql.toString();
	}

	private void populateObjects(JSONObject step, CompilerContext context) {
		JSONObject graph = required(step, "graph");
		JSONArray patterns = required(graph, "patterns");
		JSONObject pattern = patterns.getJSONObject(0);
		JSONArray objects = required(pattern, "objects");
		if (objects.isEmpty()) {
			throw new IllegalArgumentException("DSL query must contain an object");
		}
		for (int i = 0; i < objects.size(); i++) {
			JSONObject object = objects.getJSONObject(i);
			int index = object.getInteger("idx") == null ? i : object.getInteger("idx");
			String alias = identifier(object.getString("variable"), "object alias");
			String className = context.className(identifier(object.getString("class"), "class name"));
			context.objects.put(index, new ObjectRef(index, alias, className));
		}
	}

	private String selectFields(JSONArray fields, CompilerContext context) {
		if (fields == null || fields.isEmpty()) {
			throw new IllegalArgumentException("DSL output fields are required");
		}
		List<String> rendered = new ArrayList<>();
		for (Object value : fields) {
			JSONObject field = (JSONObject) value;
			String expression = fieldExpression(field, context, true);
			if (Boolean.TRUE.equals(field.getBoolean("distinct"))) {
				expression = "distinct " + expression;
			}
			String alias = identifier(field.getString("as"), "output alias");
			rendered.add(expression + " as " + quote(alias));
		}
		return String.join(",", rendered);
	}

	private Set<String> outputAliases(JSONArray fields) {
		if (fields == null || fields.isEmpty()) {
			throw new IllegalArgumentException("DSL output fields are required");
		}
		Set<String> aliases = new HashSet<>();
		for (Object value : fields) {
			aliases.add(identifier(((JSONObject) value).getString("as"), "output alias"));
		}
		return aliases;
	}

	private String secondSelectFields(JSONArray fields, String derivedAlias, Set<String> sourceAliases) {
		if (fields == null || fields.isEmpty()) {
			throw new IllegalArgumentException("DSL output fields are required");
		}
		List<String> rendered = new ArrayList<>();
		for (Object value : fields) {
			JSONObject field = (JSONObject) value;
			String expression = secondFieldExpression(field, derivedAlias, sourceAliases);
			if (Boolean.TRUE.equals(field.getBoolean("distinct"))) {
				expression = "distinct " + expression;
			}
			String alias = identifier(field.getString("as"), "output alias");
			rendered.add(expression + " as " + quote(alias));
		}
		return String.join(",", rendered);
	}

	private String secondFieldExpression(JSONObject field, String derivedAlias, Set<String> sourceAliases) {
		String sourceVariable = field.getString("variable");
		if (sourceVariable != null && !sourceVariable.isBlank()
				&& !derivedAlias.equals(identifier(sourceVariable, "derived field alias"))) {
			throw new IllegalArgumentException("Two-step output must reference the derived object");
		}
		String column = identifier(field.getString("field"), "derived field");
		if (!sourceAliases.contains(column)) {
			throw new IllegalArgumentException("Two-step output field is not present in the first step: " + column);
		}
		String expression = quote(derivedAlias) + "." + quote(column);
		String function = field.getString("function");
		if (function != null && !function.isBlank()) {
			String normalized = function.toLowerCase();
			if (!FUNCTIONS.contains(normalized)) {
				throw new IllegalArgumentException("Unsupported aggregate function: " + function);
			}
			expression = normalized + "(" + expression + ")";
		}
		return expression;
	}

	private void appendSecondGroupBy(StringBuilder sql, JSONObject groupBy, String derivedAlias,
			Set<String> sourceAliases, CompilerContext context) {
		if (groupBy == null || groupBy.getJSONArray("fields") == null || groupBy.getJSONArray("fields").isEmpty()) {
			return;
		}
		List<String> fields = new ArrayList<>();
		for (Object value : groupBy.getJSONArray("fields")) {
			JSONObject field = (JSONObject) value;
			String name = identifier(field.getString("field"), "derived group field");
			if (!sourceAliases.contains(name)) {
				throw new IllegalArgumentException("Two-step group field is not present in the first step: " + name);
			}
			fields.add(quote(derivedAlias) + "." + quote(name));
		}
		sql.append("\ngroup by\n    ").append(String.join(",", fields));
		JSONArray having = groupBy.getJSONArray("having");
		if (having == null || having.isEmpty()) {
			return;
		}
		List<String> clauses = new ArrayList<>();
		for (Object value : having) {
			JSONObject condition = (JSONObject) value;
			String function = condition.getString("function");
			String normalized = function == null ? "" : function.toLowerCase();
			if (!FUNCTIONS.contains(normalized)) {
				throw new IllegalArgumentException("Unsupported having function: " + function);
			}
			String field = identifier(condition.getString("field"), "derived having field");
			if (!sourceAliases.contains(field)) {
				throw new IllegalArgumentException("Two-step having field is not present in the first step: " + field);
			}
			String operator = condition.getString("operator");
			if (!(Set.of("=", "!=", ">", ">=", "<", "<=").contains(operator))) {
				throw new IllegalArgumentException("Unsupported having operator: " + operator);
			}
			String expression = normalized + "(" + quote(derivedAlias) + "." + quote(field) + ") " + operator + " "
					+ parameterExpression("long", condition.get("value"), context);
			clauses.add(expression);
		}
		sql.append("\nhaving\n    ").append(String.join(" and ", clauses));
	}

	private void appendSecondSort(StringBuilder sql, JSONObject sort, String derivedAlias, Set<String> sourceAliases,
			Set<String> outputAliases) {
		if (sort == null || sort.getJSONArray("fields") == null || sort.getJSONArray("fields").isEmpty()) {
			return;
		}
		List<String> fields = new ArrayList<>();
		for (Object value : sort.getJSONArray("fields")) {
			JSONObject field = (JSONObject) value;
			String name = identifier(field.getString("field"), "derived sort field");
			if (!sourceAliases.contains(name) && !outputAliases.contains(name)) {
				throw new IllegalArgumentException("Two-step sort field is not present in the query output: " + name);
			}
			String order = "desc".equalsIgnoreCase(field.getString("order")) ? " desc" : " asc";
			fields.add(quote(name) + order);
		}
		sql.append("\norder by\n    ").append(String.join(",", fields));
	}

	private String fromClause(JSONArray relationships, CompilerContext context) {
		if (relationships == null || relationships.isEmpty()) {
			if (context.objects.size() > 1) {
				throw new IllegalArgumentException("Doris query relationships must connect all objects");
			}
			ObjectRef first = context.objects.values().iterator().next();
			return quote(first.className) + " as " + quote(first.alias);
		}
		Set<Integer> included = new HashSet<>();
		ObjectRef first = context.objects.values().iterator().next();
		included.add(first.index);
		StringBuilder from = new StringBuilder(quote(first.className) + " as " + quote(first.alias));
		Set<Integer> consumed = new HashSet<>();
		while (included.size() < context.objects.size()) {
			boolean progress = false;
			for (int i = 0; i < relationships.size(); i++) {
				if (consumed.contains(i)) {
					continue;
				}
				JSONObject relationship = relationships.getJSONObject(i);
				int fromIndex = relationship.getIntValue("from");
				int toIndex = relationship.getIntValue("to");
				boolean fromIncluded = included.contains(fromIndex);
				boolean toIncluded = included.contains(toIndex);
				if (fromIncluded == toIncluded) {
					continue;
				}
				ObjectRef fromRef = context.objects.get(fromIndex);
				ObjectRef toRef = context.objects.get(toIndex);
				if (fromRef == null || toRef == null) {
					throw new IllegalArgumentException("Relationship references an unknown object");
				}
				Map<String, Object> relation = context.relationship(relationship.getJSONArray("type").getString(0));
				String fromField = identifier(String.valueOf(relation.get("fromField")), "relationship field");
				String toField = identifier(String.valueOf(relation.get("toField")), "relationship field");
				ObjectRef joining = fromIncluded ? toRef : fromRef;
				from.append(" inner join ").append(quote(joining.className)).append(" as ").append(quote(joining.alias))
						.append(" on ").append(quote(fromRef.alias)).append(".").append(quote(fromField))
						.append(" = ").append(quote(toRef.alias)).append(".").append(quote(toField));
				included.add(fromIndex);
				included.add(toIndex);
				consumed.add(i);
				progress = true;
			}
			if (!progress) {
				throw new IllegalArgumentException("Doris query relationships must form a connected graph");
			}
		}
		return from.toString();
	}

	private String whereClause(JSONArray objects, CompilerContext context) {
		List<String> clauses = new ArrayList<>();
		for (Object value : objects) {
			JSONObject object = (JSONObject) value;
			JSONObject conditions = object.getJSONObject("conditions");
			if (conditions == null || conditions.getJSONObject("properties") == null) {
				continue;
			}
			int index = object.getInteger("idx") == null ? objects.indexOf(value) : object.getInteger("idx");
			ObjectRef ref = context.objects.get(index);
			String clause = condition(conditions.getJSONObject("properties"), ref, context);
			if (!clause.isEmpty()) {
				clauses.add(clause);
			}
		}
		return String.join(" and ", clauses);
	}

	private String condition(JSONObject node, ObjectRef ref, CompilerContext context) {
		for (String connective : List.of("and", "or")) {
			JSONArray children = node.getJSONArray(connective);
			if (children != null) {
				if (children.isEmpty()) {
					return "";
				}
				List<String> rendered = new ArrayList<>();
				for (Object child : children) {
					String expression = condition((JSONObject) child, ref, context);
					if (!expression.isEmpty()) {
						rendered.add(expression);
					}
				}
				return rendered.isEmpty() ? "" : "(" + String.join(" " + connective + " ", rendered) + ")";
			}
		}
		String field = identifier(node.getString("field"), "condition field");
		String type = context.attributeType(ref.className, field);
		String operator = node.getString("operator");
		if (!OPERATORS.contains(operator)) {
			throw new IllegalArgumentException("Unsupported condition operator: " + operator);
		}
		String column = quote(ref.alias) + "." + quote(field);
		if ("is".equals(operator) || "is not".equals(operator)) {
			return column + " " + operator + " null";
		}
		if ("between".equals(operator)) {
			JSONArray values = node.getJSONArray("value");
			if (values == null || values.size() != 2) {
				throw new IllegalArgumentException("Between requires two values");
			}
			return column + " >= " + parameterExpression(type, values.get(0), context) + " and " + column + " <= "
					+ parameterExpression(type, values.get(1), context);
		}
		if ("in".equals(operator)) {
			JSONArray values = node.getJSONArray("value");
			if (values == null || values.isEmpty()) {
				throw new IllegalArgumentException("In requires at least one value");
			}
			List<String> placeholders = new ArrayList<>();
			for (Object value : values) {
				placeholders.add(parameterExpression(type, value, context));
			}
			return column + " in (" + String.join(",", placeholders) + ")";
		}
		Object value = node.get("value");
		if ("like".equals(operator) || "not like".equals(operator)) {
			String text = String.valueOf(value).trim();
			if (!text.startsWith("%") && !text.endsWith("%")) {
				text = "%" + text + "%";
			}
			value = text;
		}
		return column + " " + operator + " " + parameterExpression(type, value, context);
	}

	private String parameterExpression(String type, Object value, CompilerContext context) {
		context.parameters.add(convert(type, value));
		return "date".equals(type) || "timestamp".equals(type) ? "FROM_UNIXTIME(?)" : "?";
	}

	private Object convert(String type, Object value) {
		if (value == null) {
			return null;
		}
		try {
			return switch (type) {
			case "int" -> value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value));
			case "long" -> value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
			case "double" -> value instanceof Number ? ((Number) value).doubleValue() : Double.valueOf(String.valueOf(value));
			case "decimal" -> value instanceof BigDecimal ? value : new BigDecimal(String.valueOf(value));
			case "date", "timestamp" -> value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
			default -> String.valueOf(value);
			};
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid " + type + " condition value", error);
		}
	}

	private String fieldExpression(JSONObject field, CompilerContext context, boolean output) {
		String variable = field.getString("variable");
		String column = identifier(field.getString("field"), "output field");
		String expression;
		if (variable == null || variable.isBlank()) {
			expression = quote(column);
		} else {
			ObjectRef ref = context.objectByAlias(identifier(variable, "field alias"));
			context.attributeType(ref.className, column);
			expression = quote(ref.alias) + "." + quote(column);
		}
		String function = field.getString("function");
		if (function != null && !function.isBlank()) {
			String normalized = function.toLowerCase();
			if (!FUNCTIONS.contains(normalized)) {
				throw new IllegalArgumentException("Unsupported aggregate function: " + function);
			}
			expression = normalized + "(" + (Boolean.TRUE.equals(field.getBoolean("distinct")) ? "distinct " : "") + expression + ")";
		}
		return expression;
	}

	private void appendGroupBy(StringBuilder sql, JSONObject groupBy, CompilerContext context) {
		if (groupBy == null || groupBy.getJSONArray("fields") == null || groupBy.getJSONArray("fields").isEmpty()) {
			return;
		}
		List<String> fields = new ArrayList<>();
		for (Object value : groupBy.getJSONArray("fields")) {
			fields.add(fieldExpression((JSONObject) value, context, false));
		}
		sql.append("\ngroup by\n    ").append(String.join(",", fields));
		JSONArray having = groupBy.getJSONArray("having");
		if (having == null || having.isEmpty()) {
			return;
		}
		List<String> clauses = new ArrayList<>();
		for (Object value : having) {
			JSONObject condition = (JSONObject) value;
			String function = condition.getString("function");
			if (function == null || !FUNCTIONS.contains(function.toLowerCase())) {
				throw new IllegalArgumentException("Unsupported having function: " + function);
			}
			ObjectRef ref = context.objectByAlias(identifier(condition.getString("variable"), "having alias"));
			String field = identifier(condition.getString("field"), "having field");
			context.attributeType(ref.className, field);
			String operator = condition.getString("operator");
			if (!(Set.of("=", "!=", ">", ">=", "<", "<=").contains(operator))) {
				throw new IllegalArgumentException("Unsupported having operator: " + operator);
			}
			clauses.add(function.toLowerCase() + "(" + quote(ref.alias) + "." + quote(field) + ") " + operator + " "
					+ parameterExpression("long", condition.get("value"), context));
		}
		sql.append("\nhaving\n    ").append(String.join(" and ", clauses));
	}

	private void appendSort(StringBuilder sql, JSONObject sort, CompilerContext context) {
		if (sort == null || sort.getJSONArray("fields") == null || sort.getJSONArray("fields").isEmpty()) {
			return;
		}
		List<String> fields = new ArrayList<>();
		for (Object value : sort.getJSONArray("fields")) {
			JSONObject field = (JSONObject) value;
			String name = identifier(field.getString("field"), "sort field");
			String variable = field.getString("variable");
			String expression = variable == null || variable.isBlank() ? quote(name)
					: quote(context.objectByAlias(identifier(variable, "sort alias")).alias) + "." + quote(name);
			String order = "desc".equalsIgnoreCase(field.getString("order")) ? " desc" : " asc";
			fields.add(expression + order);
		}
		sql.append("\norder by\n    ").append(String.join(",", fields));
	}

	private void appendLimit(StringBuilder sql, JSONObject limit) {
		if (limit == null) {
			return;
		}
		int offset = limit.getInteger("offset") == null ? 0 : limit.getInteger("offset");
		Integer count = limit.getInteger("count");
		if (offset < 0 || count == null || count < 0) {
			throw new IllegalArgumentException("Limit offset and count must be non-negative");
		}
		sql.append("\nlimit ").append(count).append(" offset ").append(offset);
	}

	private static String identifier(String value, String label) {
		if (value == null || !IDENTIFIER.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid " + label + ": " + value);
		}
		return value;
	}

	private static String quote(String value) {
		return "`" + value + "`";
	}

	@SuppressWarnings("unchecked")
	private static <T> T required(JSONObject object, String key) {
		if (object == null || object.get(key) == null) {
			throw new IllegalArgumentException("DSL is missing " + key);
		}
		return (T) object.get(key);
	}

	@SuppressWarnings("unchecked")
	private static <T> T required(JSONArray array, String label) {
		if (array == null || array.isEmpty()) {
			throw new IllegalArgumentException("DSL is missing " + label);
		}
		return (T) array;
	}

	private static final class CompilerContext {
		private final Map<String, Map<String, String>> attributes = new HashMap<>();
		private final Map<String, Map<String, Object>> relationships = new HashMap<>();
		private final Map<Integer, ObjectRef> objects = new HashMap<>();
		private final List<Object> parameters = new ArrayList<>();

		@SuppressWarnings("unchecked")
		private CompilerContext(Map<String, Object> rule) {
			Object defs = rule.get("classDef");
			if (!(defs instanceof List<?>)) {
				throw new IllegalArgumentException("Ontology class definitions are required");
			}
			for (Map<String, Object> classDef : (List<Map<String, Object>>) defs) {
				String className = identifier(String.valueOf(classDef.get("className")), "class name");
				Map<String, String> attrTypes = new HashMap<>();
				for (Map<String, Object> attr : (List<Map<String, Object>>) classDef.getOrDefault("attrs", List.of())) {
					attrTypes.put(identifier(String.valueOf(attr.get("name")), "attribute name"), String.valueOf(attr.get("type")));
				}
				attributes.put(className, attrTypes);
			}
			Object relationDefs = rule.get("relationship_rule");
			if (relationDefs instanceof Map<?, ?>) {
				for (Map.Entry<?, ?> entry : ((Map<?, ?>) relationDefs).entrySet()) {
					relationships.put(String.valueOf(entry.getKey()), (Map<String, Object>) entry.getValue());
				}
			}
		}

		private String className(String className) {
			if (!attributes.containsKey(className)) {
				throw new IllegalArgumentException("Class is not present in ontology mapping: " + className);
			}
			return className;
		}

		private String attributeType(String className, String field) {
			String type = attributes.getOrDefault(className, Map.of()).get(field);
			if (type == null) {
				throw new IllegalArgumentException("Field is not present in ontology mapping: " + className + "." + field);
			}
			return type;
		}

		private Map<String, Object> relationship(String name) {
			Map<String, Object> relationship = relationships.get(name);
			if (relationship == null) {
				throw new IllegalArgumentException("Relationship is not present in ontology mapping: " + name);
			}
			return relationship;
		}

		private ObjectRef objectByAlias(String alias) {
			return objects.values().stream().filter(object -> object.alias.equals(alias)).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("Unknown DSL alias: " + alias));
		}
	}

	private record ObjectRef(int index, String alias, String className) {
	}
}
