package io.ontomato.dataengine.dataAdapter;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.ontomato.dataengine.config.DataRagConfig;

/**
 * Registers the public read-only Doris FE adapter.
 *
 * @author lvdaxianerplus
 * @date 2026-10-12
 */
@Component
public class DorisAdapterProvider implements DataAdapterProvider {

	@Override
	public String type() {
		return "doris";
	}

	@Override
	public String label() {
		return "Doris";
	}

	@Override
	public boolean sql() {
		return true;
	}

	@Override
	public List<String> connectionFields() {
		return List.of("url", "user", "password");
	}

	@Override
	public Map<String, String> connectionExamples() {
		return Map.of("url", "jdbc:mysql://host:9030/db?useSSL=false", "user", "reader");
	}

	@Override
	public DataAdapter create(DataAdapterConnection connection, DataRagConfig dataRagConfig,
			Map<String, Object> jsonRule) {
		return new DorisAdapter(connection, jsonRule);
	}
}
