package com.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code POST /v1/tenants}.
 *
 * <p>Core rejects unknown properties, so this must carry nothing beyond {@code customer_name} and
 * {@code sso} -- no environment, no gateway name. Nulls are omitted rather than serialized.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoreCreateTenantRequest(
		@JsonProperty("customer_name") String customerName,
		@JsonProperty("sso") CoreSsoConfig sso) {
}
