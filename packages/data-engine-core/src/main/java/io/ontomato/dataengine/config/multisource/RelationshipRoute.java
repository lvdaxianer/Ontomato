package io.ontomato.dataengine.config.multisource;

import lombok.Data;

/**
 * Routes one ontology relationship type to one data source.
 *
 * <p>A route is only <em>needed</em> for a native-graph source whose physical edge name differs from
 * the ontology name, or when two graph sources would otherwise expose the same edge name. For a
 * relational source the join is already expressed by the {@code fromField}/{@code toField} of the
 * relationship definition in the ontomap, so nothing has to be repeated here.</p>
 *
 * <p>Because {@code fromClass}/{@code toClass} are unique class routes, a relationship whose two ends
 * land in different sources cannot be honoured — that is a cross-source graph query and is rejected.</p>
 */
@Data
public class RelationshipRoute {

	/** The ontology relationship name, globally unique across sources. */
	private String forOntoMap;

	/** The {@link DataSourceEntry#getId()} this relationship lives in. */
	private String sourceId;

	/**
	 * The physical edge name to use inside a native-graph source, when it differs from
	 * {@link #forOntoMap} or collides with another graph source's edge name. Ignored by non-graph sources.
	 */
	private String realEdge;
}
