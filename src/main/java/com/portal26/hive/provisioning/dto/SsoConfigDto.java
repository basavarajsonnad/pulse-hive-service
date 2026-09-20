package com.portal26.hive.provisioning.dto;

import jakarta.validation.constraints.NotBlank;

/** Core treats every field as mandatory once sso is present, so all four are validated here. */
public record SsoConfigDto(
		@NotBlank(message = "sso.metadataUrl is required") String metadataUrl,
		@NotBlank(message = "sso.providerName is required") String providerName,
		@NotBlank(message = "sso.emailAttribute is required") String emailAttribute,
		@NotBlank(message = "sso.groupsAttribute is required") String groupsAttribute) {
}
