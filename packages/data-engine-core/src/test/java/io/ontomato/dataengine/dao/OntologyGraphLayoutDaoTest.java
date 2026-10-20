package io.ontomato.dataengine.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/** Verifies PostgreSQL layout persistence and server-side coordinate validation. */
class OntologyGraphLayoutDaoTest {

    /** Verifies startup creates the layout table idempotently. */
    @Test
    void initializesTheDomainScopedLayoutTable() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        OntologyGraphLayoutDao dao = new OntologyGraphLayoutDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        dao.initIndex();

        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS ontology_graph_layout"));
    }

    /** Verifies missing domains return an empty position map. */
    @Test
    void returnsAnEmptyLayoutWhenTheDomainHasNoSavedRow() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.<RowMapper<JSONObject>>any(), eq("domain-a")))
                .thenThrow(new EmptyResultDataAccessException(1));
        OntologyGraphLayoutDao dao = new OntologyGraphLayoutDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        JSONObject layout = dao.query("domain-a");

        assertEquals(new JSONObject(), layout.getJSONObject("positions"));
    }

    /** Verifies a complete position snapshot is written in one upsert. */
    @Test
    void savesAndReadsTheCompletePositionSnapshot() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        JSONObject positions = JSONObject.of("Order", JSONObject.of("x", 120, "y", 240));
        JSONObject layout = JSONObject.of("positions", positions, "viewport", JSONObject.of("zoom", 1.0));
        OntologyGraphLayoutDao dao = new OntologyGraphLayoutDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        dao.save("domain-a", layout);

        verify(jdbcTemplate).update(anyString(), eq("domain-a"), eq(positions.toJSONString()),
                eq(JSONObject.of("zoom", 1.0).toJSONString()));
    }

    /** Verifies request-body map values are accepted by the persistence boundary. */
    @Test
    void savesLayoutWhenNestedValuesAreMaps() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        JSONObject layout = new JSONObject(Map.of(
                "positions", Map.of("Order", Map.of("x", 120, "y", 240))));
        OntologyGraphLayoutDao dao = new OntologyGraphLayoutDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        dao.save("domain-a", layout);

        ArgumentCaptor<String> positionsCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), eq("domain-a"), positionsCaptor.capture(), isNull());

        JSONObject savedPositions = JSONObject.parseObject(positionsCaptor.getValue());
        assertEquals(120, savedPositions.getJSONObject("Order").getIntValue("x"));
        assertEquals(240, savedPositions.getJSONObject("Order").getIntValue("y"));
    }

    /** Verifies non-finite coordinates are rejected before database access. */
    @Test
    void rejectsNonFiniteCoordinatesBeforeWriting() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        JSONObject layout = JSONObject.of("positions", JSONObject.of("Order", JSONObject.of("x", Double.NaN, "y", 2)));
        OntologyGraphLayoutDao dao = new OntologyGraphLayoutDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () -> dao.save("domain-a", layout));
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }
}
