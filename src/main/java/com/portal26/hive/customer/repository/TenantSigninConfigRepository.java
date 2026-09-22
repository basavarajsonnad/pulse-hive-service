package com.portal26.hive.customer.repository;

import com.portal26.hive.customer.entity.TenantSigninConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantSigninConfigRepository extends JpaRepository<TenantSigninConfig, UUID> {

	Optional<TenantSigninConfig> findByCustomerId(UUID customerId);
}
