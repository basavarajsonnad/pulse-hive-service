package com.portal26.hive.provisioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.exception.DuplicateCustomerException;
import com.portal26.hive.msp.CurrentMspResolver;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.dto.CreateTenantRequest;
import com.portal26.hive.provisioning.dto.CreateTenantResponse;
import com.portal26.hive.provisioning.dto.SsoConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

	private static final UUID MSP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String CORE_JOB_ID = "job_1a2b3c4d";

	@Mock
	private CurrentMspResolver currentMspResolver;

	@Mock
	private TenantWriteService tenantWriteService;

	@Mock
	private CoreTenantClient coreTenantClient;

	private TenantProvisioningService tenantProvisioningService;

	@BeforeEach
	void setUp() {
		tenantProvisioningService =
				new TenantProvisioningService(currentMspResolver, tenantWriteService, coreTenantClient);
	}

	@Test
	void createCallsCoreThenInsertsHiveRows() {
		CreateTenantRequest request = request();
		CreateTenantResponse expected =
				new CreateTenantResponse(CORE_JOB_ID, "acme-corp", ProvisioningStatuses.DB_RUNNING);
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(coreTenantClient.startProvisioning(request)).thenReturn(CORE_JOB_ID);
		when(tenantWriteService.insertRunning(MSP_ID, CORE_JOB_ID, "acme-corp"))
				.thenReturn(expected);

		CreateTenantResponse response = tenantProvisioningService.create(request);

		verify(tenantWriteService).assertNameAvailable(MSP_ID, "acme-corp");
		verify(coreTenantClient).startProvisioning(request);
		verify(tenantWriteService).insertRunning(MSP_ID, CORE_JOB_ID, "acme-corp");
		assertThat(response).isEqualTo(expected);
	}

	@Test
	void createDoesNotCallCoreWhenCustomerExists() {
		CreateTenantRequest request = request();
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		org.mockito.Mockito.doThrow(new DuplicateCustomerException("acme-corp"))
				.when(tenantWriteService)
				.assertNameAvailable(MSP_ID, "acme-corp");

		assertThatThrownBy(() -> tenantProvisioningService.create(request))
				.isInstanceOf(DuplicateCustomerException.class);
		verify(coreTenantClient, never()).startProvisioning(request);
		verify(tenantWriteService, never()).insertRunning(MSP_ID, CORE_JOB_ID, "acme-corp");
	}

	private static CreateTenantRequest request() {
		return new CreateTenantRequest(
				"acme-corp",
				new SsoConfig(
						"https://acme.okta.com/app/xyz/sso/saml/metadata",
						"Acme-Okta",
						"email",
						"groups"));
	}
}
