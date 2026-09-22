package ai.portal26.hive.provisioning.service;

import ai.portal26.hive.customer.entity.Customer;
import ai.portal26.hive.customer.entity.TenantSigninConfig;
import ai.portal26.hive.customer.repository.CustomerRepository;
import ai.portal26.hive.customer.repository.TenantSigninConfigRepository;
import ai.portal26.hive.exception.DuplicateCustomerException;
import ai.portal26.hive.msp.MspRlsSession;
import ai.portal26.hive.provisioning.ProvisioningStatuses;
import ai.portal26.hive.provisioning.dto.CreateTenantResponse;
import ai.portal26.hive.provisioning.dto.SsoConfig;
import ai.portal26.hive.provisioning.entity.ProvisioningItem;
import ai.portal26.hive.provisioning.entity.ProvisioningJob;
import ai.portal26.hive.provisioning.repository.ProvisioningItemRepository;
import ai.portal26.hive.provisioning.repository.ProvisioningJobRepository;
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

	@Transactional(readOnly = true)
	public void assertNameAvailable(UUID mspId, String customerName) {
		mspRlsSession.apply(mspId);
		if (customerRepository.existsByMspIdAndName(mspId, customerName)) {
			throw new DuplicateCustomerException(customerName);
		}
	}

	@Transactional
	public CreateTenantResponse insertRunning(UUID mspId, String coreJobId, String customerName, SsoConfig sso) {
		mspRlsSession.apply(mspId);
		if (customerRepository.existsByMspIdAndName(mspId, customerName)) {
			throw new DuplicateCustomerException(customerName);
		}
		ProvisioningJob job = provisioningJobRepository.save(ProvisioningJob.singleRunning(mspId, coreJobId));
		Customer customer = customerRepository.save(Customer.forCreate(mspId, customerName));
		provisioningItemRepository.save(
				ProvisioningItem.firstRow(mspId, job.getId(), customer.getId(), customerName));
		tenantSigninConfigRepository.save(TenantSigninConfig.forSamlCreate(mspId, customer.getId(), sso));
		return new CreateTenantResponse(job.getCoreJobReference(), customerName, ProvisioningStatuses.DB_RUNNING);
	}
}
