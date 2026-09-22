package com.portal26.hive.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.core.client.dto.CoreJobStepResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProvisioningStatusesTest {

	@Test
	void inProgressWithCriticalSucceededStaysRunning() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", "pending", null));
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_IN_PROGRESS, null, steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_RUNNING);
		assertThat(ProvisioningStatuses.isTerminal(ProvisioningStatuses.DB_RUNNING)).isFalse();
	}

	@Test
	void coreCompleteMapsToCompleted() {
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", List.of(allSucceeded()));

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_COMPLETED);
		assertThat(ProvisioningStatuses.isTerminal(ProvisioningStatuses.DB_COMPLETED)).isTrue();
	}

	@Test
	void criticalOkAndBestEffortFailedMapsToCompletedWithErrors() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_SEGMENTS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("SAML_REGISTRATION", ProvisioningStatuses.STEP_FAILED, "SSO metadata rejected"));
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_FAILED, "acme.portal26.ai", steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS);
		assertThat(ProvisioningStatuses.firstFailedStepDetail(core)).isEqualTo("SSO metadata rejected");
		assertThat(ProvisioningStatuses.isTerminal(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS)).isTrue();
	}

	@Test
	void coreFailedWithCriticalFailedMapsToFailed() {
		CoreJobStatusResponse core = job(
				ProvisioningStatuses.CORE_FAILED,
				null,
				List.of(
						step("CREATE_TENANT", ProvisioningStatuses.STEP_FAILED, "tenant already exists"),
						step("AWAIT_PROVISIONING", "pending", null),
						step("RESOLVE_TENANT_NAME", "pending", null)));

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_FAILED);
		assertThat(ProvisioningStatuses.firstFailedStepDetail(core)).isEqualTo("tenant already exists");
		assertThat(ProvisioningStatuses.isTerminal(ProvisioningStatuses.DB_FAILED)).isTrue();
	}

	@Test
	void unknownCoreStatusIsSkipped() {
		CoreJobStatusResponse core = job("queued", null, List.of());

		assertThat(ProvisioningStatuses.resolveHiveStatus(core)).isEmpty();
	}

	private static CoreJobStatusResponse job(
			String status, String tenantName, List<CoreJobStepResponse> steps) {
		return new CoreJobStatusResponse("job_1a2b3c4d", "acme-corp", tenantName, status, steps);
	}

	private static CoreJobStepResponse[] criticalSucceeded() {
		return new CoreJobStepResponse[] {
			step("CREATE_TENANT", ProvisioningStatuses.STEP_SUCCEEDED, null),
			step("AWAIT_PROVISIONING", ProvisioningStatuses.STEP_SUCCEEDED, null),
			step("RESOLVE_TENANT_NAME", ProvisioningStatuses.STEP_SUCCEEDED, null)
		};
	}

	private static CoreJobStepResponse[] allSucceeded() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_SEGMENTS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("SAML_REGISTRATION", ProvisioningStatuses.STEP_SUCCEEDED, null));
		return steps.toArray(CoreJobStepResponse[]::new);
	}

	private static CoreJobStepResponse step(String name, String status, String detail) {
		return new CoreJobStepResponse(name, status, detail);
	}
}
