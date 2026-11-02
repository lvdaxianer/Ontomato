package io.ontomato.dataengine.service.impl;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.service.LangService;
import io.ontomato.dataengine.service.PythonFormatService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PythonFormatServiceImpl implements PythonFormatService {

	/** Formatting is layout-only work: far below execution timeouts, but never unbounded. */
	private static final long FORMAT_TIMEOUT_SECONDS = 30L;

	@Autowired
	private DataRagConfig dataRagConfig;

	@Autowired
	private LangService langService;

	@Override
	public String formatPythonCode(String code, String lang) throws Exception {
		String executable = dataRagConfig.getPythonpath();
		if (executable == null || executable.isEmpty()) executable = "python";
		if (executable.contains(File.separator)) executable = Path.of(executable).toAbsolutePath().toString();
		Process process = null;
		Path stdout = null;
		Path stderr = null;
		try {
			stdout = Files.createTempFile("datarag-black-", ".out");
			stderr = Files.createTempFile("datarag-black-", ".err");
			// "-" makes black read the source from stdin and write the formatted code to stdout.
			ProcessBuilder builder = new ProcessBuilder(executable, "-m", "black", "--quiet", "-")
					.redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
			process = builder.start();
			process.getOutputStream().write(code.getBytes(StandardCharsets.UTF_8));
			process.getOutputStream().close();
			if (!process.waitFor(FORMAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new Exception(langService.get(lang, "PythonFormat.failed"));
			}
			String charsetName = dataRagConfig.getPythonCharset();
			Charset charset = Charset.forName(charsetName == null || charsetName.isEmpty() ? "utf-8" : charsetName);
			String error = Files.readString(stderr, charset);
			if (process.exitValue() != 0) {
				if (error.contains("No module named black")) {
					throw new Exception(langService.get(lang, "PythonFormat.unavailable"));
				}
				// Black reports unparseable code (and its own failures) on stderr; surface that message as-is.
				throw new Exception(error.isEmpty() ? langService.get(lang, "PythonFormat.failed") : error.trim());
			}
			return Files.readString(stdout, charset);
		} finally {
			if (process != null && process.isAlive()) process.destroyForcibly();
			deleteQuietly(stdout);
			deleteQuietly(stderr);
		}
	}

	private static void deleteQuietly(Path path) {
		if (path == null) return;
		try {
			Files.deleteIfExists(path);
		} catch (Exception e) {
			log.warn("failed to delete temp file {}: {}", path, e.getMessage());
		}
	}

}
