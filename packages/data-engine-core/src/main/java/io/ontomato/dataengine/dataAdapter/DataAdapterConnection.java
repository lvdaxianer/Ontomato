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
 * <p>The four legacy accessors below are compile-compatibility shims for
 * modules outside this repository; they are not used by the shared
 * controller/UI path, which reads and writes names generically.</p>
 */
public class DataAdapterConnection extends LinkedHashMap<String, String> {

	public String getString(String name) {
		return get(name);
	}

	public void putString(String name, String value) {
		put(name, value);
	}

	@Deprecated
	public String getUrl() {
		return get("url");
	}

	@Deprecated
	public void setUrl(String url) {
		put("url", url);
	}

	@Deprecated
	public String getCompany() {
		return get("company");
	}

	@Deprecated
	public void setCompany(String company) {
		put("company", company);
	}

	@Deprecated
	public String getUser() {
		return get("user");
	}

	@Deprecated
	public void setUser(String user) {
		put("user", user);
	}

	@Deprecated
	public String getPassword() {
		return get("password");
	}

	@Deprecated
	public void setPassword(String password) {
		put("password", password);
	}
}
