package com.portal26.hive.session;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class DbSessionStore implements SessionStore {

	private final HiveSessionRepository hiveSessionRepository;

	@Override
	@Transactional
	public void save(String sessionId, HiveSession session) {
		HiveSessionEntity entity = hiveSessionRepository.findById(sessionId)
				.orElseGet(HiveSessionEntity::new);
		if (entity.getId() == null) {
			entity = HiveSessionEntity.from(sessionId, session);
		} else {
			entity.apply(session);
		}
		hiveSessionRepository.save(entity);
	}

	@Override
	@Transactional
	public Optional<HiveSession> find(String sessionId) {
		Optional<HiveSessionEntity> found = hiveSessionRepository.findById(sessionId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		HiveSessionEntity entity = found.get();
		if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now())) {
			hiveSessionRepository.delete(entity);
			return Optional.empty();
		}
		return Optional.of(entity.toHiveSession());
	}

	@Override
	@Transactional
	public void delete(String sessionId) {
		hiveSessionRepository.deleteById(sessionId);
	}
}
