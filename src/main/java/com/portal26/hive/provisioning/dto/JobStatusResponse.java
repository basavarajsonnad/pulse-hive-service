package com.portal26.hive.provisioning.dto;

/**
 * {@code 200} body of the status API, read from Hive's own table.
 *
 * <p>Core's {@code steps[]} is deliberately not exposed here.
 */
public record JobStatusResponse(String jobId, String customerName, String status) {
}
