package io.ontomato.dataengine.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RuntimeVariableInjectorTest {

    @Test
    void replacesRegisteredPlaceholdersWithoutChangingOtherPythonText() {
        String code = "URL = '__request_path/dsl/execute'\nTOKEN = '__request_token'\nNAME = '__main__'";

        String result = RuntimeVariableInjector.inject(code, Map.of(
                "__request_path", "https://engine.internal",
                "__request_token", "trusted-token"));

        assertEquals("URL = 'https://engine.internal/dsl/execute'\nTOKEN = 'trusted-token'\nNAME = '__main__'", result);
    }

    @Test
    void rejectsNullRuntimeValueInsteadOfWritingAnEmptyCredential() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RuntimeVariableInjector.inject("TOKEN = '__request_token'", Map.of("__request_token", "")));

        assertEquals("运行时变量 __request_token 的值不能为空", error.getMessage());
    }

    @Test
    void doesNotAllowAResolvedValueToBeUsedAsAReplacementPattern() {
        String result = RuntimeVariableInjector.inject(
                "A = '__request_path'\nB = '__request_path'",
                Map.of("__request_path", "$1\\backslash"));

        assertEquals("A = '$1\\backslash'\nB = '$1\\backslash'", result);
    }

    @Test
    void rejectsAnUnresolvedPlaceholderAfterReplacement() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RuntimeVariableInjector.inject("TOKEN = '__request_token'", Map.of()));

        assertEquals("替换后仍存在未解析的运行时变量：__request_token", error.getMessage());
    }
}
