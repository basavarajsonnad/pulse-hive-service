package com.portal26.hive.provisioning.worker;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.msp.entity.Msp;
import com.portal26.hive.msp.repository.MspRepository;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.entity.ProvisioningJob;
import com.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import com.portal26.hive.provisioning.service.ProvisioningPollWriteService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background Core job poller. Not request-scoped: there is no logged-in user /
 * {@code SecurityContext}. MSP ids come from the {@code msp} table (rows created
 * on login from Cognito {@code custom:provider}); for each MSP we set RLS then
 * poll that MSP's running jobs.
 */
@Service
public class ProvisioningPollService {

	private static final Logger log = LoggerFactory.getLogger(ProvisioningPollService.class);

	private final MspRepository mspRepository;
	private final MspRlsSession mspRlsSession;
	private final ProvisioningJobRepository provisioningJobRepository;
	private final CoreTenantClient coreTenantClient;
	private final ProvisioningPollWriteService pollWriteService;
	private final TransactionTemplate readTransaction;

	@Autowired
	public ProvisioningPollService(
			MspRepository mspRepository,
			MspRlsSession mspRlsSession,
			ProvisioningJobRepository provisioningJobRepository,
			CoreTenantClient coreTenantClient,
			ProvisioningPollWriteService pollWriteService,
			PlatformTransactionManager transactionManager) {
		this.mspRepository = mspRepository;
		this.mspRlsSession = mspRlsSession;
		this.provisioningJobRepository = provisioningJobRepository;
		this.coreTenantClient = coreTenantClient;
		this.pollWriteService = pollWriteService;
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setReadOnly(true);
	}

	public void pollRunningJobs() {
		for (Msp msp : mspRepository.findAll()) {
			UUID mspId = msp.getId();
			for (ProvisioningJob job : runningJobs(mspId)) {
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

	private List<ProvisioningJob> runningJobs(UUID mspId) {
		List<ProvisioningJob> jobs = readTransaction.execute(status -> {
			mspRlsSession.apply(mspId);
			return provisioningJobRepository.findByMspIdAndStatus(mspId, ProvisioningStatuses.DB_RUNNING);
		});
		if (jobs == null) {
			return List.of();
		}
		return jobs;
	}
}
