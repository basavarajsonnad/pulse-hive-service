package com.portal26.hive.msp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "msp")
public class Msp {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String subdomain;

	@Column(nullable = false)
	private String status;

	@Column(name = "identity_pool_reference")
	private String identityPoolReference;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSubdomain() {
		return subdomain;
	}

	public String getStatus() {
		return status;
	}

	public String getIdentityPoolReference() {
		return identityPoolReference;
	}

	/**
	 * First-login seed from Cognito custom:provider when no matching MSP row exists.
	 */
	public static Msp forProviderCreate(String providerName) {
		Msp msp = new Msp();
		msp.id = UUID.randomUUID();
		msp.name = providerName;
		msp.subdomain = slug(providerName) + "-hive";
		msp.status = "active";
		msp.identityPoolReference = providerName;
		return msp;
	}

	private static String slug(String value) {
		String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return slug.isBlank() ? "msp" : slug;
	}

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
}
