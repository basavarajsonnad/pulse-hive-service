package com.portal26.hive.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal26.hive.config.SessionProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisSessionStore implements SessionStore {

	private static final String KEY_PREFIX = "hive:session:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final SessionProperties sessionProperties;

	@Override
	public void save(String sessionId, HiveSession session) {
		Duration ttl = sessionProperties.ttl();
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, objectMapper.writeValueAsString(session), ttl);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize session", ex);
		}
	}

	@Override
	public Optional<HiveSession> find(String sessionId) {
		String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
		if (json == null || json.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, HiveSession.class));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize session", ex);
		}
	}

	@Override
	public void delete(String sessionId) {
		redisTemplate.delete(KEY_PREFIX + sessionId);
	}
}
