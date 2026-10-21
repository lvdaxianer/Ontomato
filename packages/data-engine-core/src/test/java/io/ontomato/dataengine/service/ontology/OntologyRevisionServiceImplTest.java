package io.ontomato.dataengine.service.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.ontomato.dataengine.dao.JSONRuleDao;
import io.ontomato.dataengine.dao.JSONRuleState;
import io.ontomato.dataengine.dao.OntologyRevisionDao;
import io.ontomato.dataengine.dao.OntologyRevisionRecord;

/** Verifies revision bootstrap, no-op saves, and stale-write protection.
 *
 * @author lvdaxianer@yeah.net
 */
class OntologyRevisionServiceImplTest {

    private static final String DOMAIN_ID = "domain-a";
    private final JSONRuleDao jsonRuleDao = org.mockito.Mockito.mock(JSONRuleDao.class);
    private final OntologyRevisionDao revisionDao = org.mockito.Mockito.mock(OntologyRevisionDao.class);
    private OntologyRevisionServiceImpl service;
    private Map<String, Object> rule;

    /** Creates a service with mocked PostgreSQL boundaries for each focused test. */
    @BeforeEach
    void setUp() {
        rule = emptyRule();
        service = new OntologyRevisionServiceImpl();
        ReflectionTestUtils.setField(service, "jsonRuleDao", jsonRuleDao);
        ReflectionTestUtils.setField(service, "ontologyRevisionDao", revisionDao);
    }

    /** Verifies an existing legacy rule is promoted without changing its definition. */
    @Test
    void bootstrapsAnExistingRuleAsRevisionOne() {
        String content = OntologyRevisionCanonicalizer.canonicalize(rule);
        when(jsonRuleDao.queryState(DOMAIN_ID)).thenReturn(Optional.of(new JSONRuleState(content, 0L, null)));
        when(jsonRuleDao.promote(eq(DOMAIN_ID), eq(0L), eq(null), eq(1L), eq(content), any(String.class)))
                .thenReturn(true);
        when(revisionDao.saveIfAbsent(any(OntologyRevisionRecord.class))).thenReturn(true);

        OntologyRevisionSnapshot snapshot = service.current(DOMAIN_ID);

        assertEquals(1L, snapshot.revision());
        assertEquals(content, snapshot.content());
        verify(jsonRuleDao).promote(eq(DOMAIN_ID), eq(0L), eq(null), eq(1L), eq(content), eq(snapshot.contentHash()));
        verify(revisionDao).saveIfAbsent(any(OntologyRevisionRecord.class));
    }

    /** Verifies saving equivalent content does not create a new revision. */
    @Test
    void doesNotCreateARevisionForAnEquivalentRule() {
        String content = OntologyRevisionCanonicalizer.canonicalize(rule);
        String hash = OntologyRevisionCanonicalizer.sha256(rule);
        when(jsonRuleDao.queryState(DOMAIN_ID)).thenReturn(Optional.of(new JSONRuleState(content, 2L, hash)));
        when(revisionDao.find(DOMAIN_ID, 2L)).thenReturn(Optional.of(
                new OntologyRevisionRecord(DOMAIN_ID, 2L, content, hash, "manual", "user-a")));

        OntologyRevisionSnapshot snapshot = service.save(new OntologyRevisionService.RevisionSaveRequest(
                rule, 2L, hash, "manual", "user-a"), DOMAIN_ID);

        assertEquals(2L, snapshot.revision());
        verify(revisionDao, never()).save(any(OntologyRevisionRecord.class));
        verify(jsonRuleDao, never()).promote(any(), eq(2L), any(), eq(3L), any(), any());
    }

    /** Verifies stale callers receive a conflict before any history row is appended. */
    @Test
    void rejectsAStaleWrite() {
        String content = OntologyRevisionCanonicalizer.canonicalize(rule);
        String hash = OntologyRevisionCanonicalizer.sha256(rule);
        when(jsonRuleDao.queryState(DOMAIN_ID)).thenReturn(Optional.of(new JSONRuleState(content, 2L, hash)));
        when(revisionDao.find(DOMAIN_ID, 2L)).thenReturn(Optional.of(
                new OntologyRevisionRecord(DOMAIN_ID, 2L, content, hash, "manual", "user-a")));

        OntologyRevisionException exception = assertThrows(OntologyRevisionException.class, () -> service.save(
                new OntologyRevisionService.RevisionSaveRequest(rule, 1L, "stale-hash", "manual", "user-b"), DOMAIN_ID));

        assertEquals(OntologyRevisionException.STALE_REVISION, exception.getCode());
        verify(revisionDao, never()).save(any(OntologyRevisionRecord.class));
    }

    /** Verifies an explicit import resolution is applied as the next active revision. */
    @Test
    void appliesAResolvedImportAsANewRevision() {
        Map<String, Object> baseRule = emptyRule();
        Map<String, Object> currentRule = ruleWithDescription("current");
        Map<String, Object> incomingRule = ruleWithDescription("incoming");
        String baseContent = OntologyRevisionCanonicalizer.canonicalize(baseRule);
        String currentContent = OntologyRevisionCanonicalizer.canonicalize(currentRule);
        String incomingPath = "$.datasetdesc";
        String currentHash = OntologyRevisionCanonicalizer.sha256(currentRule);
        String baseHash = OntologyRevisionCanonicalizer.sha256(baseRule);
        when(jsonRuleDao.queryState(DOMAIN_ID)).thenReturn(Optional.of(new JSONRuleState(currentContent, 2L, currentHash)));
        when(revisionDao.find(DOMAIN_ID, 1L)).thenReturn(Optional.of(
                new OntologyRevisionRecord(DOMAIN_ID, 1L, baseContent, baseHash, "manual", "user-a")));
        when(revisionDao.find(DOMAIN_ID, 2L)).thenReturn(Optional.of(
                new OntologyRevisionRecord(DOMAIN_ID, 2L, currentContent, currentHash, "manual", "user-a")));
        when(jsonRuleDao.promote(eq(DOMAIN_ID), eq(2L), eq(currentHash), eq(3L), any(String.class), any(String.class)))
                .thenReturn(true);

        OntologyRevisionSnapshot snapshot = service.applyImport(new OntologyRevisionService.ImportRequest(
                incomingRule, 1L, baseHash, Map.of(incomingPath, "incoming"), "import", "user-b"), DOMAIN_ID);

        assertEquals(3L, snapshot.revision());
        assertEquals("incoming", snapshot.rule().get("datasetdesc"));
        verify(revisionDao).save(any(OntologyRevisionRecord.class));
    }

    /** Creates the smallest valid internal ontology rule. */
    private static Map<String, Object> emptyRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("datasetdesc", "");
        rule.put("classlist", java.util.List.of());
        rule.put("classDef", java.util.List.of());
        rule.put("relationship_rule", new LinkedHashMap<>());
        return rule;
    }

    /** Creates a rule whose description is changed independently by each branch. */
    private static Map<String, Object> ruleWithDescription(String description) {
        Map<String, Object> rule = emptyRule();
        rule.put("datasetdesc", description);
        return rule;
    }
}
