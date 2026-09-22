package com.portal26.hive.provisioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.core.client.dto.CoreJobStepResponse;
import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.entity.TenantSigninConfig;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.customer.repository.TenantSigninConfigRepository;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.dto.SsoConfig;
import com.portal26.hive.provisioning.entity.ProvisioningItem;
import com.portal26.hive.provisioning.entity.ProvisioningJob;
import com.portal26.hive.provisioning.repository.ProvisioningItemRepository;
import com.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisioningPollWriteServiceTest {

	private static final UUID MSP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String CORE_JOB_ID = "job_1a2b3c4d";
	private static final String SAML_DETAIL =
			"MANUAL STEP — add these to the Entra app registration or SSO login will fail: "
					+ "redirect URI https://acme-corp-pulse.auth.ap-south-1.amazoncognito.com/saml2/idpresponse";

	@Mock
	private MspRlsSession mspRlsSession;

	@Mock
	private ProvisioningJobRepository provisioningJobRepository;

	@Mock
	private ProvisioningItemRepository provisioningItemRepository;

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private TenantSigninConfigRepository tenantSigninConfigRepository;

	private ProvisioningPollWriteService writeService;

	@BeforeEach
	void setUp() {
		writeService = new ProvisioningPollWriteService(
				mspRlsSession,
				provisioningJobRepository,
				provisioningItemRepository,
				customerRepository,
				tenantSigninConfigRepository);
	}

	@Test
	void completeUpdatesJobItemAndCustomer() {
		ProvisioningJob job = ProvisioningJob.singleRunning(MSP_ID, CORE_JOB_ID);
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningItem item = ProvisioningItem.firstRow(MSP_ID, job.getId(), customer.getId(), "acme-corp");
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(provisioningItemRepository.findByJobId(job.getId())).thenReturn(List.of(item));
		when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

		writeService.applyCoreStatus(MSP_ID, job.getId(), completeJob());

		verify(mspRlsSession).apply(MSP_ID);
		verify(provisioningJobRepository).save(job);
		verify(provisioningItemRepository).save(item);
		verify(customerRepository).save(customer);
		assertThat(job.getStatus()).isEqualTo(ProvisioningStatuses.DB_COMPLETED);
		assertThat(job.getSuccessCount()).isEqualTo(1);
		assertThat(job.getFailureCount()).isEqualTo(0);
		assertThat(job.getFinishedAt()).isNotNull();
		assertThat(item.getStatus()).isEqualTo(ProvisioningStatuses.DB_COMPLETED);
		assertThat(item.getError()).isNull();
		assertThat(customer.getStatus()).isEqualTo(ProvisioningStatuses.DB_COMPLETED);
		assertThat(customer.getTenantName()).isEqualTo("acme.portal26.ai");
	}

	@Test
	void completedWithErrorsCopiesFailedStepDetail() {
		ProvisioningJob job = ProvisioningJob.singleRunning(MSP_ID, CORE_JOB_ID);
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningItem item = ProvisioningItem.firstRow(MSP_ID, job.getId(), customer.getId(), "acme-corp");
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(provisioningItemRepository.findByJobId(job.getId())).thenReturn(List.of(item));
		when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

		writeService.applyCoreStatus(MSP_ID, job.getId(), completedWithErrorsJob());

		assertThat(job.getStatus()).isEqualTo(ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS);
		assertThat(job.getSuccessCount()).isEqualTo(1);
		assertThat(item.getError()).isEqualTo("SSO metadata rejected");
		assertThat(customer.getStatus()).isEqualTo(ProvisioningStatuses.DB_COMPLETED);
		assertThat(customer.getTenantName()).isEqualTo("acme.portal26.ai");
		verify(tenantSigninConfigRepository, never()).findByCustomerId(customer.getId());
	}

	@Test
	void completePersistsSucceededSamlDetailAndRegisteredAt() {
		ProvisioningJob job = ProvisioningJob.singleRunning(MSP_ID, CORE_JOB_ID);
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningItem item = ProvisioningItem.firstRow(MSP_ID, job.getId(), customer.getId(), "acme-corp");
		TenantSigninConfig signin = TenantSigninConfig.forSamlCreate(MSP_ID, customer.getId(), sso());
		Instant endedAt = Instant.parse("2026-01-15T09:54:50Z");
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(provisioningItemRepository.findByJobId(job.getId())).thenReturn(List.of(item));
		when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
		when(tenantSigninConfigRepository.findByCustomerId(customer.getId())).thenReturn(Optional.of(signin));

		writeService.applyCoreStatus(MSP_ID, job.getId(), completeJobWithSamlDetail(endedAt));

		verify(tenantSigninConfigRepository).save(signin);
		assertThat(signin.getRegistrationOutput()).isEqualTo(SAML_DETAIL);
		assertThat(signin.getRegisteredAt()).isEqualTo(endedAt);
		assertThat(customer.getStatus()).isEqualTo(ProvisioningStatuses.DB_COMPLETED);
	}

	@Test
	void samlFailedDoesNotWriteRegistrationOutput() {
		ProvisioningJob job = ProvisioningJob.singleRunning(MSP_ID, CORE_JOB_ID);
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningItem item = ProvisioningItem.firstRow(MSP_ID, job.getId(), customer.getId(), "acme-corp");
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(provisioningItemRepository.findByJobId(job.getId())).thenReturn(List.of(item));
		when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

		writeService.applyCoreStatus(MSP_ID, job.getId(), completedWithErrorsJob());

		verify(tenantSigninConfigRepository, never()).findByCustomerId(customer.getId());
		verify(tenantSigninConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void doesNotMoveRegisteredAtWhenAlreadySet() {
		ProvisioningJob job = ProvisioningJob.singleRunning(MSP_ID, CORE_JOB_ID);
		Customer customer = Customer.forCreate(MSP_ID, "acme-corp", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningItem item = ProvisioningItem.firstRow(MSP_ID, job.getId(), customer.getId(), "acme-corp");
		TenantSigninConfig signin = TenantSigninConfig.forSamlCreate(MSP_ID, customer.getId(), sso());
		Instant existing = Instant.parse("2026-01-15T09:00:00Z");
		signin.setRegisteredAt(existing);
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(provisioningItemRepository.findByJobId(job.getId())).thenReturn(List.of(item));
		when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
		when(tenantSigninConfigRepository.findByCustomerId(customer.getId())).thenReturn(Optional.of(signin));

		writeService.applyCoreStatus(
				MSP_ID, job.getId(), completeJobWithSamlDetail(Instant.parse("2026-01-15T09:54:50Z")));

		assertThat(signin.getRegistrationOutput()).isEqualTo(SAML_DETAIL);
		assertThat(signin.getRegisteredAt()).isEqualTo(existing);
	}

	@Test
	void skipsWhenJobIsNotRunning() {
		ProvisioningJob job = ProvisioningJob.singleRunning(MSP_ID, CORE_JOB_ID);
		job.setStatus(ProvisioningStatuses.DB_COMPLETED);
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

		writeService.applyCoreStatus(MSP_ID, job.getId(), completeJob());

		verify(provisioningJobRepository, never()).save(job);
		verify(provisioningItemRepository, never()).findByJobId(job.getId());
	}

	@Test
	void skipsUnknownCoreStatus() {
		UUID hiveJobId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		writeService.applyCoreStatus(MSP_ID, hiveJobId, unknownJob());

		verify(mspRlsSession).apply(MSP_ID);
		verify(provisioningJobRepository, never()).findById(hiveJobId);
	}

	private static CoreJobStatusResponse completeJob() {
		return new CoreJobStatusResponse(
				CORE_JOB_ID,
				"acme-corp",
				"acme.portal26.ai",
				ProvisioningStatuses.CORE_COMPLETE,
				List.of(
						step("CREATE_TENANT", ProvisioningStatuses.STEP_SUCCEEDED),
						step("AWAIT_PROVISIONING", ProvisioningStatuses.STEP_SUCCEEDED),
						step("RESOLVE_TENANT_NAME", ProvisioningStatuses.STEP_SUCCEEDED),
						step("TURBO_AND_MDM", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_SEGMENTS", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED),
						step("SAML_REGISTRATION", ProvisioningStatuses.STEP_SUCCEEDED)));
	}

	private static CoreJobStatusResponse completedWithErrorsJob() {
		return new CoreJobStatusResponse(
				CORE_JOB_ID,
				"acme-corp",
				"acme.portal26.ai",
				ProvisioningStatuses.CORE_FAILED,
				List.of(
						step("CREATE_TENANT", ProvisioningStatuses.STEP_SUCCEEDED),
						step("AWAIT_PROVISIONING", ProvisioningStatuses.STEP_SUCCEEDED),
						step("RESOLVE_TENANT_NAME", ProvisioningStatuses.STEP_SUCCEEDED),
						step("TURBO_AND_MDM", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_SEGMENTS", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED),
						new CoreJobStepResponse(
								"SAML_REGISTRATION",
								ProvisioningStatuses.STEP_FAILED,
								"SSO metadata rejected")));
	}

	private static CoreJobStatusResponse completeJobWithSamlDetail(Instant endedAt) {
		return new CoreJobStatusResponse(
				CORE_JOB_ID,
				"acme-corp",
				"acme.portal26.ai",
				ProvisioningStatuses.CORE_COMPLETE,
				List.of(
						step("CREATE_TENANT", ProvisioningStatuses.STEP_SUCCEEDED),
						step("AWAIT_PROVISIONING", ProvisioningStatuses.STEP_SUCCEEDED),
						step("RESOLVE_TENANT_NAME", ProvisioningStatuses.STEP_SUCCEEDED),
						step("TURBO_AND_MDM", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_SEGMENTS", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_UI_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED),
						step("LD_BACKEND_FLAGS", ProvisioningStatuses.STEP_SUCCEEDED),
						new CoreJobStepResponse(
								ProvisioningStatuses.STEP_SAML_REGISTRATION,
								ProvisioningStatuses.STEP_SUCCEEDED,
								SAML_DETAIL,
								endedAt)));
	}

	private static CoreJobStatusResponse unknownJob() {
		return new CoreJobStatusResponse(CORE_JOB_ID, "acme-corp", null, "queued", List.of());
	}

	private static CoreJobStepResponse step(String name, String status) {
		return new CoreJobStepResponse(name, status, null);
	}

	private static SsoConfig sso() {
		return new SsoConfig(
				"https://acme.okta.com/app/xyz/sso/saml/metadata",
				"Acme-Okta",
				"email",
				"groups");
	}
}
