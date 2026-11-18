package io.ontomato.dataengine.service.impl;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.alibaba.fastjson2.JSONObject;

/**
 * 表示指标视图执行链返回的受控失败。
 *
 * @author Codex
 * @date 2026-11-17
 */
public final class MetricViewExecutionException extends Exception {

    private static final int MAX_TEXT_LENGTH = 160;
    private static final String DEFAULT_CODE = "METRIC_VIEW_EXECUTION_FAILED";

    private final String errorCode;
    private final String safeMessage;
    private final Map<String, Object> details;
    private final String traceId;

    private MetricViewExecutionException(
            String errorCode,
            String safeMessage,
            Map<String, Object> details,
            String traceId) {
        super(safeMessage);
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
        this.details = Map.copyOf(details);
        this.traceId = traceId;
    }

    /**
     * 从 Python 的受控错误标记构造执行失败。
     *
     * @param payload 错误标记 JSON
     * @return 受控执行失败
     */
    public static MetricViewExecutionException fromPayload(JSONObject payload) {
        String code = safeText(payload.getString("errorCode"), DEFAULT_CODE);
        String message = safeMessage(code);
        String traceId = safeText(payload.getString("traceId"), UUID.randomUUID().toString());
        return new MetricViewExecutionException(code, message, safeDetails(payload.getJSONObject("details")), traceId);
    }

    /**
     * 为未知 Python 错误构造通用失败。
     *
     * @return 不暴露原始 stderr 的执行失败
     */
    public static MetricViewExecutionException unknown() {
        return new MetricViewExecutionException(
                DEFAULT_CODE,
                "指标视图测试失败，请联系管理员",
                Map.of(),
                UUID.randomUUID().toString());
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSafeMessage() {
        return safeMessage;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getTraceId() {
        return traceId;
    }

    private static Map<String, Object> safeDetails(JSONObject source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : new String[] {
                "parameter", "field", "path", "table", "databaseType", "dslLogicalType", "metricViewType",
                "actualType", "expectedType", "suggestion"}) {
            String value = safeText(source.getString(key), "");
            if (!value.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH
                || value.contains("\n") || value.contains("\r") || containsSensitiveTerm(value)) {
            return fallback;
        }
        return value;
    }

    private static String safeMessage(String errorCode) {
        if ("PARAMETER_FIELD_TYPE_MISMATCH".equals(errorCode)) {
            return "指标参数与字段类型不匹配";
        }
        if ("UNAUTHENTICATED".equals(errorCode)) {
            return "当前会话无权执行指标视图";
        }
        return "指标视图查询失败";
    }

    private static boolean containsSensitiveTerm(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("token")
                || normalized.contains("jdbc") || normalized.contains("select ")
                || normalized.contains(" from ");
    }
}
