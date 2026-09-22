package com.portal26.hive.provisioning.repository;

import com.portal26.hive.provisioning.entity.ProvisioningItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvisioningItemRepository extends JpaRepository<ProvisioningItem, UUID> {

	List<ProvisioningItem> findByJobId(UUID jobId);
}
