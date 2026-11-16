package io.ontomato.dataengine.service.impl;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.bean.PythonExecuteResult;
import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.service.BackendSessionEntrance;
import io.ontomato.dataengine.service.LangService;
import io.ontomato.dataengine.service.PythonExecuteService;
import io.ontomato.dataengine.service.SessionCancelledException;
import io.ontomato.dataengine.service.runtime.PythonRuntimeContext;
import io.ontomato.dataengine.service.runtime.RuntimeVariableResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PythonExecuteServiceImpl implements PythonExecuteService {

    File pythonPath = new File("./python");

    @Autowired
    private DataRagConfig dataRagConfig;

    @Autowired
    private LangService langService;

    /** The port programs call /dsl/execute on; only that local origin's answers are recorded. */
    @Value("${server.port}")
    private int serverPort;

    /** 企业版本提供解析器；公共版本保持空值以兼容没有运行时变量的既有代码。 */
    @Autowired(required = false)
    private RuntimeVariableResolver runtimeVariableResolver;

    @Override
    public PythonExecuteResult runPythonCode(String sessionId, String code, int timeoutMinute, String lang, String... args) throws Exception {
        Files.createDirectories(pythonPath.toPath());
        String userId = args != null && args.length > 0 ? args[0] : null;
        String domainId = args != null && args.length > 1 ? args[1] : null;
        String resolvedCode = runtimeVariableResolver == null
                ? code
                : runtimeVariableResolver.resolve(code, new PythonRuntimeContext(sessionId, userId, domainId));
        Path path = Files.createTempFile(pythonPath.toPath(), "metric-view-", ".py");
        File file = path.toFile();
        try {
            Files.writeString(path, resolvedCode);
            // 持久化的指标视图代码只保留占位符，解析值只存在于本次执行的临时文件。
            return executeFile(sessionId, file, null, timeoutMinute, lang, args);
        } finally {
            try { Files.deleteIfExists(path); }
            catch (Exception error) { log.warn("[{}] Failed to clean up the Python program temporary file", sessionId, error); }
        }
    }

    @Override
    public PythonExecuteResult runPythonFile(String sessionId, File file, int timeoutMinute, String lang, String... args) throws Exception {
        return executeFile(sessionId, file, file.getAbsoluteFile().getParentFile(), timeoutMinute, lang, args);
    }

    /**
     * Sliced wait duration for the process: the upper bound of the cancellation-awareness delay, with negligible overhead.
     */
    private static final long WAIT_SLICE_MS = 200L;

    /** Launcher that runs the program and records partial /dsl/execute answers into the run's status file. */
    private static final String QUERY_RUNTIME_RESOURCE = "/python/query_runtime.py";
    private static final String QUERY_STATUS_ENV = "ONTOMATO_QUERY_STATUS_FILE";
    private static final String ENGINE_PORT_ENV = "ONTOMATO_ENGINE_PORT";
    private static final String SESSION_TOKEN_ENV = "ONTOMATO_SESSION_TOKEN";

    private PythonExecuteResult executeFile(String sessionId, File file, File directory, int timeoutMinute, String lang, String... args) throws Exception {
        long start = System.currentTimeMillis();
        Process process = null;
        Path stdout = null;
        Path stderr = null;
        Path runtime = null;
        Path queryStatus = null;
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Python execution has been cancelled");
            log.info("[{}] python program prepared ({} bytes)", sessionId, Files.size(file.toPath()));
            String executable = dataRagConfig.getPythonpath();
            if (executable == null || executable.isEmpty()) executable = "python";
            if (executable.contains(File.separator)) executable = Path.of(executable).toAbsolutePath().toString();
            runtime = Files.createTempFile("datarag-python-runtime-", ".py");
            try (InputStream source = PythonExecuteServiceImpl.class.getResourceAsStream(QUERY_RUNTIME_RESOURCE)) {
                Files.write(runtime, source.readAllBytes());
            }
            // Created per run and read only after this run exits, so no execution inherits another's status.
            queryStatus = Files.createTempFile("datarag-python-", ".query");
            List<String> command = new ArrayList<>(List.of(executable, runtime.toString(), file.getAbsolutePath()));
            if (args != null) command.addAll(Arrays.asList(args));
            log.info("python command: {}", command);
            stdout = Files.createTempFile("datarag-python-", ".out");
            stderr = Files.createTempFile("datarag-python-", ".err");
            ProcessBuilder builder = new ProcessBuilder(command).directory(directory)
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            builder.environment().put(QUERY_STATUS_ENV, queryStatus.toString());
            builder.environment().put(ENGINE_PORT_ENV, String.valueOf(serverPort));
            putSessionToken(builder, sessionId, args);
            process = builder.start();
            // Sliced waiting: on detecting cancellation, throw immediately and go through the existing process-tree kill logic in finally; timeout semantics are the same as before.
            long deadlineNanos = System.nanoTime() + TimeUnit.MINUTES.toNanos(timeoutMinute);
            while (System.nanoTime() < deadlineNanos && !process.waitFor(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)) {
                if (BackendSessionEntrance.isCancelled(sessionId)) {
                    throw new SessionCancelledException(sessionId);
                }
            }
            if (process.isAlive()) {
                throw new Exception("[" + sessionId + "] " + langService.get(lang, "PythonExecutor.execute.timeout") + ": " + timeoutMinute);
            }
            String charsetName = dataRagConfig.getPythonCharset();
            Charset charset = Charset.forName(charsetName == null || charsetName.isEmpty() ? "utf-8" : charsetName);
            String output = Files.readString(stdout, charset);
            String error = Files.readString(stderr, charset);
            log.info("[{}] pythonOutput:{}", sessionId, output);
            if (!error.isEmpty()) log.info("[{}] pythonError:{}", sessionId, error);
            if (process.exitValue() != 0 && error.isEmpty()) error = "Python process exit code was " + process.exitValue();
            PythonExecuteResult result = new PythonExecuteResult();
            result.setResult(output.isEmpty() ? null : output);
            result.setError(error.isEmpty() ? null : error);
            result.setSpendTime(System.currentTimeMillis() - start);
            if (Files.size(queryStatus) > 0) {
                result.setPartialQuery(JSONObject.parseObject(Files.readString(queryStatus, StandardCharsets.UTF_8)));
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            boolean interrupted = Thread.interrupted();
            try {
                if (process != null && process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                for (Path output : new Path[] {stdout, stderr, runtime, queryStatus}) {
                    if (output != null) {
                        try { Files.deleteIfExists(output); }
                        catch (Exception e) { log.warn("[{}] Failed to clean up the Python output temporary file", sessionId, e); }
                    }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 将当前会话 Token 作为一次性子进程环境变量传递，避免把凭据写入源码或日志。
     *
     * @param builder 当前 Python 子进程构造器
     * @param sessionId 当前执行会话标识
     * @param args 运行时上下文参数，前两个参数分别为用户和域
     * @author lvdaxianer@yeah.net
     * @date 2026-11-16
     */
    private void putSessionToken(ProcessBuilder builder, String sessionId, String... args) {
        String token = resolveSessionToken(sessionId, args);
        // 条件注释：只有解析到非空会话 Token 时才向子进程注入敏感环境变量。
        if (!token.isBlank()) {
            builder.environment().put(SESSION_TOKEN_ENV, token);
        }
    }

    /**
     * 从企业运行时解析器读取当前会话 Token；公共版没有解析器时保持兼容。
     *
     * @param sessionId 当前执行会话标识
     * @param args 运行时上下文参数，前两个参数分别为用户和域
     * @return 清理后的会话 Token
     * @author lvdaxianer@yeah.net
     * @date 2026-11-16
     */
    private String resolveSessionToken(String sessionId, String... args) {
        // 条件注释：公共实现没有企业运行时解析器时返回空值，保持原有兼容行为。
        if (runtimeVariableResolver == null) {
            return "";
        }
        String userId = args != null && args.length > 0 ? args[0] : "";
        String domainId = args != null && args.length > 1 ? args[1] : "";
        String token = runtimeVariableResolver.resolveSessionToken(
                new PythonRuntimeContext(sessionId, userId, domainId));
        return token == null ? "" : token.trim();
    }
}
