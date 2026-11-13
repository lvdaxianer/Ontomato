package io.ontomato.dataengine.service.runtime;

import java.util.Map;

/** 供企业运行时解析器和测试使用的安全字面量占位符替换器。 */
public final class RuntimeVariableInjector {

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
        if (values == null || values.isEmpty()) return sourceCode;
        String result = sourceCode;
        for (Map.Entry<String, String> entry : values.entrySet()) {
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
        return result;
    }
}
