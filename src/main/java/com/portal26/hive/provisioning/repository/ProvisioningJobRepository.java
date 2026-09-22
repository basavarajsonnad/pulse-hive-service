package com.portal26.hive.provisioning.repository;

import com.portal26.hive.provisioning.entity.ProvisioningJob;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {

	List<ProvisioningJob> findByMspIdAndStatus(UUID mspId, String status);
}
