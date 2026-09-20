package com.portal26.hive.provisioning.dto;

/** {@code 202} body of create. Status is Core's word, always {@code in_progress} at this point. */
public record CreateTenantResponse(String jobId, String customerName, String status) {
}
