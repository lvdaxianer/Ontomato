package io.ontomato.dataengine.dao;

/** Represents the active ontology rule and its optimistic-concurrency metadata.
 *
 * @author lvdaxianer@yeah.net
 */
public record JSONRuleState(String content, long revision, String contentHash) {

    /** Validates the state returned by the active rule store. */
    public JSONRuleState {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }
}
