package com.portal26.hive.provisioning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer tenant provisioned by an MSP, keyed by Core's provisioning job id.
 *
 * <p>Only {@code status} and {@code tenantName} ever change after insert, and only the poll worker
 * changes them.
 */
@Entity
@Table(name = "msp_tenants")
@Getter
@Setter
@NoArgsConstructor
public class MspTenant {

	public static final String STATUS_IN_PROGRESS = "in_progress";

	/** Core's job id. Assigned by Core, so rows cannot exist before Core accepts the create. */
	@Id
	@Column(name = "job_id", nullable = false, updatable = false, length = 64)
	private String jobId;

	@Column(name = "msp_id", nullable = false, updatable = false)
	private UUID mspId;

	@Column(name = "customer_name", nullable = false, updatable = false, length = 25)
	private String customerName;

	/** Core-generated and opaque. Null until Core's RESOLVE_TENANT_NAME step succeeds. */
	@Column(name = "tenant_name", length = 64)
	private String tenantName;

	/** Core's own job state, stored verbatim: in_progress, complete or failed. */
	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
