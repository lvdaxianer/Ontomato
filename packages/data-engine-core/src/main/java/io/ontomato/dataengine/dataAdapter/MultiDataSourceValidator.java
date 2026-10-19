package io.ontomato.dataengine.dataAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.ontomato.dataengine.config.multisource.ClassRoute;
import io.ontomato.dataengine.config.multisource.DataSourceEntry;
import io.ontomato.dataengine.config.multisource.MultiDataSourceDefinition;
import io.ontomato.dataengine.config.multisource.RelationshipRoute;

/**
 * Finds what in a multi-source definition the router could not answer unambiguously.
 *
 * <p>Every problem is collected, so a half-configured definition is reported in one pass instead of one
 * error at a time. Problems that need the ontomap (an unrouted class, a cross-source relationship, two
 * relationships sharing a physical edge name in one source) are reported by
 * {@link MultiDataSourceAdapter#validateOntoMap}.</p>
 */
public final class MultiDataSourceValidator {

	private MultiDataSourceValidator() {
	}

	/** The problems of a definition; empty when every name routes to exactly one known, installed source. */
	public static List<ConfigurationProblem> problems(MultiDataSourceDefinition definition, DataAdapterRegistry registry) {
		List<ConfigurationProblem> problems = new ArrayList<>();
		if (definition.getDataSources() == null || definition.getClasses() == null || definition.getRelationships() == null) {
			problems.add(new ConfigurationProblem("multiDataSource",
					"dataSources, classes and relationships must each be a list"));
			return problems;
		}
		Set<String> sourceIds = new HashSet<>();
		if (definition.getDataSources().isEmpty()) {
			problems.add(new ConfigurationProblem("dataSources", "no data sources are configured"));
		}
		for (DataSourceEntry source : definition.getDataSources()) {
			if (source.getId() == null || source.getId().isBlank()) {
				problems.add(new ConfigurationProblem("dataSources[]", "a data source has no id"));
				continue;
			}
			String path = "dataSources[" + source.getId() + "]";
			if (!sourceIds.add(source.getId())) {
				problems.add(new ConfigurationProblem(path, "duplicate data source id: " + source.getId()));
			}
			if (source.getAdapterType() == null || source.getAdapterType().isBlank()) {
				problems.add(new ConfigurationProblem(path, "data source " + source.getId() + " has no adapter type"));
			} else if ("multi".equals(source.getAdapterType())) {
				// A structural rule, checked before the registry: nesting is illegal whether or not the multi
				// type happens to be installed, so it must not be reported as a mere missing installation.
				problems.add(new ConfigurationProblem(path, "data source " + source.getId() + " may not nest another multi source"));
			} else if (!registry.isInstalled(source.getAdapterType())) {
				problems.add(new ConfigurationProblem(path, "data source " + source.getId()
						+ " uses an adapter type not installed in this edition: " + source.getAdapterType()));
			}
		}

		Set<String> classNames = new HashSet<>();
		for (ClassRoute route : definition.getClasses()) {
			String key = DataSourceRouter.key(route.getForOntoMap());
			if (key == null) {
				problems.add(new ConfigurationProblem("classes[]", "a class route has no class"));
				continue;
			}
			String path = "classes[" + route.getForOntoMap() + "]";
			if (!classNames.add(key)) {
				problems.add(new ConfigurationProblem(path, "duplicate class route: " + route.getForOntoMap()));
			}
			if (!sourceIds.contains(route.getSourceId())) {
				problems.add(new ConfigurationProblem(path,
						"class route " + route.getForOntoMap() + " points at unknown source: " + route.getSourceId()));
			}
		}

		Set<String> relationshipNames = new HashSet<>();
		for (RelationshipRoute route : definition.getRelationships()) {
			String key = DataSourceRouter.key(route.getForOntoMap());
			if (key == null) {
				problems.add(new ConfigurationProblem("relationships[]", "a relationship route has no relationship"));
				continue;
			}
			if (!relationshipNames.add(key)) {
				problems.add(new ConfigurationProblem("relationships[" + route.getForOntoMap() + "]",
						"duplicate relationship route: " + route.getForOntoMap()));
			}
		}
		return problems;
	}

	/** @throws IllegalArgumentException when {@link #problems} finds any */
	public static void validate(MultiDataSourceDefinition definition, DataAdapterRegistry registry) {
		List<ConfigurationProblem> problems = problems(definition, registry);
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("Invalid multi-source configuration: "
					+ String.join("; ", problems.stream().map(ConfigurationProblem::detail).toList()));
		}
	}
}
