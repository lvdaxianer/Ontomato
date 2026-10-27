package io.ontomato.dataengine.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Optional one-shot backfill for deployments that turn on connection-secret
 * encryption after records were already stored as plaintext.
 *
 * <p>Disabled by default. Set {@code ontomato.connection-secrets.backfill=true}
 * once, start the service, then set it back to false. The operation is
 * idempotent: rows already carrying {@code enc:v1:} are not rewritten.</p>
 */
@Slf4j
@Component
public class ConnectionSecretBackfillRunner {

	@Autowired(required = false)
	private BusinessConfigDao businessConfigDao;

	@Value("${ontomato.connection-secrets.backfill:false}")
	private boolean enabled;

	@EventListener(ApplicationReadyEvent.class)
	public void backfill() {
		if (!enabled || businessConfigDao == null) {
			return;
		}
		try {
			int changed = businessConfigDao.backfillConnectionSecrets();
			log.info("Connection-secret backfill completed: {} records updated", changed);
		} catch (RuntimeException e) {
			log.error("Connection-secret backfill failed", e);
		}
	}
}
