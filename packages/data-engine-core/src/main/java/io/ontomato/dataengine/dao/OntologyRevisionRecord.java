package io.ontomato.dataengine.dao;

import java.time.Instant;

/** Represents one immutable ontology definition revision.
 *
 * @author lvdaxianer@yeah.net
 */
public record OntologyRevisionRecord(
        String domainId,
        long revision,
        String content,
        String contentHash,
        String source,
        String actorId,
        Instant createdAt) {

    /** Creates a revision before PostgreSQL assigns its creation timestamp. */
    public OntologyRevisionRecord(
            String domainId,
            long revision,
            String content,
            String contentHash,
            String source,
            String actorId) {
        this(domainId, revision, content, contentHash, source, actorId, null);
    }

    /** Validates the immutable fields required by the revision store. */
    public OntologyRevisionRecord {
        requireText(domainId, "domainId");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        requireText(content, "content");
        requireText(contentHash, "contentHash");
        requireText(source, "source");
    }

    /** Validates a required text value without exposing database internals. */
    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
