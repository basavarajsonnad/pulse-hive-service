package com.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Core requires all four fields to be present and non-blank whenever {@code sso} is sent.
 *
 * <p>Field names are spelled out with {@link JsonProperty} rather than a naming strategy: the HTTP
 * layer here runs Jackson 3, which ignores Jackson 2's {@code @JsonNaming}, and getting this wrong
 * silently sends camelCase that Core rejects as unknown fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoreSsoConfig(
		@JsonProperty("metadata_url") String metadataUrl,
		@JsonProperty("provider_name") String providerName,
		@JsonProperty("email_attribute") String emailAttribute,
		@JsonProperty("groups_attribute") String groupsAttribute) {
}
