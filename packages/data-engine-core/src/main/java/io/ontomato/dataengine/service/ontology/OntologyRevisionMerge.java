package io.ontomato.dataengine.service.ontology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Performs semantic three-way merges for ontology definitions.
 *
 * @author lvdaxianer@yeah.net
 */
public final class OntologyRevisionMerge {

    private static final Object MISSING = new Object();

    private OntologyRevisionMerge() {
    }

    /** Describes one field changed differently by the active and incoming revisions. */
    public record Conflict(String path, Object baseValue, Object currentValue, Object incomingValue) {
    }

    /** Contains the merged rule and all unresolved conflicts. */
    public record MergeResult(Map<String, Object> merged, List<Conflict> conflicts) {
        /** Indicates whether an explicit conflict decision is required before saving. */
        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }
    }

    /** Merges two edits against a common base using semantic ontology keys. */
    public static MergeResult merge(
            Map<String, Object> base,
            Map<String, Object> current,
            Map<String, Object> incoming) {
        if (base == null || current == null || incoming == null) {
            throw new IllegalArgumentException("base, current and incoming rules are required");
        }
        List<Conflict> conflicts = new ArrayList<>();
        Object merged = mergeValue(base, current, incoming, "$", conflicts);
        if (!(merged instanceof Map<?, ?> mergedMap)) {
            throw new IllegalStateException("Merged ontology rule must be an object");
        }
        return new MergeResult(copyMap(mergedMap), List.copyOf(conflicts));
    }

    /** Merges nested maps while retaining additions, deletions, and disjoint edits. */
    private static Object mergeValue(
            Object base,
            Object current,
            Object incoming,
            String path,
            List<Conflict> conflicts) {
        if (same(current, incoming)) {
            return copyValue(current);
        }
        if (same(current, base)) {
            return copyValue(incoming);
        }
        if (same(incoming, base)) {
            return copyValue(current);
        }
        if (current instanceof Map<?, ?> currentMap
                && incoming instanceof Map<?, ?> incomingMap
                && (base == MISSING || base instanceof Map<?, ?>)) {
            return mergeMaps(base, currentMap, incomingMap, path, conflicts);
        }
        if (current instanceof Collection<?> currentCollection
                && incoming instanceof Collection<?> incomingCollection
                && (base == MISSING || base instanceof Collection<?>)) {
            if (isSemanticArray(path)) {
                return mergeSemanticCollections(base, currentCollection, incomingCollection, path, conflicts);
            }
        }
        conflicts.add(new Conflict(
                path,
                copyValue(base),
                copyValue(current),
                copyValue(incoming)));
        return copyValue(current);
    }

    /** Merges object fields by key and represents conflicting scalar values as conflict records. */
    private static Map<String, Object> mergeMaps(
            Object base,
            Map<?, ?> current,
            Map<?, ?> incoming,
            String path,
            List<Conflict> conflicts) {
        Map<?, ?> baseMap = base instanceof Map<?, ?> map ? map : Collections.emptyMap();
        Set<String> keys = new LinkedHashSet<>();
        addKeys(keys, baseMap);
        addKeys(keys, current);
        addKeys(keys, incoming);
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String key : keys) {
            Object mergedValue = mergeValue(
                    valueOrMissing(baseMap, key),
                    valueOrMissing(current, key),
                    valueOrMissing(incoming, key),
                    path + "." + key,
                    conflicts);
            if (mergedValue != MISSING) {
                merged.put(key, mergedValue);
            }
        }
        return merged;
    }

    /** Merges class, attribute, indicator, and class-name arrays by their semantic key. */
    private static List<Object> mergeSemanticCollections(
            Object base,
            Collection<?> current,
            Collection<?> incoming,
            String path,
            List<Conflict> conflicts) {
        Collection<?> baseCollection = base instanceof Collection<?> collection ? collection : List.of();
        Map<String, Object> baseItems = indexBySemanticKey(baseCollection, path);
        Map<String, Object> currentItems = indexBySemanticKey(current, path);
        Map<String, Object> incomingItems = indexBySemanticKey(incoming, path);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(baseItems.keySet());
        keys.addAll(currentItems.keySet());
        keys.addAll(incomingItems.keySet());
        List<Object> merged = new ArrayList<>(keys.size());
        for (String key : keys) {
            Object mergedValue = mergeValue(
                    valueOrMissing(baseItems, key),
                    valueOrMissing(currentItems, key),
                    valueOrMissing(incomingItems, key),
                    path + semanticPathSuffix(path, key),
                    conflicts);
            if (mergedValue != MISSING) {
                merged.add(mergedValue);
            }
        }
        return merged;
    }

    /** Indexes a semantic collection without issuing any external calls. */
    private static Map<String, Object> indexBySemanticKey(Collection<?> values, String path) {
        Map<String, Object> indexed = new LinkedHashMap<>();
        for (Object value : values) {
            indexed.put(semanticKey(value, path), value);
        }
        return indexed;
    }

    /** Adds string keys from an arbitrary JSON object to a stable union. */
    private static void addKeys(Set<String> keys, Map<?, ?> values) {
        for (Object key : values.keySet()) {
            keys.add(Objects.toString(key, "null"));
        }
    }

    /** Returns a map value or a sentinel that distinguishes deletion from an explicit null. */
    private static Object valueOrMissing(Map<?, ?> values, String key) {
        return values.containsKey(key) ? values.get(key) : MISSING;
    }

    /** Identifies ontology arrays that can be merged by a business key. */
    private static boolean isSemanticArray(String path) {
        return path.endsWith(".classlist")
                || path.endsWith(".classDef")
                || path.endsWith(".attrs")
                || path.endsWith(".indicators");
    }

    /** Extracts the business key used by one ontology array. */
    private static String semanticKey(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            String field = path.endsWith(".classDef") ? "className"
                    : path.endsWith(".attrs") || path.endsWith(".indicators") ? "name" : "";
            return Objects.toString(map.get(field), "");
        }
        return Objects.toString(value, "");
    }

    /** Formats semantic collection keys as paths suitable for conflict resolution. */
    private static String semanticPathSuffix(String path, String key) {
        if (path.endsWith(".classDef")) {
            return "[className=" + key + "]";
        }
        if (path.endsWith(".attrs") || path.endsWith(".indicators")) {
            return "[name=" + key + "]";
        }
        return "[value=" + key + "]";
    }

    /** Compares JSON values while treating the internal missing sentinel as a value. */
    private static boolean same(Object left, Object right) {
        if (left == MISSING || right == MISSING) {
            return left == right;
        }
        return Objects.deepEquals(left, right);
    }

    /** Copies JSON values so callers cannot mutate the merge inputs through the result. */
    private static Object copyValue(Object value) {
        if (value == MISSING) {
            return MISSING;
        }
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }

    /** Copies a JSON object into a mutable string-keyed map. */
    private static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(Objects.toString(entry.getKey(), "null"), copyValue(entry.getValue()));
        }
        return copy;
    }
}
