package com.portal26.hive.customer.repository;

import com.portal26.hive.customer.entity.Customer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	List<Customer> findByMspIdOrderByNameAsc(UUID mspId);
}
