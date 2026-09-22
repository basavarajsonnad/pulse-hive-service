package ai.portal26.hive.provisioning;

import ai.portal26.hive.core.client.dto.CoreJobStatusResponse;
import ai.portal26.hive.core.client.dto.CoreJobStepResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ProvisioningStatuses {

	public static final String DB_RUNNING = "running";
	public static final String DB_COMPLETED = "completed";
	public static final String DB_COMPLETED_WITH_ERRORS = "completed_with_errors";
	public static final String DB_FAILED = "failed";
	public static final String JOB_TYPE_SINGLE = "single";

	public static final String CORE_IN_PROGRESS = "in_progress";
	public static final String CORE_COMPLETE = "complete";
	public static final String CORE_FAILED = "failed";

	public static final String STEP_SUCCEEDED = "succeeded";
	public static final String STEP_FAILED = "failed";

	public static final Set<String> CRITICAL_STEPS = Set.of(
			"CREATE_TENANT", "AWAIT_PROVISIONING", "RESOLVE_TENANT_NAME");

	public static final Set<String> BEST_EFFORT_STEPS = Set.of(
			"TURBO_AND_MDM",
			"LD_SEGMENTS",
			"LD_UI_FLAGS",
			"LD_BACKEND_FLAGS",
			"SAML_REGISTRATION");

	private ProvisioningStatuses() {
	}

	public static Optional<String> resolveHiveStatus(CoreJobStatusResponse core) {
		if (core == null || core.status() == null || core.status().isBlank()) {
			return Optional.empty();
		}
		String coreStatus = core.status();
		if (CORE_IN_PROGRESS.equals(coreStatus)) {
			return Optional.of(DB_RUNNING);
		}
		if (CORE_COMPLETE.equals(coreStatus)) {
			return Optional.of(DB_COMPLETED);
		}
		if (criticalSucceeded(core) && anyBestEffortFailed(core)) {
			return Optional.of(DB_COMPLETED_WITH_ERRORS);
		}
		if (CORE_FAILED.equals(coreStatus)) {
			return Optional.of(DB_FAILED);
		}
		return Optional.empty();
	}

	public static boolean isTerminal(String hiveStatus) {
		return DB_COMPLETED.equals(hiveStatus)
				|| DB_COMPLETED_WITH_ERRORS.equals(hiveStatus)
				|| DB_FAILED.equals(hiveStatus);
	}

	public static String firstFailedStepDetail(CoreJobStatusResponse core) {
		if (core == null || core.steps() == null) {
			return null;
		}
		for (CoreJobStepResponse step : core.steps()) {
			if (step != null && STEP_FAILED.equals(step.status())) {
				if (step.detail() != null && !step.detail().isBlank()) {
					return step.detail();
				}
				return step.name();
			}
		}
		return null;
	}

	private static boolean criticalSucceeded(CoreJobStatusResponse core) {
		for (String name : CRITICAL_STEPS) {
			if (!stepHasStatus(core, name, STEP_SUCCEEDED)) {
				return false;
			}
		}
		return true;
	}

	private static boolean anyBestEffortFailed(CoreJobStatusResponse core) {
		List<CoreJobStepResponse> steps = core.steps();
		if (steps == null) {
			return false;
		}
		for (CoreJobStepResponse step : steps) {
			if (step != null
					&& BEST_EFFORT_STEPS.contains(step.name())
					&& STEP_FAILED.equals(step.status())) {
				return true;
			}
		}
		return false;
	}

	private static boolean stepHasStatus(CoreJobStatusResponse core, String name, String status) {
		List<CoreJobStepResponse> steps = core.steps();
		if (steps == null) {
			return false;
		}
		for (CoreJobStepResponse step : steps) {
			if (step != null && name.equals(step.name()) && status.equals(step.status())) {
				return true;
			}
		}
		return false;
	}
}
