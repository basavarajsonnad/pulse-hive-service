package com.portal26.hive.provisioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.core.client.CoreTenantClient;
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
				new CreateTenantResponse(CORE_JOB_ID, "acme-corp", "basic", ProvisioningStatuses.DB_RUNNING);
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(coreTenantClient.startProvisioning(request)).thenReturn(CORE_JOB_ID);
		when(tenantWriteService.insertRunning(MSP_ID, CORE_JOB_ID, "acme-corp", "basic", request.sso()))
				.thenReturn(expected);

		CreateTenantResponse response = tenantProvisioningService.create(request);

		verify(coreTenantClient).startProvisioning(request);
		verify(tenantWriteService).insertRunning(MSP_ID, CORE_JOB_ID, "acme-corp", "basic", request.sso());
		assertThat(response).isEqualTo(expected);
	}

	private static CreateTenantRequest request() {
		return new CreateTenantRequest(
				"acme-corp",
				"basic",
				new SsoConfig(
						"https://acme.okta.com/app/xyz/sso/saml/metadata",
						"Acme-Okta",
						"email",
						"groups"));
	}
}
