package com.portal26.hive.provisioning.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerListItem(
		UUID customerId,
		UUID mspId,
		String customerName,
		String tenantName,
		String licensePackage,
		String status,
		Instant createdAt,
		Instant updatedAt) {
}
