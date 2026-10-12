package io.ontomato.dataengine.dataAdapter;

import java.util.List;

/**
 * Compiled read-only SQL and the values that must be bound to its placeholders.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
public record DorisSql(String sql, List<Object> parameters) {

	public DorisSql {
		parameters = List.copyOf(parameters);
	}
}
