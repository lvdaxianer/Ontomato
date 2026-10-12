package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.config.DataRagConfig;

class DorisAdapterReadOnlyTest {

	private final DorisAdapter adapter = new DorisAdapter(new DataAdapterConnection(), Map.of());

	@Test
	void rejectsEveryDataMutationFamilyWithAnExplicitOperation() {
		assertAll(
			() -> assertUnsupported("namespace creation", () -> adapter.createNamespace("analytics")),
			() -> assertUnsupported("namespace truncation", () -> adapter.truncateNamespace("analytics")),
			() -> assertUnsupported("namespace deletion", () -> adapter.dropNamespace("analytics")),
			() -> assertUnsupported("class creation", () -> adapter.createClass("analytics", Map.of())),
			() -> assertUnsupported("edge-type creation", () -> adapter.createEdgeType("analytics", "owns")),
			() -> assertUnsupported("object insertion", () -> adapter.insertObjects("analytics", Map.of(), new JSONArray())),
			() -> assertUnsupported("object update", () -> adapter.updateObjects("analytics", Map.of(), new JSONObject(), new JSONObject())),
			() -> assertUnsupported("object deletion", () -> adapter.deleteObjects("analytics", Map.of(), new JSONObject())),
			() -> assertUnsupported("edge creation", () -> adapter.createEdge("analytics", "owns", "a", "1", "b", "2")),
			() -> assertUnsupported("edge deletion", () -> adapter.deleteEdge("analytics", "owns", "a", "1", "b", "2")));
	}

	@Test
	void rejectsPathsThatRequireOntomatoStoredData() {
		assertAll(
			() -> assertUnsupported("sample data", () -> adapter.getSampleDataFileName("samples", "Order")),
			() -> assertUnsupported("sample data", () -> adapter.querySampleDataByClassName("Order", List.of("id"))),
			() -> assertUnsupported("sandbox query", () -> adapter.query(new JSONObject(), "sandbox", "domain", null, new DataRagConfig())),
			() -> assertUnsupported("vector attribute query", () -> adapter.queryVectorAttr(Map.of(), "embedding", "1")),
			() -> assertUnsupported("sandbox class resolution", () -> adapter.sandboxClassName("sandbox", "Order")),
			() -> assertUnsupported("sandbox object resolution", () -> adapter.sandboxObjectId("sandbox", "1")));
	}

	private void assertUnsupported(String operation, ThrowingOperation invocation) throws Exception {
		UnsupportedDataAdapterOperationException failure = assertThrows(
				UnsupportedDataAdapterOperationException.class, invocation::run);
		assertEquals("doris data source does not support " + operation, failure.getMessage());
	}

	@FunctionalInterface
	private interface ThrowingOperation {
		void run() throws Exception;
	}
}
