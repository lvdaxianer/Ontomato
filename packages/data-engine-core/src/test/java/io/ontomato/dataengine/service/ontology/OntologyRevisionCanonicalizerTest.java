package io.ontomato.dataengine.service.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Verifies deterministic ontology revision snapshots and semantic three-way merges.
 *
 * @author lvdaxianer@yeah.net
 */
class OntologyRevisionCanonicalizerTest {

    /** Verifies map and semantic list ordering does not create a false revision. */
    @Test
    void canonicalizesEquivalentRulesToTheSameContent() {
        Map<String, Object> first = rule(
                List.of(
                        classDefinition("Order", List.of(attribute("id"), attribute("amount"))),
                        classDefinition("Customer", List.of(attribute("id")))),
                List.of("Order", "Customer"));
        Map<String, Object> second = rule(
                List.of(
                        classDefinition("Customer", List.of(attribute("id"))),
                        classDefinition("Order", List.of(attribute("amount"), attribute("id")))),
                List.of("Order", "Customer").reversed());

        assertEquals(
                OntologyRevisionCanonicalizer.canonicalize(first),
                OntologyRevisionCanonicalizer.canonicalize(second));
        assertEquals(
                OntologyRevisionCanonicalizer.sha256(first),
                OntologyRevisionCanonicalizer.sha256(second));
    }

    /** Verifies independent edits from two branches are merged without conflict. */
    @Test
    void mergesIndependentChanges() {
        Map<String, Object> base = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount")))),
                List.of("Order"));
        Map<String, Object> current = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount", "订单金额")))),
                List.of("Order"));
        Map<String, Object> incoming = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount"), attribute("status")))),
                List.of("Order"));

        OntologyRevisionMerge.MergeResult result = OntologyRevisionMerge.merge(base, current, incoming);

        assertTrue(result.conflicts().isEmpty());
        Map<String, Object> mergedOrder = findClass(result.merged(), "Order");
        assertEquals("订单金额", findAttribute(mergedOrder, "amount").get("showName"));
        assertEquals("status", findAttribute(mergedOrder, "status").get("name"));
    }

    /** Verifies divergent edits to the same semantic field are reported as conflicts. */
    @Test
    void reportsConflictingChangesOnTheSameField() {
        Map<String, Object> base = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount")))),
                List.of("Order"));
        Map<String, Object> current = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount", "订单金额")))),
                List.of("Order"));
        Map<String, Object> incoming = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount", "金额")))),
                List.of("Order"));

        OntologyRevisionMerge.MergeResult result = OntologyRevisionMerge.merge(base, current, incoming);

        assertEquals(1, result.conflicts().size());
        assertEquals("$.classDef[className=Order].attrs[name=amount].showName", result.conflicts().get(0).path());
    }

    /** Verifies deleting a definition conflicts with an edit to that definition. */
    @Test
    void reportsDeleteVersusEditConflict() {
        Map<String, Object> base = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount")))),
                List.of("Order"));
        Map<String, Object> current = rule(
                List.of(classDefinition("Order", List.of(attribute("id")))),
                List.of("Order"));
        Map<String, Object> incoming = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount", "金额")))),
                List.of("Order"));

        OntologyRevisionMerge.MergeResult result = OntologyRevisionMerge.merge(base, current, incoming);

        assertEquals(1, result.conflicts().size());
        assertEquals("$.classDef[className=Order].attrs[name=amount]", result.conflicts().get(0).path());
    }

    /** Verifies an explicit incoming choice resolves a scalar conflict. */
    @Test
    void appliesAnIncomingConflictResolution() {
        Map<String, Object> base = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount")))),
                List.of("Order"));
        Map<String, Object> current = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount", "订单金额")))),
                List.of("Order"));
        Map<String, Object> incoming = rule(
                List.of(classDefinition("Order", List.of(attribute("id"), attribute("amount", "金额")))),
                List.of("Order"));
        String path = "$.classDef[className=Order].attrs[name=amount].showName";

        OntologyRevisionMerge.MergeResult result = OntologyRevisionMerge.merge(
                base, current, incoming, Map.of(path, "incoming"));

        assertTrue(result.conflicts().isEmpty());
        assertEquals("金额", findAttribute(findClass(result.merged(), "Order"), "amount").get("showName"));
    }

    /** Creates a compact internal ontology rule for merge tests. */
    private static Map<String, Object> rule(List<Map<String, Object>> classDefinitions, List<String> classNames) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("classlist", classNames);
        rule.put("classDef", classDefinitions);
        rule.put("relationship_rule", new LinkedHashMap<>());
        return rule;
    }

    /** Creates a minimal class definition for merge tests. */
    private static Map<String, Object> classDefinition(String name, List<Map<String, Object>> attributes) {
        Map<String, Object> classDefinition = new LinkedHashMap<>();
        classDefinition.put("className", name);
        classDefinition.put("showName", name);
        classDefinition.put("attrs", attributes);
        return classDefinition;
    }

    /** Creates an attribute definition with an optional display name. */
    private static Map<String, Object> attribute(String name) {
        return attribute(name, name);
    }

    /** Creates an attribute definition for merge tests. */
    private static Map<String, Object> attribute(String name, String showName) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("name", name);
        attribute.put("showName", showName);
        return attribute;
    }

    /** Finds one class by its semantic name. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findClass(Map<String, Object> rule, String name) {
        return ((List<Map<String, Object>>) rule.get("classDef")).stream()
                .filter(classDefinition -> name.equals(classDefinition.get("className")))
                .findFirst()
                .orElseThrow();
    }

    /** Finds one attribute by its semantic name. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findAttribute(Map<String, Object> classDefinition, String name) {
        return ((List<Map<String, Object>>) classDefinition.get("attrs")).stream()
                .filter(attribute -> name.equals(attribute.get("name")))
                .findFirst()
                .orElseThrow();
    }
}
