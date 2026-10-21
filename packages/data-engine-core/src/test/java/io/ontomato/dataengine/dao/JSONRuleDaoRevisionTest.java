package io.ontomato.dataengine.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** Verifies optimistic revision metadata on the active ontology snapshot.
 *
 * @author lvdaxianer@yeah.net
 */
class JSONRuleDaoRevisionTest {

    /** Verifies existing json_rule tables receive revision metadata idempotently. */
    @Test
    void initializesRevisionMetadataForTheActiveRule() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        JSONRuleDao dao = new JSONRuleDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        dao.initIndex();

        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("ADD COLUMN IF NOT EXISTS revision"));
        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("ADD COLUMN IF NOT EXISTS content_hash"));
    }

    /** Verifies active promotion is conditional on the revision and content hash read by the caller. */
    @Test
    void promotesOnlyTheExpectedActiveRevision() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), eq("new-content"), eq(2L), eq("new-hash"), eq("domain-a"),
                eq(1L), eq("old-hash"))).thenReturn(1);
        JSONRuleDao dao = new JSONRuleDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        boolean promoted = dao.promote("domain-a", 1L, "old-hash", 2L, "new-content", "new-hash");

        assertTrue(promoted);
        verify(jdbcTemplate).update(anyString(), eq("new-content"), eq(2L), eq("new-hash"), eq("domain-a"),
                eq(1L), eq("old-hash"));
    }
}
