package ai.portal26.hive.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreJobStatusResponse(
		@JsonProperty("job_id") String jobId,
		@JsonProperty("customer_name") String customerName,
		@JsonProperty("tenant_name") String tenantName,
		@JsonProperty("status") String status,
		@JsonProperty("steps") List<CoreJobStepResponse> steps) {
}
