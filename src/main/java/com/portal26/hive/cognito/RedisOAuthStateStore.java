package com.portal26.hive.cognito;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisOAuthStateStore implements OAuthStateStore {

	private static final String KEY_PREFIX = "hive:oauth:state:";
	private static final Duration TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redisTemplate;

	@Override
	public void save(String state, String codeVerifier) {
		redisTemplate.opsForValue().set(KEY_PREFIX + state, codeVerifier, TTL);
	}

	@Override
	public Optional<String> consume(String state) {
		String key = KEY_PREFIX + state;
		String verifier = redisTemplate.opsForValue().get(key);
		if (verifier != null) {
			redisTemplate.delete(key);
		}
		return Optional.ofNullable(verifier);
	}
}
