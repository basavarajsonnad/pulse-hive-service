package ai.portal26.hive.provisioning.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoConfig(
		@NotBlank(message = "sso.metadataUrl is required") String metadataUrl,
		@NotBlank(message = "sso.providerName is required") String providerName,
		@NotBlank(message = "sso.emailAttribute is required") String emailAttribute,
		@NotBlank(message = "sso.groupsAttribute is required") String groupsAttribute) {
}
