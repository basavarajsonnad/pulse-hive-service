package com.portal26.hive.customer.entity;

import com.portal26.hive.provisioning.dto.SsoConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_signin_config")
public class TenantSigninConfig {

	public static final String PROTOCOL_SAML = "saml";
	public static final String STATUS_ACTIVE = "active";

	@Id
	private UUID id;

	@Column(name = "msp_id", nullable = false)
	private UUID mspId;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Column(nullable = false)
	private String protocol;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "provider_input", nullable = false)
	private Map<String, Object> providerInput;

	@Column(name = "registration_output")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> registrationOutput;

	@Column(nullable = false)
	private String status;

	@Column(name = "registered_at")
	private Instant registeredAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public static TenantSigninConfig forSamlCreate(UUID mspId, UUID customerId, SsoConfig sso) {
		Instant now = Instant.now();
		TenantSigninConfig config = new TenantSigninConfig();
		config.id = UUID.randomUUID();
		config.mspId = mspId;
		config.customerId = customerId;
		config.protocol = PROTOCOL_SAML;
		config.providerInput = providerInput(sso);
		config.status = STATUS_ACTIVE;
		config.createdAt = now;
		config.updatedAt = now;
		return config;
	}

	private static Map<String, Object> providerInput(SsoConfig sso) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("metadata_url", sso.metadataUrl());
		input.put("provider_name", sso.providerName());
		input.put("email_attribute", sso.emailAttribute());
		input.put("groups_attribute", sso.groupsAttribute());
		return input;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMspId() {
		return mspId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getProtocol() {
		return protocol;
	}

	public Map<String, Object> getProviderInput() {
		return providerInput;
	}

	public String getStatus() {
		return status;
	}
}
