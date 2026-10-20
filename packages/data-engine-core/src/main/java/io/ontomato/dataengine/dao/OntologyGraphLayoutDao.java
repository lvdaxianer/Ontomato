package io.ontomato.dataengine.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/** Stores one shared visual-modeling layout for each ontology domain. */
@Slf4j
@Component("ontologyGraphLayoutDao")
public class OntologyGraphLayoutDao {

    private static final String TABLE_NAME = "ontology_graph_layout";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Creates the layout table without changing existing application data. */
    @PostConstruct
    public void initIndex() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "domain_id VARCHAR(64) PRIMARY KEY, "
                + "positions JSONB NOT NULL DEFAULT '{}'::jsonb, "
                + "viewport JSONB, "
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
        log.info("Ontology graph layout table initialization complete");
    }

    /** Reads a domain layout and returns an empty snapshot when no row exists. */
    public JSONObject query(String domainId) {
        requireDomainId(domainId);
        try {
            JSONObject layout = jdbcTemplate.queryForObject(
                    "SELECT positions::text, viewport::text FROM " + TABLE_NAME + " WHERE domain_id = ?",
                    (rs, rowNum) -> mapLayout(rs), domainId);
            return layout != null ? layout : emptyLayout();
        } catch (EmptyResultDataAccessException e) {
            return emptyLayout();
        } catch (RuntimeException e) {
            log.error("Failed to read ontology graph layout from PostgreSQL", e);
            throw e;
        }
    }

    /** Replaces a domain layout atomically and validates every client-provided coordinate. */
    public void save(String domainId, JSONObject layout) {
        requireDomainId(domainId);
        JSONObject normalized = normalizeAndValidate(layout);
        JSONObject positions = normalized.getJSONObject("positions");
        JSONObject viewport = normalized.getJSONObject("viewport");

        jdbcTemplate.update(
                "INSERT INTO " + TABLE_NAME + " (domain_id, positions, viewport, updated_at) VALUES (?, ?::jsonb, ?::jsonb, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (domain_id) DO UPDATE SET positions = EXCLUDED.positions, viewport = EXCLUDED.viewport, updated_at = CURRENT_TIMESTAMP",
                domainId,
                positions.toJSONString(),
                viewport == null ? null : viewport.toJSONString());
    }

    /** Converts one PostgreSQL row into the public layout shape. */
    private static JSONObject mapLayout(ResultSet resultSet) throws SQLException {
        JSONObject layout = emptyLayout();
        String positions = resultSet.getString(1);
        if (positions != null) {
            layout.put("positions", parseObject(positions, "positions"));
        }
        String viewport = resultSet.getString(2);
        if (viewport != null) {
            layout.put("viewport", parseObject(viewport, "viewport"));
        }
        return normalizeAndValidate(layout);
    }

    /** Normalizes optional fields and rejects malformed coordinates before persistence. */
    private static JSONObject normalizeAndValidate(JSONObject layout) {
        if (layout == null) {
            throw new IllegalArgumentException("layout is required");
        }

        Object rawPositions = layout.get("positions");
        JSONObject positions = rawPositions == null ? new JSONObject() : asObject(rawPositions, "positions");
        for (Map.Entry<String, Object> entry : positions.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("position class name is required");
            }
            JSONObject position = asObject(entry.getValue(), "position for " + entry.getKey());
            requireFiniteNumber(position, "x", entry.getKey());
            requireFiniteNumber(position, "y", entry.getKey());
        }

        JSONObject normalized = new JSONObject();
        normalized.put("positions", positions);
        Object rawViewport = layout.get("viewport");
        if (rawViewport != null) {
            JSONObject viewport = asObject(rawViewport, "viewport");
            requireFiniteNumberIfPresent(viewport, "x", "viewport");
            requireFiniteNumberIfPresent(viewport, "y", "viewport");
            requireFiniteNumberIfPresent(viewport, "zoom", "viewport");
            normalized.put("viewport", viewport);
        }
        return normalized;
    }

    /** Parses a JSON object returned by PostgreSQL and reports malformed stored data clearly. */
    private static JSONObject parseObject(String value, String field) {
        try {
            return asObject(JSON.parseObject(value), field);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(field + " must be a JSON object", e);
        }
    }

    /** Requires a JSON object for a layout field. */
    private static JSONObject asObject(Object value, String field) {
        if (value instanceof JSONObject object) {
            return object;
        }
        if (value instanceof Map<?, ?>) {
            return JSON.parseObject(JSON.toJSONString(value));
        }
        throw new IllegalArgumentException(field + " must be a JSON object");
    }

    /** Requires a finite numeric coordinate. */
    private static void requireFiniteNumber(JSONObject object, String field, String context) {
        Object value = object.get(field);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(context + " requires a finite " + field);
        }
    }

    /** Validates an optional finite numeric viewport field. */
    private static void requireFiniteNumberIfPresent(JSONObject object, String field, String context) {
        if (object.containsKey(field)) {
            requireFiniteNumber(object, field, context);
        }
    }

    /** Creates the response shape used when a domain has no saved layout. */
    private static JSONObject emptyLayout() {
        JSONObject layout = new JSONObject();
        layout.put("positions", new JSONObject());
        return layout;
    }

    /** Ensures the authenticated domain identifier is usable as the storage key. */
    private static void requireDomainId(String domainId) {
        if (domainId == null || domainId.isBlank()) {
            throw new IllegalArgumentException("domainId is required");
        }
    }
}
