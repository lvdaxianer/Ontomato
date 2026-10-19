package io.ontomato.dataengine.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.config.BusinessConfig;
import io.ontomato.dataengine.config.DataRagConfig;
import io.ontomato.dataengine.dao.JSONRuleDao;
import io.ontomato.dataengine.dataAdapter.DataAdapterRegistry;
import io.ontomato.dataengine.dataAdapter.FakeDataAdapterProvider;
import io.ontomato.dataengine.service.BusinessConfigService;

/** Writing sandbox edges needs the relationship definitions, so the adapter is built with the domain's ontomap. */
class SandboxServiceImplTest {

	@Test
	void buildsTheAdapterWithTheDomainsOntomap() throws Exception {
		BusinessConfig businessConfig = new BusinessConfig();
		businessConfig.setDataAdapter("neo4j");
		BusinessConfigService businessConfigService = mock(BusinessConfigService.class);
		when(businessConfigService.get("d1")).thenReturn(businessConfig);
		JSONObject ontomap = new JSONObject().fluentPut("classDef", new JSONArray())
				.fluentPut("relationship_rule", new JSONObject());
		JSONRuleDao jsonRuleDao = mock(JSONRuleDao.class);
		when(jsonRuleDao.query("d1")).thenReturn(ontomap);
		FakeDataAdapterProvider neo4j = new FakeDataAdapterProvider("neo4j", "Neo4j", List.of("url"));

		SandboxServiceImpl service = new SandboxServiceImpl();
		ReflectionTestUtils.setField(service, "businessConfigService", businessConfigService);
		ReflectionTestUtils.setField(service, "jsonRuleDao", jsonRuleDao);
		ReflectionTestUtils.setField(service, "dataAdapterRegistry",
				new DataAdapterRegistry(List.of(neo4j), new DataRagConfig()));

		service.insertRelations("sb_", JSONArray.of(new JSONObject().fluentPut("relationName", "knows")
				.fluentPut("sourceClassName", "Person").fluentPut("sourceObjId", "1")
				.fluentPut("targetClassName", "Person").fluentPut("targetObjId", "2")), "d1");

		assertSame(ontomap, neo4j.lastJsonRule);
	}
}
