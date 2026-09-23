package com.portal26.hive.cognito;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oauth_state")
@Getter
@Setter
public class OAuthStateEntity {

	@Id
	@Column(length = 128)
	private String state;

	@Column(name = "code_verifier", nullable = false, length = 128)
	private String codeVerifier;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
