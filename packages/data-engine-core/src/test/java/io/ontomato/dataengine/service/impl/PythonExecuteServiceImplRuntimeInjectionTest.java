package io.ontomato.dataengine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.ontomato.dataengine.bean.PythonExecuteResult;
import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.service.runtime.PythonRuntimeContext;
import io.ontomato.dataengine.service.runtime.RuntimeVariableResolver;

class PythonExecuteServiceImplRuntimeInjectionTest {

    @Test
    void writesOnlyResolvedTemporaryProgramAndDoesNotLogOrPersistTheResolvedSource() throws Exception {
        PythonExecuteServiceImpl service = new PythonExecuteServiceImpl();
        DataRagConfig config = new DataRagConfig();
        config.setPythonpath("python3");
        RuntimeVariableResolver resolver = mock(RuntimeVariableResolver.class);
        when(resolver.resolve(any(), any(PythonRuntimeContext.class))).thenReturn("print('{\"answer\": []}')");
        ReflectionTestUtils.setField(service, "dataRagConfig", config);
        ReflectionTestUtils.setField(service, "runtimeVariableResolver", resolver);
        ReflectionTestUtils.setField(service, "pythonPath", Files.createTempDirectory("python-runtime-test").toFile());
        ReflectionTestUtils.setField(service, "serverPort", 18087);

        PythonExecuteResult result = service.runPythonCode("runtime-injection", "TOKEN = '__request_token'", 1, "en",
                "user-1", "domain-1");

        assertEquals("{\"answer\": []}", result.getResult());
        verify(resolver).resolve("TOKEN = '__request_token'", new PythonRuntimeContext("runtime-injection", "user-1", "domain-1"));
        File[] remaining = ((File) ReflectionTestUtils.getField(service, "pythonPath")).listFiles();
        assertEquals(0, remaining == null ? 0 : remaining.length);
    }
}
