package com.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Core's poll response for {@code GET /v1/tenants/{jobId}}.
 *
 * <p>{@code ignoreUnknown} is required, not defensive: Core also sends {@code steps}, {@code
 * started_at} and {@code updated_at}, none of which this increment stores. {@code tenantName} is
 * null while the key is absent, which Core uses to mean "not resolved yet" -- it never sends an
 * explicit null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreJobResponse(
		@JsonProperty("job_id") String jobId,
		@JsonProperty("customer_name") String customerName,
		@JsonProperty("tenant_name") String tenantName,
		@JsonProperty("status") String status) {
}
