package com.portal26.hive.provisioning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/v1/tenants}.
 *
 * <p>The pattern is Core's, enforced here so an invalid name never reaches Core. {@code sso} is
 * required even though Core treats it as optional, because the UI type declares it required.
 */
public record CreateTenantRequest(
		@Pattern(
				regexp = "^[a-zA-Z0-9-]{3,25}$",
				message = "customerName must be 3-25 characters of letters, digits or hyphens")
		@NotNull(message = "customerName is required")
		String customerName,

		@NotNull(message = "sso is required") @Valid SsoConfigDto sso) {
}
