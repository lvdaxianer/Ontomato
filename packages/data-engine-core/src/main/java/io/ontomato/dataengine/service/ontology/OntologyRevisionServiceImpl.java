package io.ontomato.dataengine.service.ontology;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.dao.JSONRuleDao;
import io.ontomato.dataengine.dao.JSONRuleState;
import io.ontomato.dataengine.dao.OntologyRevisionDao;
import io.ontomato.dataengine.dao.OntologyRevisionRecord;

/** Implements append-only ontology revisions, optimistic writes, and safe imports.
 *
 * @author lvdaxianer@yeah.net
 */
@Service
public class OntologyRevisionServiceImpl implements OntologyRevisionService {

    private static final String DEFAULT_SOURCE = "manual";
    private static final String MIGRATION_SOURCE = "migration";
    private static final int MAX_SOURCE_LENGTH = 32;
    private static final int MAX_ACTOR_LENGTH = 128;

    @Autowired
    private JSONRuleDao jsonRuleDao;

    @Autowired
    private OntologyRevisionDao ontologyRevisionDao;

    /** Returns the active revision and lazily upgrades legacy json_rule rows. */
    @Override
    public OntologyRevisionSnapshot current(String domainId) {
        requireDomainId(domainId);
        JSONRuleState state = loadOrCreateActiveState(domainId);
        return ensureRevisionHistory(domainId, state);
    }

    /** Returns newest-first revision snapshots for a bounded history page. */
    @Override
    public List<OntologyRevisionSnapshot> list(String domainId, int limit, int offset) {
        current(domainId);
        List<OntologyRevisionRecord> records = ontologyRevisionDao.list(domainId, limit, offset);
        List<OntologyRevisionSnapshot> snapshots = new ArrayList<>(records.size());
        for (OntologyRevisionRecord record : records) {
            snapshots.add(toSnapshot(record));
        }
        return List.copyOf(snapshots);
    }

    /** Returns one revision after ensuring the domain has been bootstrapped. */
    @Override
    public OntologyRevisionSnapshot find(String domainId, long revision) {
        current(domainId);
        return ontologyRevisionDao.find(domainId, revision)
                .map(this::toSnapshot)
                .orElseThrow(() -> new OntologyRevisionException(
                        OntologyRevisionException.NOT_FOUND, "Ontology revision was not found"));
    }

    /** Saves one logical ontology change with an atomic optimistic promotion. */
    @Override
    @Transactional
    public OntologyRevisionSnapshot save(RevisionSaveRequest request, String domainId) {
        requireSaveRequest(request);
        OntologyRevisionSnapshot current = current(domainId);
        verifyExpectedBase(request.expectedRevision(), request.expectedHash(), current);
        String content = OntologyRevisionCanonicalizer.canonicalize(request.rule());
        String contentHash = OntologyRevisionCanonicalizer.sha256(request.rule());
        if (Objects.equals(contentHash, current.contentHash())) {
            return current;
        }

        long newRevision = current.revision() + 1;
        OntologyRevisionRecord record = new OntologyRevisionRecord(
                domainId,
                newRevision,
                content,
                contentHash,
                normalizeSource(request.source()),
                normalizeActor(request.actorId()));
        ontologyRevisionDao.save(record);
        promoteOrThrow(domainId, current, record);
        return toSnapshot(record);
    }

    /** Previews an import using the requested base, current active rule, and incoming content. */
    @Override
    public ImportPreview preview(ImportRequest request, String domainId) {
        requireImportRequest(request);
        OntologyRevisionSnapshot current = current(domainId);
        OntologyRevisionSnapshot base = find(domainId, request.baseRevision());
        if (!Objects.equals(base.contentHash(), request.baseHash())) {
            throw staleRevision();
        }
        String incomingHash = OntologyRevisionCanonicalizer.sha256(request.rule());
        OntologyRevisionMerge.MergeResult result = OntologyRevisionMerge.merge(
                base.rule(), current.rule(), request.rule());
        return new ImportPreview(current, base, incomingHash, result.merged(), result.conflicts());
    }

    /** Applies an import only after every conflict is absent or explicitly resolved. */
    @Override
    @Transactional
    public OntologyRevisionSnapshot applyImport(ImportRequest request, String domainId) {
        requireImportRequest(request);
        ImportPreview preview = preview(request, domainId);
        OntologyRevisionMerge.MergeResult result = OntologyRevisionMerge.merge(
                preview.base().rule(), preview.current().rule(), request.rule(), safeResolutions(request.resolutions()));
        if (result.hasConflicts()) {
            throw new OntologyRevisionException(
                    OntologyRevisionException.IMPORT_CONFLICT,
                    "Resolve all ontology import conflicts before applying the import");
        }
        return save(new RevisionSaveRequest(
                result.merged(),
                preview.current().revision(),
                preview.current().contentHash(),
                normalizeSource(request.source()),
                normalizeActor(request.actorId())), domainId);
    }

    /** Restores a historical revision as a new active revision without touching graph layout. */
    @Override
    @Transactional
    public OntologyRevisionSnapshot restore(RestoreRequest request, String domainId) {
        requireRestoreRequest(request);
        OntologyRevisionSnapshot target = find(domainId, request.targetRevision());
        OntologyRevisionSnapshot current = current(domainId);
        verifyExpectedBase(request.expectedCurrentRevision(), request.expectedCurrentHash(), current);
        return save(new RevisionSaveRequest(
                target.rule(),
                current.revision(),
                current.contentHash(),
                "restore",
                normalizeActor(request.actorId())), domainId);
    }

    /** Loads the active rule or creates a minimal valid rule before migration. */
    private JSONRuleState loadOrCreateActiveState(String domainId) {
        Optional<JSONRuleState> state = jsonRuleDao.queryState(domainId);
        if (state.isPresent()) {
            return state.get();
        }
        jsonRuleDao.save(emptyRule(), domainId);
        return jsonRuleDao.queryState(domainId).orElseThrow(() -> new OntologyRevisionException(
                OntologyRevisionException.INVALID_REQUEST, "Ontology rule could not be initialized"));
    }

    /** Converts legacy content into revision one and makes the history row idempotent. */
    private OntologyRevisionSnapshot ensureRevisionHistory(String domainId, JSONRuleState state) {
        Map<String, Object> rule = parseRule(state.content());
        String content = OntologyRevisionCanonicalizer.canonicalize(rule);
        String hash = OntologyRevisionCanonicalizer.sha256(rule);
        if (state.revision() == 0 || !Objects.equals(state.contentHash(), hash)) {
            long nextRevision = state.revision() == 0 ? 1L : state.revision() + 1L;
            boolean promoted = jsonRuleDao.promote(
                    domainId, state.revision(), state.contentHash(), nextRevision, content, hash);
            if (!promoted) {
                JSONRuleState refreshed = jsonRuleDao.queryState(domainId).orElseThrow(() -> new OntologyRevisionException(
                        OntologyRevisionException.STALE_REVISION, "Ontology revision changed during migration"));
                return ensureRevisionHistory(domainId, refreshed);
            }
            state = new JSONRuleState(content, nextRevision, hash);
        }

        OntologyRevisionRecord record = new OntologyRevisionRecord(
                domainId,
                state.revision(),
                content,
                hash,
                MIGRATION_SOURCE,
                null);
        ontologyRevisionDao.saveIfAbsent(record);
        return toSnapshot(record);
    }

    /** Promotes the active row and surfaces a stable stale-write conflict on compare-and-swap failure. */
    private void promoteOrThrow(String domainId, OntologyRevisionSnapshot current, OntologyRevisionRecord record) {
        boolean promoted = jsonRuleDao.promote(
                domainId,
                current.revision(),
                current.contentHash(),
                record.revision(),
                record.content(),
                record.contentHash());
        if (!promoted) {
            throw staleRevision();
        }
    }

    /** Validates the caller's base revision and hash against the active snapshot. */
    private static void verifyExpectedBase(Long expectedRevision, String expectedHash, OntologyRevisionSnapshot current) {
        if (expectedRevision == null && (expectedHash == null || expectedHash.isBlank())) {
            return;
        }
        if (expectedRevision == null || expectedHash == null || expectedHash.isBlank()
                || expectedRevision.longValue() != current.revision()
                || !Objects.equals(expectedHash, current.contentHash())) {
            throw staleRevision();
        }
    }

    /** Converts a persistent revision into the service contract. */
    private OntologyRevisionSnapshot toSnapshot(OntologyRevisionRecord record) {
        Map<String, Object> rule = parseRule(record.content());
        return new OntologyRevisionSnapshot(
                record.domainId(),
                record.revision(),
                record.contentHash(),
                record.content(),
                rule,
                record.source(),
                record.actorId(),
                record.createdAt());
    }

    /** Parses stored JSON into the map consumed by query and adapter code. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseRule(String content) {
        Object parsed = JSON.parse(content);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new OntologyRevisionException(
                    OntologyRevisionException.INVALID_REQUEST, "Ontology revision content must be a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    /** Creates the same empty ontology shape used by the legacy bootstrap path. */
    private static Map<String, Object> emptyRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("datasetdesc", "");
        rule.put("classlist", new JSONArray());
        rule.put("classDef", new JSONArray());
        rule.put("relationship_rule", new JSONObject());
        return rule;
    }

    /** Returns a copy of optional conflict decisions without exposing mutable request state. */
    private static Map<String, Object> safeResolutions(Map<String, Object> resolutions) {
        return resolutions == null ? Map.of() : new LinkedHashMap<>(resolutions);
    }

    /** Validates a revision save request before reading or writing PostgreSQL. */
    private static void requireSaveRequest(RevisionSaveRequest request) {
        if (request == null || request.rule() == null) {
            throw invalidRequest("Ontology rule is required");
        }
        if ((request.expectedRevision() == null) != (request.expectedHash() == null)) {
            throw invalidRequest("Expected revision and hash must be supplied together");
        }
    }

    /** Validates an import request before loading its base revision. */
    private static void requireImportRequest(ImportRequest request) {
        if (request == null || request.rule() == null || request.baseRevision() < 1
                || request.baseHash() == null || request.baseHash().isBlank()) {
            throw invalidRequest("Import content, base revision, and base hash are required");
        }
    }

    /** Validates a restore request before reading historical content. */
    private static void requireRestoreRequest(RestoreRequest request) {
        if (request == null || request.targetRevision() < 1) {
            throw invalidRequest("Target revision is required");
        }
    }

    /** Validates a domain identifier at the service boundary. */
    private static void requireDomainId(String domainId) {
        if (domainId == null || domainId.isBlank()) {
            throw invalidRequest("Domain id is required");
        }
    }

    /** Validates an externally supplied source label. */
    private static String normalizeSource(String source) {
        String normalized = source == null || source.isBlank() ? DEFAULT_SOURCE : source.trim();
        if (normalized.length() > MAX_SOURCE_LENGTH) {
            throw invalidRequest("Revision source is too long");
        }
        return normalized;
    }

    /** Masks absent actor metadata while bounding caller-controlled text. */
    private static String normalizeActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return null;
        }
        String normalized = actorId.trim();
        if (normalized.length() > MAX_ACTOR_LENGTH) {
            throw invalidRequest("Revision actor is too long");
        }
        return normalized;
    }

    /** Creates a stable stale-write exception for both revision and hash mismatches. */
    private static OntologyRevisionException staleRevision() {
        return new OntologyRevisionException(
                OntologyRevisionException.STALE_REVISION,
                "Ontology changed since this operation was opened; refresh and compare again");
    }

    /** Creates a stable validation exception without leaking implementation details. */
    private static OntologyRevisionException invalidRequest(String message) {
        return new OntologyRevisionException(OntologyRevisionException.INVALID_REQUEST, message);
    }
}
