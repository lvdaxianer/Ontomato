package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

class DataAdapterConnectionJsonTest {

	@Test
	void serializesAsAFlatObjectAndReadsTheLegacyShape() {
		DataAdapterConnection connection = new DataAdapterConnection();
		connection.put("url", "jdbc:snowflake://acct");
		connection.put("account", "acct");
		connection.put("password", "secret");

		JSONObject wire = JSON.parseObject(JSON.toJSONString(connection));
		assertEquals("jdbc:snowflake://acct", wire.getString("url"));
		assertEquals("acct", wire.getString("account"));
		assertEquals("secret", wire.getString("password"));
		assertFalse(wire.containsKey("empty"), "a Map subclass must not add bean properties");

		DataAdapterConnection legacy = JSON.parseObject(
				"{\"url\":\"jdbc:pg\",\"user\":\"u\",\"password\":\"p\"}", DataAdapterConnection.class);
		assertEquals("jdbc:pg", legacy.get("url"));
		assertEquals("u", legacy.get("user"));
		assertEquals("p", legacy.get("password"));
	}
}
