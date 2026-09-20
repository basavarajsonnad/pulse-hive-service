package com.portal26.hive.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

	@Id
	private UUID id;

	@Column(name = "msp_id", nullable = false)
	private UUID mspId;

	@Column(nullable = false)
	private String name;

	@Column(name = "tenant_name")
	private String tenantName;

	@Column(nullable = false)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public UUID getMspId() {
		return mspId;
	}

	public String getName() {
		return name;
	}

	public String getTenantName() {
		return tenantName;
	}

	public String getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}
}
