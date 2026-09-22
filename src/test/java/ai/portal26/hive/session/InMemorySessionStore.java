package ai.portal26.hive.session;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-only SessionStore so tests do not need Redis.
 */
@Component
@Profile("test")
@Primary
public class InMemorySessionStore implements SessionStore {

	private final Map<String, HiveSession> sessions = new ConcurrentHashMap<>();

	@Override
	public void save(String sessionId, HiveSession session) {
		sessions.put(sessionId, session);
	}

	@Override
	public Optional<HiveSession> find(String sessionId) {
		return Optional.ofNullable(sessions.get(sessionId));
	}

	@Override
	public void delete(String sessionId) {
		sessions.remove(sessionId);
	}
}
