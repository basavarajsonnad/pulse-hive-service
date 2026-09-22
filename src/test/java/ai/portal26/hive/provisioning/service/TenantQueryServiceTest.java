package ai.portal26.hive.provisioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ai.portal26.hive.customer.entity.Customer;
import ai.portal26.hive.customer.repository.CustomerRepository;
import ai.portal26.hive.exception.CoreApiException;
import ai.portal26.hive.msp.CurrentMspResolver;
import ai.portal26.hive.msp.MspRlsSession;
import ai.portal26.hive.provisioning.dto.CustomerListResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
	void listCustomersAppliesRlsThenReadsCustomerTableWithPagination() {
		Instant createdAt = Instant.parse("2026-09-21T05:00:00Z");
		Instant updatedAt = Instant.parse("2026-09-21T05:30:00Z");
		Customer acme = new Customer();
		acme.setName("acme-corp");
		acme.setTenantName("acme-corp.portal26.ai");
		acme.setStatus("completed");
		acme.setCreatedAt(createdAt);
		acme.setUpdatedAt(updatedAt);
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(customerRepository.findAllByOrderByNameAsc(PageRequest.of(0, 20)))
				.thenReturn(new PageImpl<>(List.of(acme), PageRequest.of(0, 20), 1));

		CustomerListResponse response = tenantQueryService.listCustomers(0, 20);

		verify(mspRlsSession).apply(MSP_ID);
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(customerRepository).findAllByOrderByNameAsc(pageableCaptor.capture());
		verifyNoMoreInteractions(customerRepository);
		assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
		assertThat(response.customers()).hasSize(1);
		assertThat(response.customers().get(0).customerName()).isEqualTo("acme-corp");
		assertThat(response.page()).isZero();
		assertThat(response.size()).isEqualTo(20);
		assertThat(response.totalElements()).isEqualTo(1);
		assertThat(response.totalPages()).isEqualTo(1);
	}

	@Test
	void listCustomersRejectsNegativePage() {
		assertThatThrownBy(() -> tenantQueryService.listCustomers(-1, 20))
				.isInstanceOf(CoreApiException.class)
				.hasMessage("page must be >= 0");
	}

	@Test
	void listCustomersRejectsSizeBelowOne() {
		assertThatThrownBy(() -> tenantQueryService.listCustomers(0, 0))
				.isInstanceOf(CoreApiException.class)
				.hasMessage("size must be between 1 and 100");
	}

	@Test
	void listCustomersRejectsSizeAboveMax() {
		assertThatThrownBy(() -> tenantQueryService.listCustomers(0, 101))
				.isInstanceOf(CoreApiException.class)
				.hasMessage("size must be between 1 and 100");
	}
}
