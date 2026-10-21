package io.ontomato.dataengine.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/** Persists append-only ontology definition revisions in PostgreSQL.
 *
 * @author lvdaxianer@yeah.net
 */
@Slf4j
@Component("ontologyRevisionDao")
public class OntologyRevisionDao {

    private static final String TABLE_NAME = "ontology_revisions";
    private static final String DOMAIN_INDEX = "idx_ontology_revisions_domain_created";
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Creates the revision table and its domain lookup index idempotently. */
    @PostConstruct
    public void initIndex() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "domain_id VARCHAR(64) NOT NULL, "
                + "revision BIGINT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "content_hash VARCHAR(128) NOT NULL, "
                + "source VARCHAR(32) NOT NULL, "
                + "actor_id VARCHAR(128), "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (domain_id, revision)"
                + ")");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + DOMAIN_INDEX + " ON " + TABLE_NAME
                + " (domain_id, created_at DESC)");
        log.info("Ontology revision table initialization complete");
    }

    /** Appends one immutable revision snapshot without an update or upsert path. */
    public void save(OntologyRevisionRecord revision) {
        if (revision == null) {
            throw new IllegalArgumentException("revision is required");
        }
        jdbcTemplate.update(
                "INSERT INTO " + TABLE_NAME
                        + " (domain_id, revision, content, content_hash, source, actor_id) VALUES (?, ?, ?, ?, ?, ?)",
                revision.domainId(),
                revision.revision(),
                revision.content(),
                revision.contentHash(),
                revision.source(),
                revision.actorId());
    }

    /** Appends a migration row when absent and treats a concurrent identical bootstrap as success. */
    public boolean saveIfAbsent(OntologyRevisionRecord revision) {
        if (revision == null) {
            throw new IllegalArgumentException("revision is required");
        }
        int inserted = jdbcTemplate.update(
                "INSERT INTO " + TABLE_NAME
                        + " (domain_id, revision, content, content_hash, source, actor_id) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (domain_id, revision) DO NOTHING",
                revision.domainId(),
                revision.revision(),
                revision.content(),
                revision.contentHash(),
                revision.source(),
                revision.actorId());
        return inserted == 1;
    }

    /** Reads one revision for a domain without leaking JDBC exceptions to callers. */
    public Optional<OntologyRevisionRecord> find(String domainId, long revision) {
        requireDomainId(domainId);
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        try {
            OntologyRevisionRecord result = jdbcTemplate.queryForObject(
                    "SELECT domain_id, revision, content, content_hash, source, actor_id, created_at FROM "
                            + TABLE_NAME + " WHERE domain_id = ? AND revision = ?",
                    (resultSet, rowNum) -> map(resultSet),
                    domainId,
                    revision);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Lists bounded revisions in newest-first order for the history panel. */
    public List<OntologyRevisionRecord> list(String domainId, int limit, int offset) {
        requireDomainId(domainId);
        if (limit < 1 || limit > MAX_PAGE_SIZE || offset < 0) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE
                    + " and offset must be non-negative");
        }
        return jdbcTemplate.query(
                "SELECT domain_id, revision, content, content_hash, source, actor_id, created_at FROM "
                        + TABLE_NAME + " WHERE domain_id = ? ORDER BY revision DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> map(resultSet),
                domainId,
                limit,
                offset);
    }

    /** Converts one PostgreSQL row into the immutable revision model. */
    private static OntologyRevisionRecord map(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new OntologyRevisionRecord(
                resultSet.getString("domain_id"),
                resultSet.getLong("revision"),
                resultSet.getString("content"),
                resultSet.getString("content_hash"),
                resultSet.getString("source"),
                resultSet.getString("actor_id"),
                createdAt == null ? null : createdAt.toInstant());
    }

    /** Validates the authenticated domain identifier used by every query. */
    private static void requireDomainId(String domainId) {
        if (domainId == null || domainId.isBlank()) {
            throw new IllegalArgumentException("domainId is required");
        }
    }
}
