package io.ontomato.dataengine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.bean.PythonExecuteResult;
import io.ontomato.dataengine.bean.function.Function;
import io.ontomato.dataengine.service.PythonExecuteService;

class ABCProgramServiceImplRawResultTest {

	private final ABCProgramServiceImpl service = new ABCProgramServiceImpl();
	private final PythonExecuteService pythonExecuteService = mock(PythonExecuteService.class);

	@BeforeEach
	void setUp(@TempDir Path workspace) {
		ReflectionTestUtils.setField(service, "pythonExecuteService", pythonExecuteService);
		ReflectionTestUtils.setField(service, "baseWorkspace", workspace.toFile());
	}

	@Test
	void usesAnswerValueWithoutUnwrappingAnArray() throws Exception {
		Object result = execute("{\"answer\":[{\"id\":\"x\"}]}");

		JSONArray answer = assertInstanceOf(JSONArray.class, result);
		assertEquals(JSONObject.of("id", "x"), answer.getJSONObject(0));
	}

	@Test
	void passesThroughObjectWhenAnswerIsAbsent() throws Exception {
		Object result = execute("{\"id\":\"x\",\"name\":\"example\"}");

		assertEquals(JSONObject.of("id", "x", "name", "example"), result);
	}

	@Test
	void passesThroughArrayWhenAnswerIsAbsent() throws Exception {
		Object result = execute("[{\"id\":\"x\"}]");

		JSONArray rows = assertInstanceOf(JSONArray.class, result);
		assertEquals(1, rows.size());
	}

	@Test
	void preservesEmptyAnswerArray() throws Exception {
		Object result = execute("{\"answer\":[]}");

		assertEquals(new JSONArray(), result);
	}

	private Object execute(String output) throws Exception {
		PythonExecuteResult execution = new PythonExecuteResult();
		execution.setResult(output);
		when(pythonExecuteService.runPythonCode(anyString(), anyString(), anyInt(), anyString(), anyString(),
				anyString(), anyString())).thenReturn(execution);

		Function function = new Function();
		function.setCode("print('result')");
		function.setParameters(List.of());
		Object[] result = service.executeRawFunction("session", function, "user", "zh-CN", 1, "domain");
		return result[0];
	}
}
