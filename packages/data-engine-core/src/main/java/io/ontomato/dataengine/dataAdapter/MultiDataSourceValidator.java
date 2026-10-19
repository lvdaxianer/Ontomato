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
 * Rejects a multi-source definition the router could not answer unambiguously.
 *
 * <p>Every problem is collected before throwing, so a half-configured definition is reported in one
 * pass instead of one error at a time. A {@code null} definition is a single-source domain and valid.</p>
 */
public final class MultiDataSourceValidator {

	private MultiDataSourceValidator() {
	}

	/** @throws IllegalArgumentException when the definition cannot route every name to exactly one source */
	public static void validate(MultiDataSourceDefinition definition, DataAdapterRegistry registry) {
		if (definition == null) {
			return;
		}
		List<String> problems = new ArrayList<>();
		Set<String> sourceIds = new HashSet<>();

		if (definition.getDataSources() == null || definition.getDataSources().isEmpty()) {
			problems.add("no data sources are configured");
		} else {
			for (DataSourceEntry source : definition.getDataSources()) {
				if (source.getId() == null || source.getId().isBlank()) {
					problems.add("a data source has no id");
					continue;
				}
				if (!sourceIds.add(source.getId())) {
					problems.add("duplicate data source id: " + source.getId());
				}
				if (source.getAdapterType() == null || source.getAdapterType().isBlank()) {
					problems.add("data source " + source.getId() + " has no adapterType");
					continue;
				}
				// A structural rule, checked before the registry: nesting is illegal whether or not the multi
				// type happens to be installed, so it must not be reported as a mere missing installation.
				if ("multi".equals(source.getAdapterType())) {
					problems.add("data source " + source.getId() + " may not nest another multi source");
					continue;
				}
				try {
					DataAdapterProvider provider = registry.provider(source.getAdapterType());
					if ("multi".equals(provider.type())) {
						problems.add("data source " + source.getId() + " may not nest another multi source");
					}
				} catch (IllegalArgumentException e) {
					problems.add("data source " + source.getId() + " uses an adapter type not installed in this edition: "
							+ source.getAdapterType());
				}
			}
		}

		Set<String> classNames = new HashSet<>();
		if (definition.getClasses() != null) {
			for (ClassRoute route : definition.getClasses()) {
				String key = DataSourceRouter.key(route.getForOntoMap());
				if (key == null) {
					problems.add("a class route has no forOntoMap");
					continue;
				}
				if (!classNames.add(key)) {
					problems.add("duplicate class route: " + route.getForOntoMap());
				}
				if (!sourceIds.contains(route.getSourceId())) {
					problems.add("class route " + route.getForOntoMap() + " points at unknown source: " + route.getSourceId());
				}
			}
		}

		Set<String> relationshipNames = new HashSet<>();
		if (definition.getRelationships() != null) {
			for (RelationshipRoute route : definition.getRelationships()) {
				String key = DataSourceRouter.key(route.getForOntoMap());
				if (key == null) {
					problems.add("a relationship route has no forOntoMap");
					continue;
				}
				if (!relationshipNames.add(key)) {
					problems.add("duplicate relationship route: " + route.getForOntoMap());
				}
				if (!sourceIds.contains(route.getSourceId())) {
					problems.add("relationship route " + route.getForOntoMap() + " points at unknown source: "
							+ route.getSourceId());
				}
			}
		}

		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("Invalid multi-source configuration: " + String.join("; ", problems));
		}
	}
}
