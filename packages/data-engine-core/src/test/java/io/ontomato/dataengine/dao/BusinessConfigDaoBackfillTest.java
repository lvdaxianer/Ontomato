package io.ontomato.dataengine.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.dataAdapter.ConnectionSecretCodec;
import io.ontomato.dataengine.dataAdapter.DataAdapterRegistry;
import io.ontomato.dataengine.dataAdapter.FakeDataAdapterProvider;
import io.ontomato.dataengine.dataAdapter.SecretCipher;

class BusinessConfigDaoBackfillTest {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	@Test
	void backfillEncryptsAPlaintextSecretAndRewritesTheRecord() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		Map<String, Object> row = new HashMap<>();
		row.put("id", "d1");
		row.put("content", "{\"models\":[],\"dataAdapterConnections\":{\"postgresql\":"
				+ "{\"url\":\"jdbc:pg\",\"password\":\"pw\"}}}");
		when(jdbc.queryForList(anyString())).thenReturn(List.of(row));

		BusinessConfigDao dao = new BusinessConfigDao();
		ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbc);
		DataAdapterRegistry registry = new DataAdapterRegistry(List.of(
				new FakeDataAdapterProvider("postgresql", "PostgreSQL",
						List.of("url", "password"), Map.of())), new DataRagConfig());
		ReflectionTestUtils.setField(dao, "connectionSecretCodec",
				new ConnectionSecretCodec(registry, new SecretCipher(KEY, SecretCipher.Mode.DUAL)));

		assertEquals(1, dao.backfillConnectionSecrets());
		verify(jdbc).update(anyString(), any(), eq("d1"));
	}
}
