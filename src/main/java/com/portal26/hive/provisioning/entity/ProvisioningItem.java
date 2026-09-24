package com.portal26.hive.provisioning.entity;

import com.portal26.hive.provisioning.ProvisioningStatuses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provisioning_item")
public class ProvisioningItem {

	@Id
	private UUID id;

	@Column(name = "msp_id", nullable = false)
	private UUID mspId;

	@Column(name = "job_id", nullable = false)
	private UUID jobId;

	@Column(name = "row_number")
	private Integer rowNumber;

	@Column(name = "customer_name", nullable = false)
	private String customerName;

	@Column(nullable = false)
	private String status;

	@Column(name = "customer_id")
	private UUID customerId;

	@Column(name = "error")
	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public static ProvisioningItem firstRow(UUID mspId, UUID jobId, UUID customerId, String customerName) {
		Instant now = Instant.now();
		ProvisioningItem item = new ProvisioningItem();
		item.id = UUID.randomUUID();
		item.mspId = mspId;
		item.jobId = jobId;
		item.rowNumber = 1;
		item.customerName = customerName;
		item.status = ProvisioningStatuses.DB_RUNNING;
		item.customerId = customerId;
		item.createdAt = now;
		item.updatedAt = now;
		return item;
	}

	public UUID getJobId() {
		return jobId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
