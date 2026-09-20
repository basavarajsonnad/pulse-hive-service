package com.portal26.hive.provisioning.repository;

import com.portal26.hive.provisioning.entity.MspTenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MspTenantRepository extends JpaRepository<MspTenant, String> {

	/** Scoped by msp_id so one MSP can never read another's job. */
	Optional<MspTenant> findByJobIdAndMspId(String jobId, UUID mspId);

	List<MspTenant> findByMspIdOrderByCreatedAtDesc(UUID mspId);

	List<MspTenant> findByStatus(String status);
}
