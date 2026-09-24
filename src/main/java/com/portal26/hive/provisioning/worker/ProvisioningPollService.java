package com.portal26.hive.provisioning.worker;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.msp.entity.Msp;
import com.portal26.hive.msp.repository.MspRepository;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.entity.ProvisioningItem;
import com.portal26.hive.provisioning.entity.ProvisioningJob;
import com.portal26.hive.provisioning.repository.ProvisioningItemRepository;
import com.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import com.portal26.hive.provisioning.service.ProvisioningPollWriteService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background Core job poller. Polls only customers with status {@code in_progress}.
 * Not request-scoped: MSP ids come from the {@code msp} table.
 */
@Service
public class ProvisioningPollService {

	private static final Logger log = LoggerFactory.getLogger(ProvisioningPollService.class);

	private final MspRepository mspRepository;
	private final MspRlsSession mspRlsSession;
	private final CustomerRepository customerRepository;
	private final ProvisioningItemRepository provisioningItemRepository;
	private final ProvisioningJobRepository provisioningJobRepository;
	private final CoreTenantClient coreTenantClient;
	private final ProvisioningPollWriteService pollWriteService;
	private final TransactionTemplate readTransaction;

	@Autowired
	public ProvisioningPollService(
			MspRepository mspRepository,
			MspRlsSession mspRlsSession,
			CustomerRepository customerRepository,
			ProvisioningItemRepository provisioningItemRepository,
			ProvisioningJobRepository provisioningJobRepository,
			CoreTenantClient coreTenantClient,
			ProvisioningPollWriteService pollWriteService,
			PlatformTransactionManager transactionManager) {
		this.mspRepository = mspRepository;
		this.mspRlsSession = mspRlsSession;
		this.customerRepository = customerRepository;
		this.provisioningItemRepository = provisioningItemRepository;
		this.provisioningJobRepository = provisioningJobRepository;
		this.coreTenantClient = coreTenantClient;
		this.pollWriteService = pollWriteService;
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setReadOnly(true);
	}

	public void pollRunningJobs() {
		for (Msp msp : mspRepository.findAll()) {
			UUID mspId = msp.getId();
			for (ProvisioningJob job : jobsForInProgressCustomers(mspId)) {
				if (!ProvisioningStatuses.DB_RUNNING.equals(job.getStatus())) {
					continue;
				}
				try {
					CoreJobStatusResponse core = coreTenantClient.getJob(job.getCoreJobReference());
					pollWriteService.applyCoreStatus(mspId, job.getId(), core);
				}
				catch (CoreApiException ex) {
					log.warn(
							"Skipping poll for job {} (core {}): {}",
							job.getId(),
							job.getCoreJobReference(),
							ex.getMessage());
				}
			}
		}
	}

	private List<ProvisioningJob> jobsForInProgressCustomers(UUID mspId) {
		List<ProvisioningJob> jobs = readTransaction.execute(status -> {
			mspRlsSession.apply(mspId);
			Set<UUID> jobIds = new LinkedHashSet<>();
			for (Customer customer :
					customerRepository.findByMspIdAndStatus(mspId, ProvisioningStatuses.CUSTOMER_IN_PROGRESS)) {
				for (ProvisioningItem item : provisioningItemRepository.findByCustomerId(customer.getId())) {
					if (item.getJobId() != null) {
						jobIds.add(item.getJobId());
					}
				}
			}
			return jobIds.stream()
					.map(provisioningJobRepository::findById)
					.flatMap(java.util.Optional::stream)
					.toList();
		});
		if (jobs == null) {
			return List.of();
		}
		return jobs;
	}
}
