package io.ontomato.dataengine.dataAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.ontomato.dataengine.config.DataRagConfig;

/** The app pom's adapter modules are what the registry reports. */
class DataAdapterAssemblyTest {

	@Test
	void theOpenSourceEditionInstallsConfiguredSqlAdapters() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ctx.register(DataRagConfig.class);
			ctx.scan("io.ontomato.dataengine.dataAdapter");
			ctx.refresh();
			List<DataAdapterProvider> installed = ctx.getBean(DataAdapterRegistry.class).installed();
			assertEquals(List.of("doris", "duckdb", "mysql", "postgresql"),
					installed.stream().map(DataAdapterProvider::type).toList());
			assertEquals(Map.of("url", "jdbc:mysql://host:9030/db?useSSL=false", "user", "reader"),
					installed.stream().filter(provider -> "doris".equals(provider.type())).findFirst().orElseThrow()
							.connectionExamples());
			assertEquals(Map.of("url", "jdbc:postgresql://host:port/db", "user", "postgres"),
					installed.stream().filter(provider -> "postgresql".equals(provider.type())).findFirst().orElseThrow()
							.connectionExamples());
		}
	}
}
