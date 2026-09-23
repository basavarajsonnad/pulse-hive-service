package com.portal26.hive.session;

import java.util.Optional;

public interface SessionStore {

	void save(String sessionId, HiveSession session);

	Optional<HiveSession> find(String sessionId);

	void delete(String sessionId);
}
