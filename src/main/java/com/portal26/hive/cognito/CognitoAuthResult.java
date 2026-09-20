package com.portal26.hive.cognito;

import java.time.Instant;

public record CognitoAuthResult(
		String idToken,
		String accessToken,
		String refreshToken,
		String email,
		Instant accessTokenExpiresAt) {
}
