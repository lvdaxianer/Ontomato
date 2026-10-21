package io.ontomato.dataengine.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.ontomato.dataengine.dao.JSONRuleDao;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService.RevisionSaveRequest;
import io.ontomato.dataengine.service.ontology.OntologyRevisionSnapshot;

/** Verifies logical Admin writes use the append-only revision service in production wiring. */
class AdminServiceRevisionWriteTest {

    private static final String DOMAIN_ID = "domain-a";

    /** Verifies the legacy write helper delegates to the active revision and never bypasses it. */
    @Test
    void routesLogicalOntologyWriteThroughRevisionService() {
        JSONRuleDao jsonRuleDao = org.mockito.Mockito.mock(JSONRuleDao.class);
        OntologyRevisionService revisionService = org.mockito.Mockito.mock(OntologyRevisionService.class);
        AdminServiceImpl service = new AdminServiceImpl();
        Map<String, Object> rule = emptyRule();
        OntologyRevisionSnapshot current = new OntologyRevisionSnapshot(
                DOMAIN_ID, 2L, "current-hash", "{}", rule, "manual", "user-a", null);
        ReflectionTestUtils.setField(service, "jsonRuleDao", jsonRuleDao);
        ReflectionTestUtils.setField(service, "ontologyRevisionService", revisionService);
        when(revisionService.current(DOMAIN_ID)).thenReturn(current);

        ReflectionTestUtils.invokeMethod(service, "writeJSONRule", rule, DOMAIN_ID);

        verify(revisionService).save(any(RevisionSaveRequest.class), eq(DOMAIN_ID));
        verify(jsonRuleDao, never()).save(any(), eq(DOMAIN_ID));
    }

    /** Creates the smallest valid rule for the Admin primary-key invariant. */
    private static Map<String, Object> emptyRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("classDef", java.util.List.of());
        rule.put("classlist", java.util.List.of());
        rule.put("relationship_rule", new LinkedHashMap<>());
        return rule;
    }
}
