package io.ontomato.dataengine.service.vector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.ontomato.dataengine.config.BusinessConfig;
import io.ontomato.dataengine.dao.VectorRebuildState;
import io.ontomato.dataengine.dao.VectorRebuildStateDao;
import io.ontomato.dataengine.service.BusinessConfigService;
import io.ontomato.dataengine.service.LangService;

/**
 * The rebuild rule (confirmed item 4): baseUrl, modelName or dimensions differing from the recorded
 * vector source needs a rebuild; the key and the other execution fields never count.
 */
class VectorRetrievalGateTest {

    private final BusinessConfigService businessConfigService = mock(BusinessConfigService.class);
    private final VectorRebuildStateDao stateDao = mock(VectorRebuildStateDao.class);
    private final VectorRebuildTracker rebuildTracker = new VectorRebuildTracker();
    private final LangService langService = mock(LangService.class);
    private final VectorRetrievalGate gate = new VectorRetrievalGate();

    VectorRetrievalGateTest() {
        ReflectionTestUtils.setField(gate, "businessConfigService", businessConfigService);
        ReflectionTestUtils.setField(gate, "stateDao", stateDao);
        ReflectionTestUtils.setField(gate, "rebuildTracker", rebuildTracker);
        ReflectionTestUtils.setField(gate, "langService", langService);
    }

    @Test
    void changedDimensionsNeedARebuild() {
        assertTrue(VectorRetrievalGate.isRebuildNeeded(config("http://emb/v1", "m1", 2048, 1024)));
    }

    @Test
    void changedModelNameNeedsARebuild() {
        assertTrue(VectorRetrievalGate.isRebuildNeeded(config("http://emb/v1", "m2", 2048, 2048)));
    }

    @Test
    void changedBaseUrlNeedsARebuild() {
        assertTrue(VectorRetrievalGate.isRebuildNeeded(config("http://other/v1", "m1", 2048, 2048)));
    }

    @Test
    void onlyTheKeyChangedNeedsNoRebuild() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 2048);
        businessConfig.getEmbeddingModelProperties().setApiKey("new-key");
        assertFalse(VectorRetrievalGate.isRebuildNeeded(businessConfig));
    }

    @Test
    void identicalConfigurationNeedsNoRebuild() {
        assertFalse(VectorRetrievalGate.isRebuildNeeded(config("http://emb/v1", "m1", 2048, 2048)));
    }

    /** A migrated empty source must force a rebuild once the embedding model is usable. */
    @Test
    void emptyVectorSourceNeedsARebuildWhenEmbeddingIsConfigured() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 2048);
        businessConfig.setEmbeddingVectorSource(new BusinessConfig.EmbeddingVectorSource());

        assertTrue(VectorRetrievalGate.isRebuildNeeded(businessConfig));
    }

    @Test
    void missingVectorSourceFailsExplicitly() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 2048);
        businessConfig.setEmbeddingVectorSource(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> VectorRetrievalGate.isRebuildNeeded(businessConfig));
        assertTrue(error.getMessage().contains("embeddingVectorSource"), error.getMessage());
    }

    /** An absent current embedding configuration must not block settings-page initialization. */
    @Test
    void missingEmbeddingConfigurationDoesNotNeedARebuild() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 2048);
        businessConfig.setEmbeddingModelProperties(null);

        assertFalse(VectorRetrievalGate.isRebuildNeeded(businessConfig));
    }

    /** A partial embedding configuration is not actionable and therefore needs no rebuild. */
    @Test
    void incompleteEmbeddingConfigurationDoesNotNeedARebuild() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 2048);
        businessConfig.getEmbeddingModelProperties().setDimensions(null);

        assertFalse(VectorRetrievalGate.isRebuildNeeded(businessConfig));
    }

    @Test
    void retrievalFailsWhenARebuildIsNeededAndNoneRuns() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 1024);
        when(businessConfigService.get("d1")).thenReturn(businessConfig);
        when(stateDao.find("d1")).thenReturn(VectorRebuildState.of(
                businessConfig.getEmbeddingModelProperties(), VectorRebuildState.Status.PENDING, false));
        when(langService.get(eq(null), eq("Vector.rebuildRequired"))).thenReturn("vectors need rebuilding");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> gate.requireRetrievable("d1"));
        assertTrue(error.getMessage().contains("need rebuilding"), error.getMessage());
    }

    @Test
    void retrievalRemainsBlockedWhileARebuildRuns() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 1024);
        when(businessConfigService.get("d1")).thenReturn(businessConfig);
        when(stateDao.find("d1")).thenReturn(VectorRebuildState.of(
                businessConfig.getEmbeddingModelProperties(), VectorRebuildState.Status.PENDING, false));
        when(langService.get(eq(null), eq("Vector.rebuildRequired"))).thenReturn("vectors need rebuilding");
        rebuildTracker.tryStart("d1");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> gate.requireRetrievable("d1"));
        assertTrue(error.getMessage().contains("need rebuilding"), error.getMessage());
    }

    @Test
    void retrievalPassesWhenNoRebuildIsNeeded() {
        BusinessConfig businessConfig = config("http://emb/v1", "m1", 2048, 2048);
        when(businessConfigService.get("d1")).thenReturn(businessConfig);
        when(stateDao.find("d1")).thenReturn(VectorRebuildState.of(
                businessConfig.getEmbeddingModelProperties(), VectorRebuildState.Status.READY, true));

        gate.requireRetrievable("d1");

        verifyNoInteractions(langService);
    }

    private static BusinessConfig config(String currentBaseUrl, String currentModelName,
            int sourceDimensions, int currentDimensions) {
        BusinessConfig businessConfig = new BusinessConfig();
        BusinessConfig.EmbeddingModelProperties props = businessConfig.new EmbeddingModelProperties();
        props.setBaseUrl(currentBaseUrl);
        props.setApiKey("key");
        props.setModelName(currentModelName);
        props.setDimensions(currentDimensions);
        businessConfig.setEmbeddingModelProperties(props);
        BusinessConfig.EmbeddingVectorSource source = new BusinessConfig.EmbeddingVectorSource();
        source.setBaseUrl("http://emb/v1");
        source.setModelName("m1");
        source.setDimensions(sourceDimensions);
        businessConfig.setEmbeddingVectorSource(source);
        return businessConfig;
    }

}
