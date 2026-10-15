package io.ontomato.dataengine.dao;

import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter.Feature;
import io.ontomato.dataengine.config.AgentConfig;
import io.ontomato.dataengine.config.BusinessConfig;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("businessConfigDao")
public class BusinessConfigDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String TABLE_NAME = "business_config";

	@PostConstruct
	public void initIndex() {
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
				+ "id VARCHAR(64) PRIMARY KEY, "
				+ "content TEXT"
				+ ")");
		log.info(">>>businessConfig table initialization complete");
	}

	public void save(BusinessConfig businessConfig, String domainId) {
		jdbcTemplate.update(
				"INSERT INTO " + TABLE_NAME + " (id, content) VALUES (?, ?) "
						+ "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content",
				domainId, JSON.toJSONString(businessConfig, Feature.WriteMapNullValue));
	}

	public BusinessConfig query(String domainId) {
		try {
			return queryPersisted(domainId, 0);
		} catch (org.springframework.dao.EmptyResultDataAccessException e) {
			return null;
		}
	}

	/**
	 * Reads and, when necessary, conditionally migrates one persisted domain configuration.
	 *
	 * @param domainId domain whose configuration is being read
	 * @param retryCount number of compare-and-set retries already attempted
	 * @return persisted business configuration, or null when no row exists
	 * @author lvdaxianer@yeah.net
	 * @date 2026-10-15
	 */
	private BusinessConfig queryPersisted(String domainId, int retryCount) {
		String content = jdbcTemplate.queryForObject(
				"SELECT content FROM " + TABLE_NAME + " WHERE id = ?",
				String.class, domainId);
		if (content == null) {
			return null;
		}
		JSONObject stored = JSON.parseObject(content, JSONReader.Feature.DisableReferenceDetect);
		if (stored == null) {
			return null;
		}
		if (!stored.containsKey("models")) {
			throw new IllegalStateException(
					"business_config record is in the old format; run the upgrade migration first (domainId=" + domainId + ")");
		}
		rejectLegacyShapes(stored, domainId);
		boolean dataAdapterConnectionsMigrated = LegacyDataAdapterConnections.migrate(stored);
		boolean embeddingVectorSourceMigrated = migrateEmbeddingVectorSource(stored);
		if (dataAdapterConnectionsMigrated || embeddingVectorSourceMigrated) {
			String migratedContent = JSON.toJSONString(stored, Feature.WriteMapNullValue);
			if (embeddingVectorSourceMigrated) {
				int updated = jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET content = ? WHERE id = ? AND content = ?",
						migratedContent, domainId, content);
				if (updated == 0) {
					if (retryCount >= 1) {
						throw new IllegalStateException("business_config changed concurrently; retry the request (domainId=" + domainId + ")");
					}
					return queryPersisted(domainId, retryCount + 1);
				}
			} else {
				jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET content = ? WHERE id = ?", migratedContent, domainId);
			}
			content = migratedContent;
			if (dataAdapterConnectionsMigrated) {
				log.info("business_config data adapter connections migrated (domainId={})", domainId);
			}
		}
		return JSON.parseObject(content, BusinessConfig.class, JSONReader.Feature.DisableReferenceDetect);
	}

	/**
	 * Mark a legacy domain's vector source as unknown so a configured embedding model requires an
	 * explicit rebuild. The migration never invents the model URL, name, or dimensions.
	 *
	 * @param stored persisted business configuration JSON
	 * @return whether the JSON was changed
	 * @author lvdaxianer@yeah.net
	 * @date 2026-10-15
	 */
	static boolean migrateEmbeddingVectorSource(JSONObject stored) {
		if (stored.get("embeddingVectorSource") != null) {
			return false;
		}
		stored.put("embeddingVectorSource", new JSONObject());
		return true;
	}

	private static final Set<String> LEGACY_ROLE_KEYS = Set.of(
			"normalChat", "questionSpliter", "cleverNormalChat", "pythonChat", "toolChat");

	static void rejectLegacyShapes(JSONObject stored, String domainId) {
		JSONObject agentsObj = stored.getJSONObject("agents");
		if (agentsObj != null) {
			for (String oldKey : LEGACY_ROLE_KEYS) {
				if (agentsObj.containsKey(oldKey)) {
					throw new IllegalStateException(
							"business_config record contains legacy role key " + oldKey
									+ "; run the upgrade migration first (domainId=" + domainId + ")");
				}
			}
			for (Map.Entry<String, Object> entry : agentsObj.entrySet()) {
				if (entry.getValue() instanceof Map<?, ?> roleMap) {
					if (roleMap.containsKey("actorCopies")) {
						throw new IllegalStateException(
								"business_config record contains legacy agent parameter actorCopies in role "
										+ entry.getKey() + "; run the upgrade migration first (domainId=" + domainId + ")");
					}
					for (String legacy : AgentConfig.LEGACY_MODEL_FIELDS) {
						if (roleMap.containsKey(legacy)) {
							throw new IllegalStateException(
									"business_config record still contains role-side model parameters (" + legacy
											+ " in role " + entry.getKey()
											+ "); run the upgrade migration first (domainId=" + domainId + ")");
						}
					}
				}
			}
		}

		JSONArray modelsArr = stored.getJSONArray("models");
		if (modelsArr != null) {
			for (int i = 0; i < modelsArr.size(); i++) {
				Object item = modelsArr.get(i);
				if (item instanceof Map<?, ?> modelMap) {
					if (modelMap.containsKey("strictTools") || modelMap.containsKey("strict_tools") || modelMap.containsKey("strict-tools")) {
						throw new IllegalStateException(
								"business_config record contains legacy model parameter strictTools; run the upgrade migration first (domainId="
										+ domainId + ")");
					}
				}
			}
		}
	}
}
