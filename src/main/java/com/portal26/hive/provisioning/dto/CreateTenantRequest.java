package com.portal26.hive.provisioning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateTenantRequest(
		@NotBlank(message = "customerName is required") String customerName,
		@NotBlank(message = "licensePackage is required")
		@Pattern(
				regexp = "^(basic|intermediate|advanced)$",
				message = "licensePackage must be basic, intermediate, or advanced")
		String licensePackage,
		@NotNull(message = "sso is required") SsoConfig sso) {
}
