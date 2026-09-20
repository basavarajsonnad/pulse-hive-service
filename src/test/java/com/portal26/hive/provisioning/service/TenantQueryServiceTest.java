package com.portal26.hive.provisioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.msp.CurrentMspResolver;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.dto.CustomerListResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantQueryServiceTest {

	private static final UUID MSP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private CurrentMspResolver currentMspResolver;

	@Mock
	private MspRlsSession mspRlsSession;

	@Mock
	private CustomerRepository customerRepository;

	private TenantQueryService tenantQueryService;

	@BeforeEach
	void setUp() {
		tenantQueryService = new TenantQueryService(currentMspResolver, mspRlsSession, customerRepository);
	}

	@Test
	void listCustomersAppliesRlsThenReadsCustomerTable() {
		Customer acme = new Customer();
		acme.setName("acme-corp");
		acme.setTenantName("acme-corp.portal26.ai");
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(customerRepository.findByMspIdOrderByNameAsc(MSP_ID)).thenReturn(List.of(acme));

		CustomerListResponse response = tenantQueryService.listCustomers();

		verify(mspRlsSession).apply(MSP_ID);
		assertThat(response.customers()).hasSize(1);
		assertThat(response.customers().get(0).customerName()).isEqualTo("acme-corp");
		assertThat(response.customers().get(0).tenantName()).isEqualTo("acme-corp.portal26.ai");
	}
}
