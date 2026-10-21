package io.ontomato.dataengine.service.ontology;

import java.time.Instant;
import java.util.Map;

/** Exposes one ontology revision without exposing persistence implementation details.
 *
 * @author lvdaxianer@yeah.net
 */
public record OntologyRevisionSnapshot(
        String domainId,
        long revision,
        String contentHash,
        String content,
        Map<String, Object> rule,
        String source,
        String actorId,
        Instant createdAt) {
}
