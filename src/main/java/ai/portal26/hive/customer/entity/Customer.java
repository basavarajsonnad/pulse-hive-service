package ai.portal26.hive.customer.entity;

import ai.portal26.hive.provisioning.ProvisioningStatuses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

	@Id
	private UUID id;

	@Column(name = "msp_id", nullable = false)
	private UUID mspId;

	@Column(nullable = false)
	private String name;

	@Column(name = "tenant_name")
	private String tenantName;

	@Column(nullable = false)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public UUID getMspId() {
		return mspId;
	}

	public String getName() {
		return name;
	}

	public String getTenantName() {
		return tenantName;
	}

	public String getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public static Customer forCreate(UUID mspId, String name) {
		Instant now = Instant.now();
		Customer customer = new Customer();
		customer.id = UUID.randomUUID();
		customer.mspId = mspId;
		customer.name = name;
		customer.status = ProvisioningStatuses.DB_RUNNING;
		customer.createdAt = now;
		customer.updatedAt = now;
		return customer;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
