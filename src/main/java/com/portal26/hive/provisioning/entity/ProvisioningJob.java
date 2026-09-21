package com.portal26.hive.provisioning.entity;

import com.portal26.hive.provisioning.ProvisioningStatuses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provisioning_job")
public class ProvisioningJob {

	@Id
	private String id;

	@Column(name = "msp_id", nullable = false)
	private UUID mspId;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String status;

	@Column(name = "total_count", nullable = false)
	private int totalCount;

	@Column(name = "success_count", nullable = false)
	private int successCount;

	@Column(name = "failure_count", nullable = false)
	private int failureCount;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public static ProvisioningJob singleRunning(UUID mspId, String coreJobId) {
		Instant now = Instant.now();
		ProvisioningJob job = new ProvisioningJob();
		job.id = coreJobId;
		job.mspId = mspId;
		job.type = ProvisioningStatuses.JOB_TYPE_SINGLE;
		job.status = ProvisioningStatuses.DB_RUNNING;
		job.totalCount = 1;
		job.successCount = 0;
		job.failureCount = 0;
		job.startedAt = now;
		job.createdAt = now;
		return job;
	}

	public String getId() {
		return id;
	}

	public String getStatus() {
		return status;
	}

	public int getSuccessCount() {
		return successCount;
	}

	public int getFailureCount() {
		return failureCount;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

	public void setSuccessCount(int successCount) {
		this.successCount = successCount;
	}

	public void setFailureCount(int failureCount) {
		this.failureCount = failureCount;
	}
}
