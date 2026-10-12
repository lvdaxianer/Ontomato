package io.ontomato.dataengine.dataAdapter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Shared validation, quoting, and parameter conversion for Doris SQL rendering.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerSupport {

	static final int DEFAULT_QUERY_LIMIT = 1000;
	static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	static final Set<String> OPERATORS = Set.of("=", "!=", "LIKE", "NOT LIKE", ">", ">=", "<", "<=", "BETWEEN",
			"IN", "IS", "IS NOT");
	static final Set<String> FUNCTIONS = Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");

	private DorisSqlCompilerSupport() {
	}

	/**
	 * Validates the two required compiler inputs.
	 *
	 * @param dsl DSL object
	 * @param jsonRule ontology mapping
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static void requireInputs(JSONObject dsl, Map<String, Object> jsonRule) {
		if (dsl == null || jsonRule == null) {
			throw new IllegalArgumentException("DSL and ontology mapping are required");
		} else {
			return;
		}
	}

	/**
	 * Validates the supported number of DSL steps.
	 *
	 * @param steps parsed DSL steps
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static void validateStepCount(JSONArray steps) {
		if (steps.isEmpty() || steps.size() > 2) {
			throw new IllegalArgumentException("Doris supports one or two DSL query steps");
		} else {
			return;
		}
	}

	/**
	 * Validates and returns an SQL identifier.
	 *
	 * @param value candidate identifier
	 * @param label error label
	 * @return validated identifier
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String identifier(String value, String label) {
		if (value == null || !IDENTIFIER.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid " + label + ": " + value);
		} else {
			return value;
		}
	}

	/**
	 * Quotes a validated Doris identifier.
	 *
	 * @param value validated identifier
	 * @return backtick-quoted identifier
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String quote(String value) {
		return "`" + value + "`";
	}

	/**
	 * Reads a required JSON value.
	 *
	 * @param object source object
	 * @param key required key
	 * @return required value
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	@SuppressWarnings("unchecked")
	static <T> T required(JSONObject object, String key) {
		if (object == null || object.get(key) == null) {
			throw new IllegalArgumentException("DSL is missing " + key);
		} else {
			return (T) object.get(key);
		}
	}

	/**
	 * Reads a required non-empty JSON array.
	 *
	 * @param array source array
	 * @param label missing-value label
	 * @return the same non-empty array
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static JSONArray required(JSONArray array, String label) {
		if (array == null || array.isEmpty()) {
			throw new IllegalArgumentException("DSL is missing " + label);
		} else {
			return array;
		}
	}

	/**
	 * Normalizes and validates a DSL operator.
	 *
	 * @param value operator from the DSL
	 * @return uppercase allowlisted operator
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String operator(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (!OPERATORS.contains(normalized)) {
			throw new IllegalArgumentException("Unsupported condition operator: " + value);
		} else {
			return normalized;
		}
	}

	/**
	 * Normalizes and validates an aggregate function.
	 *
	 * @param value function from the DSL
	 * @param label function error label
	 * @return uppercase allowlisted function
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String function(String value, String label) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (!FUNCTIONS.contains(normalized)) {
			throw new IllegalArgumentException("Unsupported " + label + ": " + value);
		} else {
			return normalized;
		}
	}

	/**
	 * Converts a condition value to a JDBC-safe type.
	 *
	 * @param type ontology attribute type
	 * @param value raw DSL value
	 * @return converted value
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static Object convert(String type, Object value) {
		if (value == null) {
			return null;
		} else {
			return convertNonNull(type, value);
		}
	}

	/**
	 * Converts a non-null value according to the ontology type.
	 *
	 * @param type ontology attribute type
	 * @param value non-null raw value
	 * @return converted value
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static Object convertNonNull(String type, Object value) {
		try {
			switch (type) {
			case "int" -> { return asNumber(value).intValue(); }
			case "long" -> { return asNumber(value).longValue(); }
			case "double" -> { return asNumber(value).doubleValue(); }
			case "decimal" -> { return value instanceof BigDecimal ? value : new BigDecimal(String.valueOf(value)); }
			case "date", "timestamp" -> { return asNumber(value).longValue(); }
			default -> { return String.valueOf(value); }
			}
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid " + type + " condition value", error);
		}
	}

	/**
	 * Converts numeric and textual input to a decimal source number.
	 *
	 * @param value raw value
	 * @return numeric value
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static Number asNumber(Object value) {
		if (value instanceof Number number) {
			return number;
		} else {
			return new BigDecimal(String.valueOf(value));
		}
	}

	/**
	 * Adds one converted parameter and returns its placeholder expression.
	 *
	 * @param type ontology attribute type
	 * @param value raw DSL value
	 * @param parameters mutable JDBC parameter list
	 * @return placeholder expression
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String parameter(String type, Object value, List<Object> parameters) {
		parameters.add(convert(type, value));
		if ("date".equals(type) || "timestamp".equals(type)) {
			return "FROM_UNIXTIME(?)";
		} else {
			return "?";
		}
	}
}
