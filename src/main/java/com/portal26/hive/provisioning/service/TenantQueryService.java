package com.portal26.hive.provisioning.service;

import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.msp.CurrentMspResolver;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.dto.CustomerListItem;
import com.portal26.hive.provisioning.dto.CustomerListResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantQueryService {

	private final CurrentMspResolver currentMspResolver;
	private final MspRlsSession mspRlsSession;
	private final CustomerRepository customerRepository;

	@Autowired
	public TenantQueryService(
			CurrentMspResolver currentMspResolver,
			MspRlsSession mspRlsSession,
			CustomerRepository customerRepository) {
		this.currentMspResolver = currentMspResolver;
		this.mspRlsSession = mspRlsSession;
		this.customerRepository = customerRepository;
	}

	@Transactional(readOnly = true)
	public CustomerListResponse listCustomers() {
		UUID mspId = currentMspResolver.currentMspId();
		mspRlsSession.apply(mspId);
		List<CustomerListItem> customers = customerRepository.findByMspIdOrderByNameAsc(mspId).stream()
				.map(this::toItem)
				.toList();
		return new CustomerListResponse(customers);
	}

	private CustomerListItem toItem(Customer customer) {
		return new CustomerListItem(customer.getName(), customer.getTenantName());
	}
}
