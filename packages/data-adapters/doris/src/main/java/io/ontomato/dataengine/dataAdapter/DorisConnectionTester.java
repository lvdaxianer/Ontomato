package io.ontomato.dataengine.dataAdapter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.alibaba.fastjson2.JSONObject;

/**
 * Runs the Doris connection probe with a bounded lifecycle.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
final class DorisConnectionTester {

	private static final long TIMEOUT_MILLIS = 15000L;
	private static final int VALID_TIMEOUT_SECONDS = 5;

	private final DorisAdapter adapter;

	/**
	 * Creates a connection tester.
	 *
	 * @param adapter Doris adapter
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	DorisConnectionTester(DorisAdapter adapter) {
		this.adapter = adapter;
	}

	/**
	 * Executes a bounded metadata probe.
	 *
	 * @return metadata report
	 * @throws Exception when the probe fails or exceeds its timeout
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	JSONObject test() throws Exception {
		ExecutorService executor = newExecutor();
		try {
			return await(executor.submit(this::probe));
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Creates a daemon executor for the connection probe.
	 *
	 * @return single-thread executor
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private ExecutorService newExecutor() {
		return Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "doris-connection-test");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Waits for the probe and normalizes lifecycle failures.
	 *
	 * @param future submitted probe
	 * @return metadata report
	 * @throws Exception when the probe fails or exceeds its timeout
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private JSONObject await(Future<JSONObject> future) throws Exception {
		try {
			return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException timeout) {
			future.cancel(true);
			throw new SQLException("Connection test timed out after " + TIMEOUT_MILLIS + " ms");
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new SQLException("Connection test was interrupted");
		} catch (ExecutionException failed) {
			return rethrow(failed.getCause());
		}
	}

	/**
	 * Rethrows the original probe exception when possible.
	 *
	 * @param cause probe failure
	 * @return never returns normally
	 * @throws Exception original exception or normalized SQL exception
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private JSONObject rethrow(Throwable cause) throws Exception {
		if (cause instanceof Exception exception) {
			throw exception;
		} else {
			throw new SQLException("Connection test failed: " + cause);
		}
	}

	/**
	 * Opens the connection and reads visible metadata.
	 *
	 * @return metadata report
	 * @throws Exception when JDBC rejects the connection or metadata query
	 * @author lvdaxianerplus
	 * @date 2026-10-12
	 */
	private JSONObject probe() throws Exception {
		long started = System.nanoTime();
		try (Connection connection = adapter.openConnection()) {
			if (!connection.isValid(VALID_TIMEOUT_SECONDS)) {
				throw new SQLException("The database connection is not valid");
			}
			return DorisMetadataReader.read(connection, started);
		}
	}
}
