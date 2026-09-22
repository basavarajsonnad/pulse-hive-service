package com.portal26.hive.provisioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.entity.TenantSigninConfig;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.customer.repository.TenantSigninConfigRepository;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.NotFoundException;
import com.portal26.hive.msp.CurrentMspResolver;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.dto.CustomerListResponse;
import com.portal26.hive.provisioning.dto.RegistrationOutputResponse;
import com.portal26.hive.provisioning.dto.SsoConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

	@Mock
	private TenantSigninConfigRepository tenantSigninConfigRepository;

	private TenantQueryService tenantQueryService;

	@BeforeEach
	void setUp() {
		tenantQueryService = new TenantQueryService(
				currentMspResolver, mspRlsSession, customerRepository, tenantSigninConfigRepository);
	}

	@Test
	void listCustomersAppliesRlsThenReadsCustomerTableWithPagination() {
		Instant createdAt = Instant.parse("2026-09-21T05:00:00Z");
		Instant updatedAt = Instant.parse("2026-09-21T05:30:00Z");
		Customer acme = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
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
		assertThat(response.customers().get(0).customerId()).isEqualTo(acme.getId());
		assertThat(response.customers().get(0).mspId()).isEqualTo(MSP_ID);
		assertThat(response.customers().get(0).customerName()).isEqualTo("acme-corp");
		assertThat(response.customers().get(0).licensePackage()).isEqualTo("basic");
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

	@Test
	void getRegistrationOutputAppliesRlsThenReturnsSigninText() {
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		TenantSigninConfig signin = TenantSigninConfig.forSamlCreate(MSP_ID, customer.getId(), sso());
		signin.setRegistrationOutput("MANUAL STEP — add these to the Entra app");
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(customerRepository.findFirstByNameOrderByUpdatedAtDesc("acme-corp")).thenReturn(Optional.of(customer));
		when(tenantSigninConfigRepository.findByCustomerId(customer.getId())).thenReturn(Optional.of(signin));

		RegistrationOutputResponse response = tenantQueryService.getRegistrationOutput("acme-corp");

		verify(mspRlsSession).apply(MSP_ID);
		verify(customerRepository).findFirstByNameOrderByUpdatedAtDesc("acme-corp");
		verify(tenantSigninConfigRepository).findByCustomerId(customer.getId());
		assertThat(response.registrationOutput()).isEqualTo("MANUAL STEP — add these to the Entra app");
	}

	@Test
	void getRegistrationOutputReturnsNullWhenNotYetPolled() {
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		TenantSigninConfig signin = TenantSigninConfig.forSamlCreate(MSP_ID, customer.getId(), sso());
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(customerRepository.findFirstByNameOrderByUpdatedAtDesc("acme-corp")).thenReturn(Optional.of(customer));
		when(tenantSigninConfigRepository.findByCustomerId(customer.getId())).thenReturn(Optional.of(signin));

		RegistrationOutputResponse response = tenantQueryService.getRegistrationOutput("acme-corp");

		assertThat(response.registrationOutput()).isNull();
	}

	@Test
	void getRegistrationOutputReturnsNullWhenSigninRowMissing() {
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(customerRepository.findFirstByNameOrderByUpdatedAtDesc("acme-corp")).thenReturn(Optional.of(customer));
		when(tenantSigninConfigRepository.findByCustomerId(customer.getId())).thenReturn(Optional.empty());

		RegistrationOutputResponse response = tenantQueryService.getRegistrationOutput("acme-corp");

		assertThat(response.registrationOutput()).isNull();
	}

	@Test
	void getRegistrationOutputTrimsCustomerName() {
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(customerRepository.findFirstByNameOrderByUpdatedAtDesc("acme-corp")).thenReturn(Optional.of(customer));
		when(tenantSigninConfigRepository.findByCustomerId(customer.getId())).thenReturn(Optional.empty());

		tenantQueryService.getRegistrationOutput("  acme-corp  ");

		verify(customerRepository).findFirstByNameOrderByUpdatedAtDesc("acme-corp");
	}

	@Test
	void getRegistrationOutputRejectsBlankCustomerName() {
		assertThatThrownBy(() -> tenantQueryService.getRegistrationOutput("  "))
				.isInstanceOf(CoreApiException.class)
				.hasMessage("customerName is required");
		verifyNoInteractions(customerRepository, tenantSigninConfigRepository, mspRlsSession);
	}

	@Test
	void getRegistrationOutputThrowsWhenCustomerUnknown() {
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(customerRepository.findFirstByNameOrderByUpdatedAtDesc("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> tenantQueryService.getRegistrationOutput("missing"))
				.isInstanceOf(NotFoundException.class)
				.hasMessage("customer not found");
		verifyNoInteractions(tenantSigninConfigRepository);
	}

	private static SsoConfig sso() {
		return new SsoConfig(
				"https://acme.okta.com/app/xyz/sso/saml/metadata",
				"Acme-Okta",
				"email",
				"groups");
	}
}
