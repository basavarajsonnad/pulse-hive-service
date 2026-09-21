package com.portal26.hive.cognito;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-only OAuth state store so tests do not need Redis.
 */
@Component
@Profile("test")
@Primary
public class InMemoryOAuthStateStore implements OAuthStateStore {

	private final Map<String, String> states = new ConcurrentHashMap<>();

	@Override
	public void save(String state, String codeVerifier) {
		states.put(state, codeVerifier);
	}

	@Override
	public Optional<String> consume(String state) {
		return Optional.ofNullable(states.remove(state));
	}
}
