package io.ontomato.dataengine.config.multisource;

import lombok.Data;

/**
 * One data source a multi-source domain reads from.
 *
 * <p>The {@code id} is the stable name the class/relationship routes point at, and it is also the
 * default key into {@code BusinessConfig.dataAdapterConnections}; {@code connectionRef} exists only
 * for the case where several sources share one saved connection.</p>
 */
@Data
public class DataSourceEntry {

	/** Stable id referenced by class and relationship routes; also the default connection key. */
	private String id;

	/** Display name shown in management UIs; falls back to {@link #id} when blank. */
	private String label;

	/** The installed data adapter type this source uses, e.g. {@code postgresql}. Never {@code multi}. */
	private String adapterType;

	/**
	 * A read-only source is skipped by namespace fan-out; its own adapter still refuses writes with
	 * {@code UnsupportedDataAdapterOperationException}, so this only avoids a guaranteed failure.
	 */
	private boolean readOnly;

	/** Key into {@code BusinessConfig.dataAdapterConnections}; defaults to {@link #id} when blank. */
	private String connectionRef;

	/** The connection key to read: {@link #connectionRef} when set, otherwise {@link #id}. */
	public String effectiveConnectionRef() {
		return connectionRef == null || connectionRef.isBlank() ? id : connectionRef;
	}
}
