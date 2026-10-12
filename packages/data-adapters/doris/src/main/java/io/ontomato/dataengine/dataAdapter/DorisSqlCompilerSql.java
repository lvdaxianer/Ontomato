package io.ontomato.dataengine.dataAdapter;

import java.util.Set;

import com.alibaba.fastjson2.JSONObject;

/**
 * Assembles complete Doris SELECT statements from validated SQL fragments.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerSql {

	private DorisSqlCompilerSql() {
	}

	/**
	 * Renders a complete one-step query.
	 *
	 * @param step source DSL step
	 * @param output output definition
	 * @param context compiler context
	 * @return one-step SELECT statement
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String renderSingle(JSONObject step, JSONObject output, DorisSqlCompilerContext context) {
		StringBuilder sql = new StringBuilder(renderInner(step, output, context));
		DorisSqlCompilerGroups.append(sql, output.getJSONObject("group_by"), context);
		DorisSqlCompilerExpressions.appendSort(sql, output.getJSONObject("sort"), context);
		DorisSqlCompilerExpressions.appendLimit(sql, output.getJSONObject("limit"));
		return sql.toString();
	}

	/**
	 * Renders the source portion of a query without outer aggregation clauses.
	 *
	 * @param step source DSL step
	 * @param output output definition
	 * @param context compiler context
	 * @return source SELECT statement
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String renderInner(JSONObject step, JSONObject output, DorisSqlCompilerContext context) {
		JSONObject pattern = DorisSqlCompilerPatterns.first(step);
		StringBuilder sql = new StringBuilder("SELECT\n    ");
		sql.append(DorisSqlCompilerExpressions.fields(output.getJSONArray("fields"), context));
		sql.append("\nFROM\n    ").append(DorisSqlCompilerJoins.from(pattern, context));
		String where = DorisSqlCompilerExpressions.where(pattern, context);
		if (!where.isEmpty()) {
			sql.append("\nWHERE\n    ").append(where);
		} else {
			return sql.toString();
		}
		return sql.toString();
	}

	/**
	 * Renders an outer query over a first-step derived table.
	 *
	 * @param inner source SELECT statement
	 * @param alias derived table alias
	 * @param sourceAliases aliases exposed by the inner query
	 * @param output outer output definition
	 * @param context compiler context
	 * @return two-step SELECT statement
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String renderDerived(String inner, String alias, Set<String> sourceAliases, JSONObject output,
			DorisSqlCompilerContext context) {
		StringBuilder sql = new StringBuilder("SELECT\n    ");
		sql.append(DorisSqlCompilerDerivedExpressions.fields(output.getJSONArray("fields"), alias, sourceAliases));
		sql.append("\nFROM (\n").append(inner).append("\n) AS ")
				.append(DorisSqlCompilerSupport.quote(alias));
		DorisSqlCompilerDerivedExpressions.appendGroup(sql, output.getJSONObject("group_by"), alias, sourceAliases,
				context);
		DorisSqlCompilerDerivedExpressions.appendSort(sql, output.getJSONObject("sort"), alias, sourceAliases, output);
		DorisSqlCompilerExpressions.appendLimit(sql, output.getJSONObject("limit"));
		return sql.toString();
	}
}
