package io.ontomato.dataengine.service.runtime;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 供企业运行时解析器和测试使用的安全字面量占位符替换器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-14
 */
public final class RuntimeVariableInjector {

    private static final Pattern PLACEHOLDER = Pattern.compile("__[A-Za-z0-9_-]{1,127}");
    /** Python 内置名称和指标视图协议标记允许在注入后原样保留。 */
    private static final Set<String> RESERVED_LITERALS = Set.of(
            "__main__",
            "__name__",
            "__file__",
            "__builtins__",
            "__METRIC_VIEW_ERROR__");

    private RuntimeVariableInjector() {}

    /**
     * 使用字面量替换变量，不把解析值当作正则表达式处理。
     *
     * @param sourceCode 占位符源码
     * @param values 占位符和值
     * @return 替换后的源码
     */
    public static String inject(String sourceCode, Map<String, String> values) {
        if (sourceCode == null) throw new IllegalArgumentException("Python 源码不能为空");
        String result = sourceCode;
        for (Map.Entry<String, String> entry : values == null ? Map.<String, String>of().entrySet() : values.entrySet()) {
            String placeholder = entry.getKey();
            String value = entry.getValue();
            if (placeholder == null || !placeholder.matches("__[A-Za-z0-9_-]{1,127}")) {
                throw new IllegalArgumentException("运行时变量占位符格式不正确：" + placeholder);
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("运行时变量 " + placeholder + " 的值不能为空");
            }
            result = result.replace(placeholder, value);
        }
        Matcher matcher = PLACEHOLDER.matcher(result);
        while (matcher.find()) {
            String remaining = matcher.group();
            if (!RESERVED_LITERALS.contains(remaining)) {
                throw new IllegalArgumentException("替换后仍存在未解析的运行时变量：" + remaining);
            }
        }
        return result;
    }
}
