package io.ontomato.dataengine.dataAdapter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Renders validated ontology relationships as Doris inner joins.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerJoins {

	private DorisSqlCompilerJoins() {
	}

	/**
	 * Renders the FROM clause for a connected graph.
	 *
	 * @param pattern graph pattern
	 * @param context compiler context
	 * @return quoted FROM and JOIN clauses
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	static String from(JSONObject pattern, DorisSqlCompilerContext context) {
		JSONArray relationships = pattern.getJSONArray("relationship");
		if (relationships == null || relationships.isEmpty()) {
			return singleTable(context);
		} else {
			return connectedTables(relationships, context);
		}
	}

	/**
	 * Renders the single-table form and rejects disconnected objects.
	 *
	 * @param context compiler context
	 * @return quoted table and alias
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String singleTable(DorisSqlCompilerContext context) {
		if (context.objects.size() > 1) {
			throw new IllegalArgumentException("Doris query relationships must connect all objects");
		} else {
			DorisSqlCompilerContext.ObjectRef first = firstObject(context);
			return DorisSqlCompilerSupport.quote(first.className()) + " AS "
					+ DorisSqlCompilerSupport.quote(first.alias());
		}
	}

	/**
	 * Walks relationships until every mapped object is included.
	 *
	 * @param relationships relationship array
	 * @param context compiler context
	 * @return connected join expression
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static String connectedTables(JSONArray relationships, DorisSqlCompilerContext context) {
		Set<Integer> included = new HashSet<>();
		Set<Integer> consumed = new HashSet<>();
		DorisSqlCompilerContext.ObjectRef first = firstObject(context);
		included.add(first.index());
		StringBuilder sql = new StringBuilder(DorisSqlCompilerSupport.quote(first.className()) + " AS "
				+ DorisSqlCompilerSupport.quote(first.alias()));
		joinUntilComplete(sql, relationships, context, included, consumed);
		return sql.toString();
	}

	/**
	 * Selects the lowest DSL object index as a stable join root.
	 *
	 * @param context compiler context
	 * @return stable first object
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static DorisSqlCompilerContext.ObjectRef firstObject(DorisSqlCompilerContext context) {
		return context.objects.values().stream().min(Comparator.comparingInt(DorisSqlCompilerContext.ObjectRef::index))
				.orElseThrow(() -> new IllegalArgumentException("Doris query objects are required"));
	}

	/**
	 * Consumes relationship edges while they attach to the included graph.
	 *
	 * @param sql SQL builder
	 * @param relationships relationship array
	 * @param context compiler context
	 * @param included included object indexes
	 * @param consumed consumed relationship indexes
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void joinUntilComplete(StringBuilder sql, JSONArray relationships,
			DorisSqlCompilerContext context, Set<Integer> included, Set<Integer> consumed) {
		while (included.size() < context.objects.size()) {
			boolean progress = consumeJoin(sql, relationships, context, included, consumed);
			if (!progress) {
				throw new IllegalArgumentException("Doris query relationships must form a connected graph");
			} else {
				continue;
			}
		}
	}

	/**
	 * Consumes every currently attachable relationship.
	 *
	 * @param sql SQL builder
	 * @param relationships relationship array
	 * @param context compiler context
	 * @param included included object indexes
	 * @param consumed consumed relationship indexes
	 * @return whether the graph advanced
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static boolean consumeJoin(StringBuilder sql, JSONArray relationships,
			DorisSqlCompilerContext context, Set<Integer> included, Set<Integer> consumed) {
		boolean progress = false;
		for (int position = 0; position < relationships.size(); position++) {
			if (consumed.contains(position)) {
				continue;
			} else {
				progress |= addJoin(sql, relationships.getJSONObject(position), position, context, included, consumed);
			}
		}
		return progress;
	}

	/**
	 * Adds one relationship when exactly one endpoint is already included.
	 *
	 * @param sql SQL builder
	 * @param relationship relationship definition
	 * @param position relationship index
	 * @param context compiler context
	 * @param included included object indexes
	 * @param consumed consumed relationship indexes
	 * @return whether a join was added
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static boolean addJoin(StringBuilder sql, JSONObject relationship, int position,
			DorisSqlCompilerContext context, Set<Integer> included, Set<Integer> consumed) {
		int fromIndex = relationship.getIntValue("from");
		int toIndex = relationship.getIntValue("to");
		boolean fromIncluded = included.contains(fromIndex);
		boolean toIncluded = included.contains(toIndex);
		if (fromIncluded == toIncluded) {
			return false;
		} else {
			appendJoin(sql, relationship, fromIndex, toIndex, fromIncluded, context);
			included.add(fromIndex);
			included.add(toIndex);
			consumed.add(position);
			return true;
		}
	}

	/**
	 * Appends one relationship's quoted join condition.
	 *
	 * @param sql SQL builder
	 * @param relationship relationship definition
	 * @param fromIndex source object index
	 * @param toIndex target object index
	 * @param context compiler context
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private static void appendJoin(StringBuilder sql, JSONObject relationship, int fromIndex, int toIndex,
			boolean fromIncluded, DorisSqlCompilerContext context) {
		DorisSqlCompilerContext.ObjectRef from = context.objects.get(fromIndex);
		DorisSqlCompilerContext.ObjectRef to = context.objects.get(toIndex);
		if (from == null || to == null) {
			throw new IllegalArgumentException("Relationship references an unknown object");
		} else {
			Map<String, Object> definition = context.relationship(relationship.getJSONArray("type").getString(0));
			DorisSqlCompilerContext.ObjectRef joining = fromIncluded ? to : from;
			String left = DorisSqlCompilerSupport.identifier(String.valueOf(definition.get("fromField")), "relationship field");
			String right = DorisSqlCompilerSupport.identifier(String.valueOf(definition.get("toField")), "relationship field");
			sql.append(" INNER JOIN ").append(DorisSqlCompilerSupport.quote(joining.className())).append(" AS ")
					.append(DorisSqlCompilerSupport.quote(joining.alias())).append(" ON ")
					.append(DorisSqlCompilerSupport.quote(from.alias())).append(".").append(DorisSqlCompilerSupport.quote(left))
					.append(" = ").append(DorisSqlCompilerSupport.quote(to.alias())).append(".")
					.append(DorisSqlCompilerSupport.quote(right));
		}
	}
}
