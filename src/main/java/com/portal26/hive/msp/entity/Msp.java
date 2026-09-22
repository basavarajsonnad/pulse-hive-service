package com.portal26.hive.msp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
}
