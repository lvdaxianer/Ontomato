package io.ontomato.dataengine.dataAdapter;

import java.util.LinkedHashMap;

/**
 * Saved connection of one data adapter type: a flat map of the field names the
 * adapter's provider declares.
 *
 * <p>A map instead of a fixed bean is what lets a new adapter add
 * {@code account}, {@code warehouse}, {@code role}, {@code serverHostname},
 * {@code httpPath} or any other connection field without changing this class,
 * the controller or the settings form. The JSON stays flat
 * ({@code {"url":...,"user":...,"account":...}}), so existing saved configs
 * remain readable without a migration.</p>
 *
 * <p>Every shared path — controller, settings form and adapter — reads and
 * writes values by name; there is no per-adapter field list here.</p>
 */
public class DataAdapterConnection extends LinkedHashMap<String, String> {

	public String getString(String name) {
		return get(name);
	}

	public void putString(String name, String value) {
		put(name, value);
	}

}
