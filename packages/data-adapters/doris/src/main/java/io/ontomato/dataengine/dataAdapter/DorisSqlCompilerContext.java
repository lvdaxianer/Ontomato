package io.ontomato.dataengine.dataAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds validated ontology metadata and mutable parameters for one compilation.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisSqlCompilerContext {

	final Map<Integer, ObjectRef> objects = new HashMap<>();
	final List<Object> parameters = new ArrayList<>();
	private final Map<String, Map<String, String>> attributes = new HashMap<>();
	private final Map<String, Map<String, Object>> relationships = new HashMap<>();

	/**
	 * Builds a compilation context from an ontology rule.
	 *
	 * @param rule ontology class and relationship definitions
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	DorisSqlCompilerContext(Map<String, Object> rule) {
		readClasses(rule);
		readRelationships(rule);
	}

	/**
	 * Resolves a mapped class name.
	 *
	 * @param className class name from the DSL
	 * @return validated class name
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	String className(String className) {
		if (!attributes.containsKey(className)) {
			throw new IllegalArgumentException("Class is not present in ontology mapping: " + className);
		} else {
			return className;
		}
	}

	/**
	 * Resolves a mapped attribute type.
	 *
	 * @param className mapped class name
	 * @param field mapped field name
	 * @return ontology type
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	String attributeType(String className, String field) {
		String type = attributes.getOrDefault(className, Map.of()).get(field);
		if (type == null) {
			throw new IllegalArgumentException("Field is not present in ontology mapping: " + className + "." + field);
		} else {
			return type;
		}
	}

	/**
	 * Resolves a relationship definition.
	 *
	 * @param name relationship name
	 * @return relationship definition
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	Map<String, Object> relationship(String name) {
		Map<String, Object> relationship = relationships.get(name);
		if (relationship == null) {
			throw new IllegalArgumentException("Relationship is not present in ontology mapping: " + name);
		} else {
			return relationship;
		}
	}

	/**
	 * Finds an object reference by DSL alias.
	 *
	 * @param alias DSL alias
	 * @return object reference
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	ObjectRef objectByAlias(String alias) {
		return objects.values().stream().filter(object -> object.alias().equals(alias)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown DSL alias: " + alias));
	}

	/**
	 * Reads all ontology class definitions into the context.
	 *
	 * @param rule ontology rule
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	@SuppressWarnings("unchecked")
	private void readClasses(Map<String, Object> rule) {
		Object definitions = rule.get("classDef");
		if (!(definitions instanceof List<?>)) {
			throw new IllegalArgumentException("Ontology class definitions are required");
		} else {
			for (Map<String, Object> definition : (List<Map<String, Object>>) definitions) {
				readClass(definition);
			}
		}
	}

	/**
	 * Reads one ontology class definition.
	 *
	 * @param definition class definition
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	@SuppressWarnings("unchecked")
	private void readClass(Map<String, Object> definition) {
		String className = DorisSqlCompilerSupport.identifier(String.valueOf(definition.get("className")), "class name");
		Map<String, String> types = new HashMap<>();
		List<Map<String, Object>> attrs = (List<Map<String, Object>>) definition.getOrDefault("attrs", List.of());
		for (Map<String, Object> attr : attrs) {
			types.put(DorisSqlCompilerSupport.identifier(String.valueOf(attr.get("name")), "attribute name"),
					String.valueOf(attr.get("type")));
		}
		attributes.put(className, types);
	}

	/**
	 * Reads relationship definitions when present.
	 *
	 * @param rule ontology rule
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	@SuppressWarnings("unchecked")
	private void readRelationships(Map<String, Object> rule) {
		Object definitions = rule.get("relationship_rule");
		if (definitions instanceof Map<?, ?>) {
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) definitions).entrySet()) {
				relationships.put(String.valueOf(entry.getKey()), (Map<String, Object>) entry.getValue());
			}
		} else {
			return;
		}
	}

	/** A validated DSL object and its mapped physical table. */
	record ObjectRef(int index, String alias, String className) {
	}
}
