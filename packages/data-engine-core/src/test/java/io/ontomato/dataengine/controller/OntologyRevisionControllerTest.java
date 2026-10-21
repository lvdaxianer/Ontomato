package io.ontomato.dataengine.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.core.bean.User;
import io.ontomato.dataengine.service.IdentityService;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService;
import io.ontomato.dataengine.service.ontology.OntologyRevisionSnapshot;
import io.ontomato.dataengine.util.SpringBeanUtil;

/** Verifies stable revision endpoint payloads and import request mapping.
 *
 * @author lvdaxianer@yeah.net
 */
class OntologyRevisionControllerTest {

    private final OntologyRevisionService revisionService = org.mockito.Mockito.mock(OntologyRevisionService.class);
    private final User user = org.mockito.Mockito.mock(User.class);
    private OntologyRevisionController controller;

    /** Wires the authenticated domain and revision service without starting Spring MVC. */
    @BeforeEach
    void setUp() {
        when(user.getDomainId()).thenReturn("domain-a");
        when(user.getId()).thenReturn("user-a");
        IdentityService identityService = org.mockito.Mockito.mock(IdentityService.class);
        when(identityService.getCurrentUser()).thenReturn(user);
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(IdentityService.class, () -> identityService);
        context.refresh();
        new SpringBeanUtil().setApplicationContext(context);

        controller = new OntologyRevisionController();
        ReflectionTestUtils.setField(controller, "revisionService", revisionService);
    }

    /** Verifies preview maps export metadata and client resolutions into the service request. */
    @Test
    void previewsAnImportWithStableConflictData() {
        Map<String, Object> rule = Map.of("classDef", List.of());
        OntologyRevisionSnapshot current = snapshot(2L, "current-hash", rule);
        OntologyRevisionSnapshot base = snapshot(1L, "base-hash", rule);
        OntologyRevisionService.ImportPreview preview = new OntologyRevisionService.ImportPreview(
                current,
                base,
                "incoming-hash",
                rule,
                List.of());
        when(revisionService.preview(any(), eq("domain-a"))).thenReturn(preview);

        JSONObject response = controller.previewImport(JSONObject.of(
                "baseRevision", 1,
                "baseHash", "base-hash",
                "content", rule,
                "resolutions", Map.of()));

        assertTrue(response.getBooleanValue("success"), response.toString());
        assertEquals(2L, response.getJSONObject("data").getLongValue("currentRevision"));
        assertTrue(response.getJSONObject("data").getBooleanValue("canApply"));
        verify(revisionService).preview(any(), eq("domain-a"));
    }

    /** Verifies stale writes retain a stable business error code for the conflict UI. */
    @Test
    void returnsStableCodeForRevisionConflicts() {
        when(revisionService.restore(any(), eq("domain-a"))).thenThrow(
                new io.ontomato.dataengine.service.ontology.OntologyRevisionException(
                        io.ontomato.dataengine.service.ontology.OntologyRevisionException.STALE_REVISION,
                        "stale"));

        JSONObject response = controller.restore(JSONObject.of("targetRevision", 1, "expectedCurrentRevision", 2,
                "expectedCurrentHash", "hash"));

        assertEquals(false, response.getBooleanValue("success"));
        assertEquals("ONTOLOGY_REVISION_STALE", response.getString("code"));
    }

    /** Creates a snapshot fixture for controller responses. */
    private static OntologyRevisionSnapshot snapshot(long revision, String hash, Map<String, Object> rule) {
        return new OntologyRevisionSnapshot("domain-a", revision, hash, "{}", rule, "manual", "user-a", null);
    }
}
