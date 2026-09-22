package com.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreStartProvisioningResponse(
		@JsonProperty("job_id") String jobId,
		@JsonProperty("customer_name") String customerName,
		@JsonProperty("status") String status) {
}
