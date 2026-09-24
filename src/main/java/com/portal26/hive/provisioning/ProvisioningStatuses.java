package com.portal26.hive.provisioning;

import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.core.client.dto.CoreJobStepResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ProvisioningStatuses {

	public static final String DB_RUNNING = "running";
	public static final String DB_COMPLETED = "completed";
	public static final String DB_COMPLETED_WITH_ERRORS = "completed_with_errors";
	public static final String DB_FAILED = "failed";
	public static final String CUSTOMER_IN_PROGRESS = "in_progress";
	public static final String JOB_TYPE_SINGLE = "single";

	public static final String CORE_IN_PROGRESS = "in_progress";
	public static final String CORE_COMPLETE = "complete";
	public static final String CORE_FAILED = "failed";

	public static final String STEP_SUCCEEDED = "succeeded";
	public static final String STEP_FAILED = "failed";

	public static final Set<String> CRITICAL_STEPS = Set.of(
			"CREATE_TENANT", "AWAIT_PROVISIONING", "RESOLVE_TENANT_NAME");

	public static final String STEP_SAML_REGISTRATION = "SAML_REGISTRATION";

	public static final Set<String> BEST_EFFORT_STEPS = Set.of(
			"TURBO_AND_MDM",
			"LD_SEGMENTS",
			"LD_UI_FLAGS",
			"LD_BACKEND_FLAGS",
			STEP_SAML_REGISTRATION);

	private ProvisioningStatuses() {
	}

	public static Optional<String> resolveHiveStatus(CoreJobStatusResponse core) {
		if (core == null || core.status() == null || core.status().isBlank()) {
			return Optional.empty();
		}
		if (anyCriticalFailed(core)) {
			return Optional.of(DB_FAILED);
		}
		if (CORE_IN_PROGRESS.equals(core.status())) {
			return Optional.of(DB_RUNNING);
		}
		if (CORE_COMPLETE.equals(core.status()) && !allEightStepsFinished(core)) {
			return Optional.of(DB_RUNNING);
		}
		if (criticalSucceeded(core) && anyBestEffortFailed(core)) {
			return Optional.of(DB_COMPLETED_WITH_ERRORS);
		}
		if (CORE_COMPLETE.equals(core.status())) {
			return Optional.of(DB_COMPLETED);
		}
		if (CORE_FAILED.equals(core.status())) {
			return Optional.of(DB_FAILED);
		}
		return Optional.empty();
	}

	public static boolean isTerminal(String hiveStatus) {
		return DB_COMPLETED.equals(hiveStatus)
				|| DB_COMPLETED_WITH_ERRORS.equals(hiveStatus)
				|| DB_FAILED.equals(hiveStatus);
	}

	public static String toCustomerStatus(String hiveStatus) {
		if (DB_FAILED.equals(hiveStatus)) {
			return DB_FAILED;
		}
		if (DB_RUNNING.equals(hiveStatus)) {
			return CUSTOMER_IN_PROGRESS;
		}
		return DB_COMPLETED;
	}

	public static Optional<CoreJobStepResponse> succeededSamlRegistration(CoreJobStatusResponse core) {
		if (core == null || core.steps() == null) {
			return Optional.empty();
		}
		for (CoreJobStepResponse step : core.steps()) {
			if (step != null
					&& STEP_SAML_REGISTRATION.equals(step.name())
					&& STEP_SUCCEEDED.equals(step.status())
					&& step.detail() != null
					&& !step.detail().isBlank()) {
				return Optional.of(step);
			}
		}
		return Optional.empty();
	}

	public static String midRunItemStatus(CoreJobStatusResponse core) {
		if (anyCriticalFailed(core)) {
			return DB_FAILED;
		}
		if (criticalSucceeded(core) && anyBestEffortFailed(core)) {
			return DB_COMPLETED_WITH_ERRORS;
		}
		if (criticalSucceeded(core)) {
			return DB_COMPLETED;
		}
		return DB_RUNNING;
	}

	public static String midRunItemError(CoreJobStatusResponse core) {
		String itemStatus = midRunItemStatus(core);
		if (DB_FAILED.equals(itemStatus) || DB_COMPLETED_WITH_ERRORS.equals(itemStatus)) {
			return failedStepDetails(core);
		}
		if (DB_RUNNING.equals(itemStatus)) {
			return currentStepProgress(core);
		}
		return null;
	}

	public static Optional<String> resolvedTenantName(CoreJobStatusResponse core) {
		if (core == null || !criticalSucceeded(core)) {
			return Optional.empty();
		}
		String tenantName = core.tenantName();
		if (tenantName == null || tenantName.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(tenantName);
	}

	public static String currentStepProgress(CoreJobStatusResponse core) {
		if (core == null || core.steps() == null) {
			return null;
		}
		for (CoreJobStepResponse step : core.steps()) {
			if (step != null && "running".equals(step.status()) && step.name() != null) {
				return step.name() + " running";
			}
		}
		for (CoreJobStepResponse step : core.steps()) {
			if (step != null && "pending".equals(step.status()) && step.name() != null) {
				return step.name() + " pending";
			}
		}
		return null;
	}

	public static String firstFailedStepDetail(CoreJobStatusResponse core) {
		String all = failedStepDetails(core);
		if (all == null) {
			return null;
		}
		int sep = all.indexOf("; ");
		return sep < 0 ? all : all.substring(0, sep);
	}

	public static String failedStepDetails(CoreJobStatusResponse core) {
		if (core == null || core.steps() == null) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		for (CoreJobStepResponse step : core.steps()) {
			if (step == null || !STEP_FAILED.equals(step.status())) {
				continue;
			}
			if (step.detail() != null && !step.detail().isBlank()) {
				parts.add(step.detail());
			} else if (step.name() != null && !step.name().isBlank()) {
				parts.add(step.name());
			}
		}
		if (parts.isEmpty()) {
			return null;
		}
		return String.join("; ", parts);
	}

	private static boolean anyCriticalFailed(CoreJobStatusResponse core) {
		List<CoreJobStepResponse> steps = core.steps();
		if (steps == null) {
			return false;
		}
		for (CoreJobStepResponse step : steps) {
			if (step != null
					&& CRITICAL_STEPS.contains(step.name())
					&& STEP_FAILED.equals(step.status())) {
				return true;
			}
		}
		return false;
	}

	private static boolean allEightStepsFinished(CoreJobStatusResponse core) {
		for (String name : CRITICAL_STEPS) {
			if (!stepFinished(core, name)) {
				return false;
			}
		}
		for (String name : BEST_EFFORT_STEPS) {
			if (!stepFinished(core, name)) {
				return false;
			}
		}
		return true;
	}

	private static boolean stepFinished(CoreJobStatusResponse core, String name) {
		return stepHasStatus(core, name, STEP_SUCCEEDED) || stepHasStatus(core, name, STEP_FAILED);
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
