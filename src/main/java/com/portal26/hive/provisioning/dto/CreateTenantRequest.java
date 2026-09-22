package com.portal26.hive.provisioning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateTenantRequest(
		@NotBlank(message = "customerName is required")
		@Pattern(
				regexp = "^[a-zA-Z0-9-]{3,25}$",
				message = "customerName must be 3-25 characters and contain only letters, digits, and hyphens")
		String customerName,
		@NotNull(message = "sso is required") @Valid SsoConfig sso) {
}
