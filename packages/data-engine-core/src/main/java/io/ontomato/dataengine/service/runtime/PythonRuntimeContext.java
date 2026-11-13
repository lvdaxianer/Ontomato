package io.ontomato.dataengine.service.runtime;

/**
 * 传递给运行时变量解析器的可信执行上下文，不向 Python 代码暴露凭据。
 */
public record PythonRuntimeContext(String sessionId, String userId, String domainId) {}
