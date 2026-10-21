package io.ontomato.dataengine.dao;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** Verifies the append-only PostgreSQL boundary for ontology revisions.
 *
 * @author lvdaxianer@yeah.net
 */
class OntologyRevisionDaoTest {

    /** Verifies the revision table and lookup index are initialized idempotently. */
    @Test
    void initializesTheRevisionStore() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        OntologyRevisionDao dao = new OntologyRevisionDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        dao.initIndex();

        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS ontology_revisions"));
        verify(jdbcTemplate, times(2)).execute(anyString());
    }

    /** Verifies revision rows are inserted without an update path. */
    @Test
    void appendsARevisionSnapshot() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        OntologyRevisionDao dao = new OntologyRevisionDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);
        OntologyRevisionRecord revision = new OntologyRevisionRecord(
                "domain-a", 3L, "{\"classDef\":[]}", "hash", "manual", "user-a");

        dao.save(revision);

        verify(jdbcTemplate).update(anyString(), eq("domain-a"), eq(3L), eq(revision.content()),
                eq(revision.contentHash()), eq(revision.source()), eq(revision.actorId()));
    }
}
