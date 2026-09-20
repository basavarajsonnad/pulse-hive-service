package com.portal26.hive.provisioning.dto;

import java.time.OffsetDateTime;

public record JobListItem(
		String jobId,
		String customerName,
		String status,
		OffsetDateTime createdAt) {
}
