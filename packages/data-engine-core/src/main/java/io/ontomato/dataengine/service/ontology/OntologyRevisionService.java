package io.ontomato.dataengine.service.ontology;

import java.util.List;
import java.util.Map;

/** Owns domain-scoped ontology revision lifecycle and import conflict decisions.
 *
 * @author lvdaxianer@yeah.net
 */
public interface OntologyRevisionService {

    /** Describes a save against an optional optimistic base revision. */
    record RevisionSaveRequest(
            Map<String, Object> rule,
            Long expectedRevision,
            String expectedHash,
            String source,
            String actorId) {
    }

    /** Describes an import or restore base and optional field-level conflict decisions. */
    record ImportRequest(
            Map<String, Object> rule,
            long baseRevision,
            String baseHash,
            Map<String, Object> resolutions,
            String source,
            String actorId) {
    }

    /** Returns a bounded preview and the unresolved semantic conflicts. */
    record ImportPreview(
            OntologyRevisionSnapshot current,
            OntologyRevisionSnapshot base,
            String incomingHash,
            Map<String, Object> mergedRule,
            List<OntologyRevisionMerge.Conflict> conflicts) {

        /** Indicates whether applying the preview is safe without more user choices. */
        public boolean canApply() {
            return conflicts.isEmpty();
        }
    }

    /** Describes a historical restore that always creates a new active revision. */
    record RestoreRequest(long targetRevision, Long expectedCurrentRevision, String expectedCurrentHash, String actorId) {
    }

    /** Returns the active revision, bootstrapping legacy json_rule data when necessary. */
    OntologyRevisionSnapshot current(String domainId);

    /** Returns newest-first revision metadata and content for a bounded history page. */
    List<OntologyRevisionSnapshot> list(String domainId, int limit, int offset);

    /** Returns one immutable revision for export or comparison. */
    OntologyRevisionSnapshot find(String domainId, long revision);

    /** Saves one logical ontology change or returns the current revision for a no-op. */
    OntologyRevisionSnapshot save(RevisionSaveRequest request, String domainId);

    /** Previews a three-way import without changing PostgreSQL state. */
    ImportPreview preview(ImportRequest request, String domainId);

    /** Applies a conflict-free or explicitly resolved import as a new revision. */
    OntologyRevisionSnapshot applyImport(ImportRequest request, String domainId);

    /** Restores historical content by creating a new active revision. */
    OntologyRevisionSnapshot restore(RestoreRequest request, String domainId);
}
