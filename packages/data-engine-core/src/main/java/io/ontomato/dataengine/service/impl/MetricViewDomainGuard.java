package io.ontomato.dataengine.service.impl;

import java.util.Objects;

import io.ontomato.dataengine.bean.metricView.MetricView;
import io.ontomato.dataengine.core.bean.User;
import io.ontomato.dataengine.util.SystemUtils;

final class MetricViewDomainGuard {

    private MetricViewDomainGuard() {
    }

    static boolean canAccess(MetricView metricView, User user) {
        if (metricView == null || user == null) {
            return false;
        }
        if (SystemUtils.SUPER_ADMIN.equals(user.getId()) && user.getDomainId() == null) {
            return true;
        }
        return user.getDomainId() != null && Objects.equals(metricView.getDomainId(), user.getDomainId());
    }
}
