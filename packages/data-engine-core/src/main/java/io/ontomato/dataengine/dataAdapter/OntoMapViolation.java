package io.ontomato.dataengine.dataAdapter;

/**
 * One problem an adapter finds in a domain's ontomap under its own routing rules.
 *
 * <p>An adapter reports what it cannot honour rather than the management layer knowing any adapter's
 * constraints: the default implementation of {@link DataAdapter#validateOntoMap} returns none, and an
 * adapter that constrains the ontomap (for example the multi-source adapter, whose cross-source
 * relationships cannot be a graph query) returns the located problems.</p>
 *
 * @param path   a JSON-pointer-like locator into the ontomap, e.g. {@code relationshipDefs[person_knows]}
 * @param detail a human-readable sentence naming the offending construct
 */
public record OntoMapViolation(String path, String detail) {
}
