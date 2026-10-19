package io.ontomato.dataengine.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.ontomato.dataengine.bean.metricView.MetricView;
import io.ontomato.dataengine.config.BusinessConfig;
import io.ontomato.dataengine.core.bean.User;
import io.ontomato.dataengine.service.BusinessConfigService;
import io.ontomato.dataengine.service.IdentityService;
import io.ontomato.dataengine.service.MetricViewService;
import io.ontomato.dataengine.service.sys.bean.permission.UserDataPermission;
import io.ontomato.dataengine.util.SpringBeanUtil;

/** Verifies the metric view execution response contract at the controller boundary. */
class MetricViewControllerExecuteTest {

    private final BusinessConfigService businessConfigService = mock(BusinessConfigService.class);
    private final IdentityService identityService = mock(IdentityService.class);
    private final MetricViewService metricViewService = mock(MetricViewService.class);
    private final MetricViewController controller = new MetricViewController();
    private final User user = mock(User.class);
    private final UserDataPermission permission = mock(UserDataPermission.class);

    @BeforeEach
    void setUp() {
        when(user.getDomainId()).thenReturn("d1");
        when(identityService.getCurrentUser()).thenReturn(user);
        when(identityService.getCurrentUserDataPermission()).thenReturn(permission);

        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(IdentityService.class, () -> identityService);
        context.refresh();
        new SpringBeanUtil().setApplicationContext(context);

        BusinessConfig config = new BusinessConfig();
        config.setDslCookerTimeout(60_000L);
        when(businessConfigService.get("d1")).thenReturn(config);

        ReflectionTestUtils.setField(controller, "identityService", identityService);
        ReflectionTestUtils.setField(controller, "businessConfigService", businessConfigService);
        ReflectionTestUtils.setField(controller, "metricViewService", metricViewService);
    }

    @Test
    void executeUnwrapsASingleMetricResultObject() throws Exception {
        JSONObject result = new JSONObject().fluentPut("totalEvents", 3);
        JSONArray results = new JSONArray();
        results.add(result);
        when(metricViewService.execute(eq("metric-1"), any(JSONObject.class), eq("zh-CN"), same(user),
                same(permission), anyInt())).thenReturn(results);

        JSONObject response = controller.execute("zh-CN", request());

        assertTrue(response.getBooleanValue("success"));
        assertEquals(result, response.get("data"));
        assertFalse(response.get("data") instanceof JSONArray);
    }

    @Test
    void executePreservesEmptyAndMultipleMetricResultsAsArrays() throws Exception {
        JSONArray results = new JSONArray();
        results.add(new JSONObject().fluentPut("name", "first"));
        results.add(new JSONObject().fluentPut("name", "second"));
        when(metricViewService.execute(eq("metric-1"), any(JSONObject.class), eq("zh-CN"), same(user),
                same(permission), anyInt())).thenReturn(results);

        JSONObject response = controller.execute("zh-CN", request());

        assertTrue(response.getBooleanValue("success"));
        assertEquals(results, response.get("data"));
        assertTrue(response.get("data") instanceof JSONArray);
    }

    @Test
    void executePreservesAnEmptyMetricResultAsAnArray() throws Exception {
        JSONArray results = new JSONArray();
        when(metricViewService.execute(eq("metric-1"), any(JSONObject.class), eq("zh-CN"), same(user),
                same(permission), anyInt())).thenReturn(results);

        JSONObject response = controller.execute("zh-CN", request());

        assertTrue(response.getBooleanValue("success"));
        assertEquals(results, response.get("data"));
        assertTrue(response.get("data") instanceof JSONArray);
    }

    @Test
    void testExecuteKeepsItsArrayResponse() throws Exception {
        JSONArray results = new JSONArray();
        results.add(new JSONObject().fluentPut("name", "first"));
        when(metricViewService.testExecute(any(MetricView.class), any(JSONObject.class), eq("zh-CN"), same(user),
                same(permission), anyInt())).thenReturn(results);

        JSONObject request = new JSONObject()
                .fluentPut("metricView", new JSONObject())
                .fluentPut("param", new JSONObject());
        JSONObject response = controller.testExecute("zh-CN", request);

        assertTrue(response.getBooleanValue("success"));
        assertEquals(results, response.get("data"));
        assertTrue(response.get("data") instanceof JSONArray);
    }

    private static JSONObject request() {
        return new JSONObject()
                .fluentPut("id", "metric-1")
                .fluentPut("param", new JSONObject());
    }
}
