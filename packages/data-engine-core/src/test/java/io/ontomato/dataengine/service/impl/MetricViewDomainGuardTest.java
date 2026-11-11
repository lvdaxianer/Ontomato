package io.ontomato.dataengine.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.ontomato.dataengine.bean.metricView.MetricView;
import io.ontomato.dataengine.service.sys.bean.UserDetail;

class MetricViewDomainGuardTest {

    @Test
    void ordinaryUserCanOnlyExecuteMetricViewFromTheSameDomain() {
        assertTrue(MetricViewDomainGuard.canAccess(metric("domain-a"), user("alice", "domain-a")));
        assertFalse(MetricViewDomainGuard.canAccess(metric("domain-b"), user("alice", "domain-a")));
        assertFalse(MetricViewDomainGuard.canAccess(metric(null), user("alice", "domain-a")));
    }

    @Test
    void domainlessSuperAdminRetainsGlobalAccess() {
        assertTrue(MetricViewDomainGuard.canAccess(metric("domain-a"), user("superAdmin", null)));
    }

    private static MetricView metric(String domainId) {
        MetricView metricView = new MetricView();
        metricView.setDomainId(domainId);
        return metricView;
    }

    private static UserDetail user(String id, String domainId) {
        UserDetail user = new UserDetail();
        user.setId(id);
        user.setDomainId(domainId);
        return user;
    }
}
