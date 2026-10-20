package io.ontomato.dataengine.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONObject;
import io.ontomato.dataengine.core.bean.User;
import io.ontomato.dataengine.service.AdminService;
import io.ontomato.dataengine.service.IdentityService;
import io.ontomato.dataengine.util.SpringBeanUtil;

/** Verifies the admin API boundary for domain-scoped graph layout persistence. */
class AdminControllerGraphLayoutTest {

    private final AdminController controller = new AdminController();
    private final AdminService adminService = mock(AdminService.class);
    private final User user = mock(User.class);

    @BeforeEach
    void wire() {
        when(user.getDomainId()).thenReturn("domain-a");
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.getCurrentUser()).thenReturn(user);
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(IdentityService.class, () -> identityService);
        context.refresh();
        new SpringBeanUtil().setApplicationContext(context);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
    }

    /** Verifies reads use the current user's domain and return the service snapshot. */
    @Test
    void getsTheCurrentDomainsLayout() {
        JSONObject layout = JSONObject.of("positions", JSONObject.of("Order", JSONObject.of("x", 1, "y", 2)));
        when(adminService.getGraphLayout("domain-a")).thenReturn(layout);

        JSONObject result = controller.getGraphLayout();

        assertTrue(result.getBooleanValue("success"));
        assertSame(layout, result.getJSONObject("data"));
        verify(adminService).getGraphLayout("domain-a");
    }

    /** Verifies writes pass the payload to the current domain rather than trusting a client domain field. */
    @Test
    void savesTheCurrentDomainsLayout() {
        JSONObject payload = JSONObject.of("positions", JSONObject.of("Order", JSONObject.of("x", 10, "y", 20)));

        JSONObject result = controller.saveGraphLayout(payload);

        assertTrue(result.getBooleanValue("success"));
        verify(adminService).saveGraphLayout(payload, "domain-a");
    }

    /** Verifies service validation errors are returned as a failed stable response. */
    @Test
    void returnsFailureWhenTheServiceRejectsThePayload() {
        JSONObject payload = new JSONObject();
        doThrow(new IllegalArgumentException("positions is invalid"))
                .when(adminService).saveGraphLayout(any(JSONObject.class), eq("domain-a"));

        JSONObject result = controller.saveGraphLayout(payload);

        assertFalse(result.getBooleanValue("success"));
        assertEquals("positions is invalid", result.getString("message"));
    }
}
