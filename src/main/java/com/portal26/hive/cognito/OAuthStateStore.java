package com.portal26.hive.cognito;

import java.util.Optional;

public interface OAuthStateStore {

	void save(String state, String codeVerifier);

	/**
	 * Returns and removes the PKCE code verifier for the given state, if present.
	 */
	Optional<String> consume(String state);
}
