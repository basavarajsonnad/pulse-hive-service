package com.portal26.hive.provisioning.service;

import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.entity.TenantSigninConfig;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.customer.repository.TenantSigninConfigRepository;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.dto.CreateTenantResponse;
import com.portal26.hive.provisioning.dto.SsoConfig;
import com.portal26.hive.provisioning.entity.ProvisioningItem;
import com.portal26.hive.provisioning.entity.ProvisioningJob;
import com.portal26.hive.provisioning.repository.ProvisioningItemRepository;
import com.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantWriteService {

	private final MspRlsSession mspRlsSession;
	private final CustomerRepository customerRepository;
	private final ProvisioningJobRepository provisioningJobRepository;
	private final ProvisioningItemRepository provisioningItemRepository;
	private final TenantSigninConfigRepository tenantSigninConfigRepository;

	@Autowired
	public TenantWriteService(
			MspRlsSession mspRlsSession,
			CustomerRepository customerRepository,
			ProvisioningJobRepository provisioningJobRepository,
			ProvisioningItemRepository provisioningItemRepository,
			TenantSigninConfigRepository tenantSigninConfigRepository) {
		this.mspRlsSession = mspRlsSession;
		this.customerRepository = customerRepository;
		this.provisioningJobRepository = provisioningJobRepository;
		this.provisioningItemRepository = provisioningItemRepository;
		this.tenantSigninConfigRepository = tenantSigninConfigRepository;
	}

	@Transactional
	public CreateTenantResponse insertRunning(
			UUID mspId, String coreJobId, String customerName, String licensePackage, SsoConfig sso) {
		mspRlsSession.apply(mspId);
		ProvisioningJob job = provisioningJobRepository.save(ProvisioningJob.singleRunning(mspId, coreJobId));
		Customer customer = customerRepository.save(Customer.forCreate(mspId, customerName, licensePackage));
		provisioningItemRepository.save(
				ProvisioningItem.firstRow(mspId, job.getId(), customer.getId(), customerName));
		tenantSigninConfigRepository.save(TenantSigninConfig.forSamlCreate(mspId, customer.getId(), sso));
		return new CreateTenantResponse(
				job.getCoreJobReference(), customerName, licensePackage, ProvisioningStatuses.DB_RUNNING);
	}
}
