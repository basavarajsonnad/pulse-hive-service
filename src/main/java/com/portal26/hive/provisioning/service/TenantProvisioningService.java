package com.portal26.hive.provisioning.service;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.msp.CurrentMspResolver;
import com.portal26.hive.provisioning.dto.CreateTenantRequest;
import com.portal26.hive.provisioning.dto.CreateTenantResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TenantProvisioningService {

	private final CurrentMspResolver currentMspResolver;
	private final TenantWriteService tenantWriteService;
	private final CoreTenantClient coreTenantClient;

	@Autowired
	public TenantProvisioningService(
			CurrentMspResolver currentMspResolver,
			TenantWriteService tenantWriteService,
			CoreTenantClient coreTenantClient) {
		this.currentMspResolver = currentMspResolver;
		this.tenantWriteService = tenantWriteService;
		this.coreTenantClient = coreTenantClient;
	}

	public CreateTenantResponse create(CreateTenantRequest request) {
		UUID mspId = currentMspResolver.currentMspId();
		tenantWriteService.assertNameAvailable(mspId, request.customerName());
		String coreJobId = coreTenantClient.startProvisioning(request);
		return tenantWriteService.insertRunning(
				mspId, coreJobId, request.customerName(), request.licensePackage(), request.sso());
	}
}
