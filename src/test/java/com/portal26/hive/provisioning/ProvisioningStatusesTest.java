package com.portal26.hive.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.core.client.dto.CoreJobStepResponse;
import java.time.Instant;
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
	void doesNotMapUntilAllEightStepsFinished() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", ProvisioningStatuses.STEP_FAILED, "TURBO_AND_MDM failed"));
		steps.add(step("LD_SEGMENTS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("SAML_REGISTRATION", "pending", null));
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_RUNNING);
	}

	@Test
	void inProgressWithBestEffortFailStaysRunningUntilStep8() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", ProvisioningStatuses.STEP_FAILED, "TURBO_AND_MDM failed"));
		steps.add(step("SAML_REGISTRATION", "pending", null));
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_IN_PROGRESS, "acme.portal26.ai", steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_RUNNING);
	}

	@Test
	void coreCompleteWithBestEffortFailedMapsToCompletedWithErrors() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", ProvisioningStatuses.STEP_FAILED, "TURBO_AND_MDM failed"));
		steps.add(step("LD_SEGMENTS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("SAML_REGISTRATION", ProvisioningStatuses.STEP_SUCCEEDED, "MANUAL STEP"));
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS);
		assertThat(ProvisioningStatuses.toCustomerStatus(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS))
				.isEqualTo(ProvisioningStatuses.DB_COMPLETED);
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
				job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS);
		assertThat(ProvisioningStatuses.firstFailedStepDetail(core)).isEqualTo("SSO metadata rejected");
		assertThat(ProvisioningStatuses.isTerminal(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS)).isTrue();
	}

	@Test
	void coreCompleteWithAllBestEffortFailedMapsToCompletedWithErrors() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		for (String name : ProvisioningStatuses.BEST_EFFORT_STEPS) {
			steps.add(step(name, ProvisioningStatuses.STEP_FAILED, name + " failed"));
		}
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS);
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
	void completedWithErrorsMapsToCompletedOnCustomer() {
		assertThat(ProvisioningStatuses.toCustomerStatus(ProvisioningStatuses.DB_RUNNING))
				.isEqualTo(ProvisioningStatuses.CUSTOMER_IN_PROGRESS);
		assertThat(ProvisioningStatuses.toCustomerStatus(ProvisioningStatuses.DB_COMPLETED))
				.isEqualTo(ProvisioningStatuses.DB_COMPLETED);
		assertThat(ProvisioningStatuses.toCustomerStatus(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS))
				.isEqualTo(ProvisioningStatuses.DB_COMPLETED);
		assertThat(ProvisioningStatuses.toCustomerStatus(ProvisioningStatuses.DB_FAILED))
				.isEqualTo(ProvisioningStatuses.DB_FAILED);
	}

	@Test
	void currentStepProgressPrefersRunningThenPending() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", "running", null));
		steps.add(step("SAML_REGISTRATION", "pending", null));
		assertThat(ProvisioningStatuses.currentStepProgress(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, null, steps)))
				.isEqualTo("TURBO_AND_MDM running");

		List<CoreJobStepResponse> pendingOnly = new ArrayList<>(List.of(criticalSucceeded()));
		pendingOnly.add(step("TURBO_AND_MDM", "pending", null));
		assertThat(ProvisioningStatuses.currentStepProgress(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, null, pendingOnly)))
				.isEqualTo("TURBO_AND_MDM pending");
	}

	@Test
	void midRunItemStatusCompletesAfterThreeCriticalSteps() {
		List<CoreJobStepResponse> afterThree = new ArrayList<>(List.of(criticalSucceeded()));
		afterThree.add(step("TURBO_AND_MDM", "running", null));
		assertThat(ProvisioningStatuses.midRunItemStatus(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, "acme.portal26.ai", afterThree)))
				.isEqualTo(ProvisioningStatuses.DB_COMPLETED);
		assertThat(ProvisioningStatuses.midRunItemError(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, "acme.portal26.ai", afterThree)))
				.isNull();

		afterThree.add(step("LD_SEGMENTS", ProvisioningStatuses.STEP_FAILED, "LD_SEGMENTS failed"));
		assertThat(ProvisioningStatuses.midRunItemStatus(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, "acme.portal26.ai", afterThree)))
				.isEqualTo(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS);
		assertThat(ProvisioningStatuses.midRunItemError(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, "acme.portal26.ai", afterThree)))
				.isEqualTo("LD_SEGMENTS failed");
	}

	@Test
	void failedStepDetailsJoinsEveryFailedBestEffortStep() {
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(step("TURBO_AND_MDM", ProvisioningStatuses.STEP_FAILED, "TURBO_AND_MDM failed"));
		steps.add(step("LD_SEGMENTS", ProvisioningStatuses.STEP_FAILED, "LD_SEGMENTS failed"));
		steps.add(step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED, null));
		steps.add(step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_FAILED, "LD_BACKEND_FLAGS failed"));
		steps.add(step("SAML_REGISTRATION", ProvisioningStatuses.STEP_SUCCEEDED, "MANUAL STEP"));
		CoreJobStatusResponse core =
				job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", steps);

		assertThat(ProvisioningStatuses.resolveHiveStatus(core))
				.contains(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS);
		assertThat(ProvisioningStatuses.failedStepDetails(core))
				.isEqualTo("TURBO_AND_MDM failed; LD_SEGMENTS failed; LD_BACKEND_FLAGS failed");
	}

	@Test
	void resolvedTenantNameRequiresAllThreeCriticalSteps() {
		assertThat(ProvisioningStatuses.resolvedTenantName(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, "acme.portal26.ai", List.of(
								step("CREATE_TENANT", ProvisioningStatuses.STEP_SUCCEEDED, null),
								step("AWAIT_PROVISIONING", "running", null)))))
				.isEmpty();
		List<CoreJobStepResponse> afterThree = new ArrayList<>(List.of(criticalSucceeded()));
		afterThree.add(step("TURBO_AND_MDM", "running", null));
		assertThat(ProvisioningStatuses.resolvedTenantName(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, "acme.portal26.ai", afterThree)))
				.contains("acme.portal26.ai");
		assertThat(ProvisioningStatuses.resolvedTenantName(
						job(ProvisioningStatuses.CORE_IN_PROGRESS, null, afterThree)))
				.isEmpty();
	}

	@Test
	void unknownCoreStatusIsSkipped() {
		CoreJobStatusResponse core = job("queued", null, List.of());

		assertThat(ProvisioningStatuses.resolveHiveStatus(core)).isEmpty();
	}

	@Test
	void succeededSamlRegistrationRequiresDetail() {
		assertThat(ProvisioningStatuses.succeededSamlRegistration(
						job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", List.of(allSucceeded()))))
				.isEmpty();
		CoreJobStepResponse saml = new CoreJobStepResponse(
				ProvisioningStatuses.STEP_SAML_REGISTRATION,
				ProvisioningStatuses.STEP_SUCCEEDED,
				"redirect URI https://example.com/saml2/idpresponse",
				Instant.parse("2026-01-15T09:54:50Z"));
		List<CoreJobStepResponse> steps = new ArrayList<>(List.of(criticalSucceeded()));
		steps.add(saml);
		assertThat(ProvisioningStatuses.succeededSamlRegistration(
						job(ProvisioningStatuses.CORE_COMPLETE, "acme.portal26.ai", steps)))
				.contains(saml);
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
