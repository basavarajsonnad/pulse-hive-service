package com.portal26.hive.session;

import com.portal26.hive.staff.enums.HiveRole;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record HiveSession(
		UUID staffId,
		UUID mspId,
		String email,
		HiveRole role,
		String accessToken,
		String idToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		Instant createdAt,
		Instant expiresAt) implements Serializable {
}
