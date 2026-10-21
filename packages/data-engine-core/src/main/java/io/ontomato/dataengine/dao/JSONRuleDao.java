package io.ontomato.dataengine.dao;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter.Feature;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("jsonRuleDao")
public class JSONRuleDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String TABLE_NAME = "json_rule";

	@PostConstruct
	public void initIndex() {
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
				+ "id VARCHAR(64) PRIMARY KEY, "
				+ "content TEXT"
				+ ")");
		jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME
				+ " ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 0");
		jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME
				+ " ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128)");
		log.info(">>>jsonRule table initialization complete");
	}

	public void save(Map<String, Object> jsonRule, String domainId) {
		jdbcTemplate.update(
				"INSERT INTO " + TABLE_NAME + " (id, content) VALUES (?, ?) "
						+ "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content",
				domainId, JSON.toJSONString(jsonRule, Feature.WriteMapNullValue));
	}

	public Map<String, Object> query(String domainId) {
		Map<String, Object> jsonRule = null;
		try {
			String content = jdbcTemplate.queryForObject(
					"SELECT content FROM " + TABLE_NAME + " WHERE id = ?",
					String.class, domainId);
			if (content != null) {
				jsonRule = JSONObject.parseObject(content);
			}
		} catch (org.springframework.dao.EmptyResultDataAccessException e) {
			return null;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return jsonRule;
	}

	/** Reads the active ontology rule together with its optimistic-concurrency metadata. */
	public Optional<JSONRuleState> queryState(String domainId) {
		try {
			JSONRuleState state = jdbcTemplate.queryForObject(
					"SELECT content, revision, content_hash FROM " + TABLE_NAME + " WHERE id = ?",
					(resultSet, rowNum) -> new JSONRuleState(
							resultSet.getString("content"),
							resultSet.getLong("revision"),
							resultSet.getString("content_hash")),
					domainId);
			return Optional.ofNullable(state);
		} catch (org.springframework.dao.EmptyResultDataAccessException e) {
			return Optional.empty();
		}
	}

	/** Promotes the active rule only when the caller still owns the expected revision and hash. */
	public boolean promote(
			String domainId,
			long expectedRevision,
			String expectedHash,
			long newRevision,
			String content,
			String contentHash) {
		if (expectedRevision < 0 || newRevision < 1 || content == null || content.isBlank()
				|| contentHash == null || contentHash.isBlank()) {
			throw new IllegalArgumentException("invalid ontology promotion arguments");
		}
		int updated = jdbcTemplate.update(
				"UPDATE " + TABLE_NAME + " SET content = ?, revision = ?, content_hash = ? "
						+ "WHERE id = ? AND revision = ? AND content_hash IS NOT DISTINCT FROM ?",
				content,
				newRevision,
				contentHash,
				domainId,
				expectedRevision,
				expectedHash);
		return updated == 1;
	}
}
