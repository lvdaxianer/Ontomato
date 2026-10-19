package io.ontomato.dataengine.dataAdapter;

import java.util.LinkedHashMap;
import java.util.Map;

import io.ontomato.dataengine.config.multisource.ClassRoute;
import io.ontomato.dataengine.config.multisource.MultiDataSourceDefinition;
import io.ontomato.dataengine.config.multisource.RelationshipRoute;

/**
 * Maps an ontology class or relationship name to the id of the data source it lives in.
 *
 * <p>Pure lookups over the configured routes: it holds no adapter and touches no data. Because
 * {@code forOntoMap} values are unique (enforced when the configuration is validated), a class name
 * and a relationship name each resolve to exactly one source, so no name has to be prefixed per source.</p>
 */
public class DataSourceRouter {

	private final Map<String, String> classToSource = new LinkedHashMap<>();
	private final Map<String, RelationshipRoute> relationshipRoutes = new LinkedHashMap<>();

	public DataSourceRouter(MultiDataSourceDefinition definition) {
		if (definition == null) {
			return;
		}
		if (definition.getClasses() != null) {
			for (ClassRoute route : definition.getClasses()) {
				String key = key(route.getForOntoMap());
				if (key != null && route.getSourceId() != null) {
					classToSource.put(key, route.getSourceId());
				}
			}
		}
		if (definition.getRelationships() != null) {
			for (RelationshipRoute route : definition.getRelationships()) {
				String key = key(route.getForOntoMap());
				if (key != null && route.getSourceId() != null) {
					relationshipRoutes.put(key, route);
				}
			}
		}
	}

	/** The source id a class lives in, or {@code null} when unrouted. */
	public String sourceOfClass(String className) {
		return className == null ? null : classToSource.get(key(className));
	}

	/** The route of a relationship, or {@code null} when unrouted. */
	public RelationshipRoute routeOfRelationship(String relationshipName) {
		return relationshipName == null ? null : relationshipRoutes.get(key(relationshipName));
	}

	/**
	 * Normalize a name the way routing compares it: trimmed, with any leading slashes dropped, so
	 * {@code /Person} and {@code Person} route alike. This mirrors {@code DBSchemaUtil.normalizeClassName}'s
	 * slash tolerance, but without needing the class definitions — routing has to work on the sandbox write
	 * path, where no ontomap is in scope.
	 */
	static String key(String name) {
		if (name == null) {
			return null;
		}
		String key = name.trim();
		while (key.startsWith("/")) {
			key = key.substring(1);
		}
		return key;
	}
}
