package com.portal26.hive.provisioning.dto;

public record SsoConfig(String metadataUrl, String providerName, String emailAttribute, String groupsAttribute) {
}
