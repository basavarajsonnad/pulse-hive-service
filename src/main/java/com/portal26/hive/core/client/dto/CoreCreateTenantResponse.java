package com.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Core's {@code 202 Accepted} body. {@code status} is always {@code in_progress} here.
 *
 * <p>There is no {@code tenant_name}: Core generates it partway through the job, so it only appears
 * on the poll response once RESOLVE_TENANT_NAME has succeeded.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreCreateTenantResponse(
		@JsonProperty("job_id") String jobId,
		@JsonProperty("customer_name") String customerName,
		@JsonProperty("status") String status) {
}
