package com.portal26.hive.core.exception;

/**
 * The only error codes these APIs emit. Hive mirrors Core's vocabulary rather than inventing its
 * own, so a code returned to the UI means the same thing it does in Core.
 *
 * <p>Lives in {@code core} because both the Core client and the provisioning layer need it, and
 * provisioning already depends on core -- the reverse would be a cycle.
 */
public final class ErrorCodes {

	public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
	public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";

	private ErrorCodes() {
	}
}
