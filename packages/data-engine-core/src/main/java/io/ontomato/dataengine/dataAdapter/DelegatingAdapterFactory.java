package io.ontomato.dataengine.dataAdapter;

import java.util.Map;

import io.ontomato.dataengine.config.BusinessConfig;
import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.config.multisource.DataSourceEntry;

/**
 * Builds the delegate adapter of one configured source on demand.
 *
 * <p>Delegates are deliberately not cached across calls. An adapter is a thin holder of
 * {@code (connection, jsonRule)}, and different call paths hand the same source different jsonRules
 * (the sandbox write path passes none at all) — so a cache keyed on the source id would eventually
 * serve a stale ontomap. Building is cheap, so it happens per call.</p>
 */
public class DelegatingAdapterFactory {

	private final DataAdapterRegistry registry;
	private final DataRagConfig dataRagConfig;
	private final BusinessConfig businessConfig;

	public DelegatingAdapterFactory(DataAdapterRegistry registry, DataRagConfig dataRagConfig,
			BusinessConfig businessConfig) {
		this.registry = registry;
		this.dataRagConfig = dataRagConfig;
		this.businessConfig = businessConfig;
	}

	/** The delegate of one source, wired to the given jsonRule (which may be null, as on the write path). */
	public DataAdapter create(DataSourceEntry source, Map<String, Object> jsonRule) {
		if (source == null) {
			throw new IllegalArgumentException("Unknown data source");
		}
		DataAdapterProvider provider = registry.provider(source.getAdapterType());
		if ("multi".equals(provider.type())) {
			throw new IllegalArgumentException("A data source may not nest another multi source: " + source.getId());
		}
		DataAdapterConnection connection = businessConfig.getDataAdapterConnections()
				.getOrDefault(source.effectiveConnectionRef(), new DataAdapterConnection());
		return provider.create(connection, dataRagConfig, jsonRule);
	}
}
