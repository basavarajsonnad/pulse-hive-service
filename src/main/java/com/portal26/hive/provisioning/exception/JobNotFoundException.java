package com.portal26.hive.provisioning.exception;

import lombok.Getter;

/**
 * No job with that id for the current MSP. Also covers another MSP's job, deliberately — Hive
 * does not leak that the row exists elsewhere.
 */
@Getter
public class JobNotFoundException extends RuntimeException {

	private final String jobId;

	public JobNotFoundException(String jobId) {
		super("No provisioning job found for id " + jobId);
		this.jobId = jobId;
	}
}
