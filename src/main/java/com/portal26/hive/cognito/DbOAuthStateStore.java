package com.portal26.hive.cognito;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
public class DbOAuthStateStore implements OAuthStateStore {

	private static final Duration TTL = Duration.ofMinutes(10);

	private final OAuthStateRepository oauthStateRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public DbOAuthStateStore(OAuthStateRepository oauthStateRepository) {
		this.oauthStateRepository = oauthStateRepository;
	}

	@Override
	@Transactional
	public void save(String state, String codeVerifier) {
		OAuthStateEntity entity = new OAuthStateEntity();
		entity.setState(state);
		entity.setCodeVerifier(codeVerifier);
		entity.setExpiresAt(Instant.now().plus(TTL));
		oauthStateRepository.save(entity);
	}

	@Override
	@Transactional
	@SuppressWarnings("unchecked")
	public Optional<String> consume(String state) {
		List<String> results = entityManager.createNativeQuery(
						"DELETE FROM oauth_state WHERE state = :state AND expires_at > :now RETURNING code_verifier")
				.setParameter("state", state)
				.setParameter("now", Instant.now())
				.getResultList();
		if (results.isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(results.getFirst());
	}
}
