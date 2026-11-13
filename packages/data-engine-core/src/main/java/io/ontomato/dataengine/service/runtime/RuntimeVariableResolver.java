package io.ontomato.dataengine.service.runtime;

/**
 * 在 Python 程序执行前解析模型可见的变量占位符。
 *
 * <p>实现只能读取可信配置或请求上下文，不能持久化解析后的代码、在日志中输出凭据，或把缺失值静默转换为空字符串。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-14
 */
@FunctionalInterface
public interface RuntimeVariableResolver {

    /**
     * 解析一份待执行的 Python 源码。
     *
     * @param sourceCode 持久化的占位符源码
     * @param context 当前执行上下文
     * @return 仅用于本次执行的解析后源码
     */
    String resolve(String sourceCode, PythonRuntimeContext context);
}
