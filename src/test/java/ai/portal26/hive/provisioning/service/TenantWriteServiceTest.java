package ai.portal26.hive.provisioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.portal26.hive.customer.entity.TenantSigninConfig;
import ai.portal26.hive.customer.repository.CustomerRepository;
import ai.portal26.hive.customer.repository.TenantSigninConfigRepository;
import ai.portal26.hive.msp.MspRlsSession;
import ai.portal26.hive.provisioning.ProvisioningStatuses;
import ai.portal26.hive.provisioning.dto.CreateTenantResponse;
import ai.portal26.hive.provisioning.dto.SsoConfig;
import ai.portal26.hive.provisioning.entity.ProvisioningItem;
import ai.portal26.hive.provisioning.entity.ProvisioningJob;
import ai.portal26.hive.provisioning.repository.ProvisioningItemRepository;
import ai.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantWriteServiceTest {

	private static final UUID MSP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String CORE_JOB_ID = "job_1a2b3c4d";

	@Mock
	private MspRlsSession mspRlsSession;

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private ProvisioningJobRepository provisioningJobRepository;

	@Mock
	private ProvisioningItemRepository provisioningItemRepository;

	@Mock
	private TenantSigninConfigRepository tenantSigninConfigRepository;

	private TenantWriteService tenantWriteService;

	@BeforeEach
	void setUp() {
		tenantWriteService = new TenantWriteService(
				mspRlsSession,
				customerRepository,
				provisioningJobRepository,
				provisioningItemRepository,
				tenantSigninConfigRepository);
		when(provisioningJobRepository.save(any(ProvisioningJob.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(provisioningItemRepository.save(any(ProvisioningItem.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(tenantSigninConfigRepository.save(any(TenantSigninConfig.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void insertRunningPersistsSsoAfterCustomer() {
		SsoConfig sso = new SsoConfig(
				"https://acme.okta.com/app/xyz/sso/saml/metadata",
				"Acme-Okta",
				"email",
				"groups");

		CreateTenantResponse response =
				tenantWriteService.insertRunning(MSP_ID, CORE_JOB_ID, "acme-corp", sso);

		assertThat(response.jobId()).isEqualTo(CORE_JOB_ID);
		assertThat(response.status()).isEqualTo(ProvisioningStatuses.DB_RUNNING);
		ArgumentCaptor<TenantSigninConfig> captor = ArgumentCaptor.forClass(TenantSigninConfig.class);
		verify(tenantSigninConfigRepository).save(captor.capture());
		TenantSigninConfig saved = captor.getValue();
		assertThat(saved.getMspId()).isEqualTo(MSP_ID);
		assertThat(saved.getProtocol()).isEqualTo(TenantSigninConfig.PROTOCOL_SAML);
		assertThat(saved.getStatus()).isEqualTo(TenantSigninConfig.STATUS_ACTIVE);
		assertThat(saved.getProviderInput())
				.containsEntry("metadata_url", sso.metadataUrl())
				.containsEntry("provider_name", sso.providerName())
				.containsEntry("email_attribute", sso.emailAttribute())
				.containsEntry("groups_attribute", sso.groupsAttribute());
	}
}
