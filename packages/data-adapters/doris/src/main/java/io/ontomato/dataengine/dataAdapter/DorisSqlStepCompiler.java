package io.ontomato.dataengine.dataAdapter;

import java.util.Set;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Compiles one-step and two-step Ontology DSL query shapes.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlStepCompiler {

	private final DorisSqlCompilerContext context;

	/**
	 * Creates a step compiler for one ontology mapping.
	 *
	 * @param context compilation context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	DorisSqlStepCompiler(DorisSqlCompilerContext context) {
		this.context = context;
	}

	/**
	 * Compiles the supported step count.
	 *
	 * @param steps DSL steps
	 * @return parameterized Doris SQL
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	DorisSql compile(JSONArray steps) {
		if (steps.size() == 1) {
			return compileSingle(steps.getJSONObject(0));
		} else {
			return compileTwo(steps.getJSONObject(0), steps.getJSONObject(1));
		}
	}

	/**
	 * Compiles the direct table query shape.
	 *
	 * @param step one DSL step
	 * @return parameterized Doris SQL
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private DorisSql compileSingle(JSONObject step) {
		DorisSqlCompilerObjects.populate(step, context);
		JSONObject output = DorisSqlCompilerSupport.required(step, "output");
		String sql = DorisSqlCompilerSql.renderSingle(step, output, context);
		return new DorisSql(sql, context.parameters);
	}

	/**
	 * Compiles the existing derived-subquery aggregation shape.
	 *
	 * @param firstStep source step
	 * @param secondStep derived step
	 * @return parameterized Doris SQL
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private DorisSql compileTwo(JSONObject firstStep, JSONObject secondStep) {
		DorisSqlCompilerObjects.populate(firstStep, context);
		JSONObject firstOutput = DorisSqlCompilerSupport.required(firstStep, "output");
		String inner = DorisSqlCompilerSql.renderInner(firstStep, firstOutput, context);
		String alias = DorisSqlCompilerObjects.derivedAlias(secondStep);
		Set<String> sourceAliases = DorisSqlCompilerObjects.outputAliases(firstOutput);
		JSONObject secondOutput = DorisSqlCompilerSupport.required(secondStep, "output");
		String sql = DorisSqlCompilerSql.renderDerived(inner, alias, sourceAliases, secondOutput, context);
		return new DorisSql(sql, context.parameters);
	}
}
