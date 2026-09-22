package com.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreJobStepResponse(String name, String status, String detail) {
}
