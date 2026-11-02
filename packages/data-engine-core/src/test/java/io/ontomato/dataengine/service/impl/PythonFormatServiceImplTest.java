package io.ontomato.dataengine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import io.ontomato.dataengine.config.DataRagConfig;

/**
 * Real-black formatting: layout is rewritten deterministically, unparseable code answers with the
 * tool's own message, and an interpreter without black reports the formatter as unavailable. Only a
 * local python is needed; when black is not installed the black-dependent tests skip themselves.
 */
class PythonFormatServiceImplTest {

	private static final String LANG = "en";
	private static final String UNAVAILABLE = "The code formatter is not available in this deployment.";

	private static String python;
	private static boolean blackAvailable;

	@TempDir
	Path workspace;

	@BeforeAll
	static void detectPython() {
		python = System.getenv().getOrDefault("PYTHON", "python3");
		blackAvailable = pythonExitsZero(python, "-c", "import black");
	}

	@Test
	void messyCodeIsFormattedDeterministically() throws Exception {
		Assumptions.assumeTrue(blackAvailable, "python with black is required");
		String formatted = serviceWith(python).formatPythonCode("def f( a,b ):\n  return a+b\n", LANG);
		assertEquals("def f(a, b):\n    return a + b\n", formatted);
	}

	@Test
	void alreadyFormattedCodeComesBackUnchanged() throws Exception {
		Assumptions.assumeTrue(blackAvailable, "python with black is required");
		String code = "def f(a, b):\n    return a + b\n";
		assertEquals(code, serviceWith(python).formatPythonCode(code, LANG));
	}

	@Test
	void unparseableCodeSurfacesTheToolMessage() {
		Assumptions.assumeTrue(blackAvailable, "python with black is required");
		Exception failure = assertThrows(Exception.class,
				() -> serviceWith(python).formatPythonCode("def broken(:\n", LANG));
		assertTrue(failure.getMessage() != null && !failure.getMessage().isEmpty(),
				"the syntax error message must reach the caller");
	}

	@Test
	void interpreterWithoutBlackReportsUnavailable() throws Exception {
		Path fake = workspace.resolve("fake-python");
		Files.writeString(fake, "#!/bin/sh\necho '/usr/bin/python: No module named black' >&2\nexit 1\n");
		fake.toFile().setExecutable(true);
		Exception failure = assertThrows(Exception.class,
				() -> serviceWith(fake.toString()).formatPythonCode("x = 1\n", LANG));
		assertEquals(UNAVAILABLE, failure.getMessage());
	}

	private static PythonFormatServiceImpl serviceWith(String executable) {
		DataRagConfig config = new DataRagConfig();
		config.setPythonpath(executable);
		PythonFormatServiceImpl service = new PythonFormatServiceImpl();
		ReflectionTestUtils.setField(service, "dataRagConfig", config);
		ReflectionTestUtils.setField(service, "langService",
				(io.ontomato.dataengine.service.LangService) (lang, key) -> switch (key) {
				case "PythonFormat.unavailable" -> UNAVAILABLE;
				case "PythonFormat.failed" -> "Failed to format the code.";
				default -> key;
				});
		return service;
	}

	private static boolean pythonExitsZero(String executable, String... args) {
		try {
			ProcessBuilder builder = new ProcessBuilder(executable);
			for (String arg : args) builder.command().add(arg);
			Process process = builder.start();
			return process.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

}
