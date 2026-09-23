package com.portal26.hive.session;

import com.portal26.hive.staff.enums.HiveRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "hive_session")
@Getter
@Setter
public class HiveSessionEntity {

	@Id
	@Column(length = 64)
	private String id;

	@Column(name = "staff_id", nullable = false)
	private UUID staffId;

	@Column(name = "msp_id", nullable = false)
	private UUID mspId;

	@Column(nullable = false, length = 320)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(length = 64)
	private HiveRole role;

	@Column(name = "access_token", nullable = false, columnDefinition = "text")
	private String accessToken;

	@Column(name = "id_token", nullable = false, columnDefinition = "text")
	private String idToken;

	@Column(name = "refresh_token", nullable = false, columnDefinition = "text")
	private String refreshToken;

	@Column(name = "access_token_expires_at", nullable = false)
	private Instant accessTokenExpiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	static HiveSessionEntity from(String sessionId, HiveSession session) {
		HiveSessionEntity entity = new HiveSessionEntity();
		entity.setId(sessionId);
		entity.setStaffId(session.staffId());
		entity.setMspId(session.mspId());
		entity.setEmail(session.email());
		entity.setRole(session.role());
		entity.setAccessToken(session.accessToken());
		entity.setIdToken(session.idToken());
		entity.setRefreshToken(session.refreshToken());
		entity.setAccessTokenExpiresAt(session.accessTokenExpiresAt());
		entity.setCreatedAt(session.createdAt());
		entity.setExpiresAt(session.expiresAt());
		return entity;
	}

	HiveSession toHiveSession() {
		return new HiveSession(
				staffId,
				mspId,
				email,
				role,
				accessToken,
				idToken,
				refreshToken,
				accessTokenExpiresAt,
				createdAt,
				expiresAt);
	}

	void apply(HiveSession session) {
		setStaffId(session.staffId());
		setMspId(session.mspId());
		setEmail(session.email());
		setRole(session.role());
		setAccessToken(session.accessToken());
		setIdToken(session.idToken());
		setRefreshToken(session.refreshToken());
		setAccessTokenExpiresAt(session.accessTokenExpiresAt());
		setCreatedAt(session.createdAt());
		setExpiresAt(session.expiresAt());
	}
}
