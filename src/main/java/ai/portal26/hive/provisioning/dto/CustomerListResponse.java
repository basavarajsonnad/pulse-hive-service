package ai.portal26.hive.provisioning.dto;

import java.util.List;

public record CustomerListResponse(
		List<CustomerListItem> customers, int page, int size, long totalElements, int totalPages) {
}
