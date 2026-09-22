package com.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreJobStepResponse(
		String name,
		String status,
		String detail,
		@JsonProperty("ended_at") Instant endedAt) {

	public CoreJobStepResponse(String name, String status, String detail) {
		this(name, status, detail, null);
	}
}
