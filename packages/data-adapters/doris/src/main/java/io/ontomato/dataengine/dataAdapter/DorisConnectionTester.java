package io.ontomato.dataengine.dataAdapter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.alibaba.fastjson2.JSONObject;

/** Runs the Doris connection probe with the same bounded lifecycle as the shared tester. */
final class DorisConnectionTester {

	private static final long TIMEOUT_MILLIS = 15000L;
	private static final int VALID_TIMEOUT_SECONDS = 5;

	private final DorisAdapter adapter;

	DorisConnectionTester(DorisAdapter adapter) {
		this.adapter = adapter;
	}

	JSONObject test() throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "doris-connection-test");
			thread.setDaemon(true);
			return thread;
		});
		try {
			Future<JSONObject> future = executor.submit(this::probe);
			try {
				return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			} catch (TimeoutException timeout) {
				future.cancel(true);
				throw new SQLException("Connection test timed out after " + TIMEOUT_MILLIS + " ms");
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new SQLException("Connection test was interrupted");
			} catch (java.util.concurrent.ExecutionException failed) {
				Throwable cause = failed.getCause();
				if (cause instanceof Exception exception) {
					throw exception;
				}
				throw new SQLException("Connection test failed: " + cause);
			}
		} finally {
			executor.shutdownNow();
		}
	}

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
