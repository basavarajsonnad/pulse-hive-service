package com.portal26.hive.provisioning.dto;

import java.time.Instant;

public record CustomerListItem(
		String customerName,
		String tenantName,
		String status,
		Instant createdAt,
		Instant updatedAt) {
}
