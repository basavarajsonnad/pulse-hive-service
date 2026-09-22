package ai.portal26.hive.customer.repository;

import ai.portal26.hive.customer.entity.Customer;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	Page<Customer> findAllByOrderByNameAsc(Pageable pageable);

	boolean existsByMspIdAndName(UUID mspId, String name);

	boolean existsByMspIdAndTenantName(UUID mspId, String tenantName);
}
