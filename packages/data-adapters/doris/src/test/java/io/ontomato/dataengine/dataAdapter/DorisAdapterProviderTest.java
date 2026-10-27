package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.ontomato.dataengine.config.DataRagConfig;

class DorisAdapterProviderTest {

	private final DorisAdapterProvider provider = new DorisAdapterProvider();

	@Test
	void advertisesDorisFeConnectionContract() {
		assertEquals("doris", provider.type());
		assertEquals("Doris", provider.label());
		assertTrue(provider.requiresJoinFields());
		assertEquals(List.of("url", "user", "password"), provider.connectionFields());
		assertEquals("jdbc:mysql://host:9030/db?useSSL=false", provider.connectionExamples().get("url"));
	}

	@Test
	void createsDorisAdapterWithConnection() {
		DataAdapterConnection connection = new DataAdapterConnection();
		connection.setUrl("jdbc:mysql://doris-fe:9030/analytics");
		connection.setUser("reader");
		connection.setPassword("secret");

		DataAdapter adapter = provider.create(connection, new DataRagConfig(), Map.of());

		assertInstanceOf(DorisAdapter.class, adapter);
		assertEquals(connection, ((DorisAdapter) adapter).connection());
	}
}
