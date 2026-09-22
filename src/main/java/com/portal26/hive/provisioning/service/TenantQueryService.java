package com.portal26.hive.provisioning.service;

import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.ErrorCodes;
import com.portal26.hive.msp.CurrentMspResolver;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.dto.CustomerListItem;
import com.portal26.hive.provisioning.dto.CustomerListResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantQueryService {

	public static final int DEFAULT_PAGE = 0;
	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

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
	public CustomerListResponse listCustomers(int page, int size) {
		validatePagination(page, size);
		UUID mspId = currentMspResolver.currentMspId();
		mspRlsSession.apply(mspId);
		Page<Customer> result = customerRepository.findAllByOrderByNameAsc(PageRequest.of(page, size));
		return new CustomerListResponse(
				result.getContent().stream().map(this::toItem).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	private static void validatePagination(int page, int size) {
		if (page < 0) {
			throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, "page must be >= 0");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, "size must be between 1 and " + MAX_SIZE);
		}
	}

	private CustomerListItem toItem(Customer customer) {
		return new CustomerListItem(
				customer.getName(),
				customer.getTenantName(),
				customer.getStatus(),
				customer.getCreatedAt(),
				customer.getUpdatedAt());
	}
}
