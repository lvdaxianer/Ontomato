package io.ontomato.dataengine.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONObject;
import io.ontomato.dataengine.config.BusinessConfig;

/**
 * Verifies the persisted embedding vector-source upgrade is idempotent and non-destructive.
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-15
 */
class BusinessConfigDaoEmbeddingMigrationTest {

    /** Verifies a DAO read persists the empty source while preserving the rest of the JSON. */
    @Test
    void queryPersistsTheMigrationWithoutChangingOtherConfiguration() {
        JSONObject stored = JSONObject.of("models", new Object[0], "embeddingVectorSource", null);
        JSONObject migrated = JSONObject.of("models", new Object[0], "embeddingVectorSource", new JSONObject());
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("d1")))
                .thenReturn(stored.toJSONString(), migrated.toJSONString());
        when(jdbcTemplate.update(anyString(), anyString(), eq("d1"), anyString())).thenReturn(1);
        BusinessConfigDao dao = new BusinessConfigDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        dao.query("d1");
        dao.query("d1");

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).update(anyString(), content.capture(), eq("d1"), anyString());
        JSONObject persisted = JSONObject.parseObject(content.getValue());
        assertNotNull(persisted.getJSONObject("embeddingVectorSource"));
        assertTrue(persisted.containsKey("models"));
    }

    /** Verifies a concurrent write causes a retry instead of overwriting the newer configuration. */
    @Test
    void queryRetriesWhenTheCompareAndSetMigrationLosesAConcurrentUpdate() {
        JSONObject stored = JSONObject.of("models", new Object[0], "embeddingVectorSource", null);
        JSONObject current = JSONObject.of("models", new Object[0], "embeddingVectorSource", new JSONObject());
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("d1")))
                .thenReturn(stored.toJSONString(), current.toJSONString());
        when(jdbcTemplate.update(anyString(), anyString(), eq("d1"), anyString())).thenReturn(0);
        BusinessConfigDao dao = new BusinessConfigDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        BusinessConfig result = dao.query("d1");

        assertEquals(0, result.getModels().size());
        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), eq("d1"), anyString());
        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(String.class), eq("d1"));
    }

    /** Verifies repeated concurrent changes stop after the bounded retry instead of looping forever. */
    @Test
    void queryStopsAfterTheSecondCompareAndSetConflict() {
        JSONObject stored = JSONObject.of("models", new Object[0], "embeddingVectorSource", null);
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("d1")))
                .thenReturn(stored.toJSONString(), stored.toJSONString());
        when(jdbcTemplate.update(anyString(), anyString(), eq("d1"), anyString())).thenReturn(0);
        BusinessConfigDao dao = new BusinessConfigDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> dao.query("d1"));

        verify(jdbcTemplate, times(2)).update(anyString(), anyString(), eq("d1"), anyString());
        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(String.class), eq("d1"));
    }

    /** Verifies a missing source is represented as unknown rather than as a matching snapshot. */
    @Test
    void missingVectorSourceIsMigratedToAnEmptyObject() {
        JSONObject stored = new JSONObject();

        assertTrue(BusinessConfigDao.migrateEmbeddingVectorSource(stored));
        assertNotNull(stored.getJSONObject("embeddingVectorSource"));
        assertTrue(stored.getJSONObject("embeddingVectorSource").isEmpty());
    }

    /** Verifies an explicit null source receives the same one-time migration. */
    @Test
    void nullVectorSourceIsMigratedToAnEmptyObject() {
        JSONObject stored = new JSONObject();
        stored.put("embeddingVectorSource", null);

        assertTrue(BusinessConfigDao.migrateEmbeddingVectorSource(stored));
        assertNotNull(stored.getJSONObject("embeddingVectorSource"));
    }

    /** Verifies an existing source snapshot is never overwritten. */
    @Test
    void existingVectorSourceIsPreserved() {
        JSONObject stored = new JSONObject();
        JSONObject source = JSONObject.of("baseUrl", "http://emb/v1", "modelName", "m1", "dimensions", 2048);
        stored.put("embeddingVectorSource", source);

        assertFalse(BusinessConfigDao.migrateEmbeddingVectorSource(stored));
        assertTrue(source.equals(stored.getJSONObject("embeddingVectorSource")));
    }
}
