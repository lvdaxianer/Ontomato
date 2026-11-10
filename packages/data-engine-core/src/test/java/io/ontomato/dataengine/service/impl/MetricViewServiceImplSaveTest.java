package io.ontomato.dataengine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.ontomato.dataengine.bean.function.Function;
import io.ontomato.dataengine.bean.metricView.MetricView;
import io.ontomato.dataengine.dao.FunctionDao;
import io.ontomato.dataengine.dao.MetricViewDao;
import io.ontomato.dataengine.service.LangService;

class MetricViewServiceImplSaveTest {

	private final MetricViewServiceImpl service = new MetricViewServiceImpl();
	private final FunctionDao functionDao = mock(FunctionDao.class);
	private final MetricViewDao metricViewDao = mock(MetricViewDao.class);
	private final LangService langService = mock(LangService.class);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(service, "functionDao", functionDao);
		ReflectionTestUtils.setField(service, "metricViewDao", metricViewDao);
		ReflectionTestUtils.setField(service, "langService", langService);
		when(functionDao.save(any(Function.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(metricViewDao.save(any(MetricView.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(langService.get("en", "MetricView.domainRequired")).thenReturn("MetricView domain is required");
	}

	@Test
	void savePropagatesMetricViewDomainToNestedFunction() throws Exception {
		Function function = readFunction();
		MetricView metricView = new MetricView();
		metricView.setDomainId("domain-1");
		metricView.setFunction(function);

		service.save(metricView, "en");

		ArgumentCaptor<Function> savedFunction = ArgumentCaptor.forClass(Function.class);
		verify(functionDao).save(savedFunction.capture());
		assertEquals("domain-1", savedFunction.getValue().getDomainId());
	}

	@Test
	void saveRejectsMissingMetricViewDomainBeforeWriting() {
		MetricView metricView = new MetricView();
		metricView.setFunction(readFunction());

		assertThrows(Exception.class, () -> service.save(metricView, "en"));

		verifyNoInteractions(functionDao, metricViewDao);
	}

	@Test
	void saveRejectsFunctionFromAnotherDomainBeforeWriting() {
		Function existingFunction = readFunction();
		existingFunction.setId("function-1");
		existingFunction.setDomainId("domain-2");
		when(functionDao.queryById("function-1")).thenReturn(existingFunction);

		Function function = readFunction();
		function.setId("function-1");
		MetricView metricView = new MetricView();
		metricView.setDomainId("domain-1");
		metricView.setFunction(function);

		assertThrows(Exception.class, () -> service.save(metricView, "en"));

		verify(functionDao, never()).save(any(Function.class));
		verifyNoInteractions(metricViewDao);
	}

	private Function readFunction() {
		Function function = new Function();
		function.setOperation(Function.OPERATION_READ);
		return function;
	}
}
