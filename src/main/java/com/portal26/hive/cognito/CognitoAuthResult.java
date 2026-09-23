package com.portal26.hive.cognito;

import java.time.Instant;

public record CognitoAuthResult(
		String idToken,
		String accessToken,
		String refreshToken,
		String email,
		/** MSP provider name from custom:provider (optional). */
		String provider,
		Instant accessTokenExpiresAt) {
}
