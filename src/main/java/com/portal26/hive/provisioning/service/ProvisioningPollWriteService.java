package com.portal26.hive.provisioning.service;

import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.entity.ProvisioningItem;
import com.portal26.hive.provisioning.entity.ProvisioningJob;
import com.portal26.hive.provisioning.repository.ProvisioningItemRepository;
import com.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvisioningPollWriteService {

	private static final Logger log = LoggerFactory.getLogger(ProvisioningPollWriteService.class);

	private final MspRlsSession mspRlsSession;
	private final ProvisioningJobRepository provisioningJobRepository;
	private final ProvisioningItemRepository provisioningItemRepository;
	private final CustomerRepository customerRepository;

	@Autowired
	public ProvisioningPollWriteService(
			MspRlsSession mspRlsSession,
			ProvisioningJobRepository provisioningJobRepository,
			ProvisioningItemRepository provisioningItemRepository,
			CustomerRepository customerRepository) {
		this.mspRlsSession = mspRlsSession;
		this.provisioningJobRepository = provisioningJobRepository;
		this.provisioningItemRepository = provisioningItemRepository;
		this.customerRepository = customerRepository;
	}

	@Transactional
	public void applyCoreStatus(UUID mspId, UUID jobId, CoreJobStatusResponse core) {
		mspRlsSession.apply(mspId);
		Optional<String> mapped = ProvisioningStatuses.resolveHiveStatus(core);
		if (mapped.isEmpty()) {
			log.warn(
					"Skipping poll write for job {} with unknown Core status {}",
					jobId,
					core == null ? null : core.status());
			return;
		}
		Optional<ProvisioningJob> found = provisioningJobRepository.findById(jobId);
		if (found.isEmpty()) {
			return;
		}
		ProvisioningJob job = found.get();
		if (!ProvisioningStatuses.DB_RUNNING.equals(job.getStatus())) {
			return;
		}
		String hiveStatus = mapped.get();
		job.setStatus(hiveStatus);
		if (ProvisioningStatuses.isTerminal(hiveStatus)) {
			job.setFinishedAt(Instant.now());
			if (ProvisioningStatuses.DB_FAILED.equals(hiveStatus)) {
				job.setSuccessCount(0);
				job.setFailureCount(1);
			} else {
				job.setSuccessCount(1);
				job.setFailureCount(0);
			}
		}
		provisioningJobRepository.save(job);

		Instant now = Instant.now();
		String error = itemError(hiveStatus, core);
		String tenantName = core.tenantName();
		for (ProvisioningItem item : provisioningItemRepository.findByJobId(jobId)) {
			item.setStatus(hiveStatus);
			item.setUpdatedAt(now);
			if (error != null) {
				item.setError(error);
			}
			provisioningItemRepository.save(item);
			if (item.getCustomerId() == null) {
				continue;
			}
			customerRepository.findById(item.getCustomerId()).ifPresent(customer -> {
				customer.setStatus(ProvisioningStatuses.toCustomerStatus(hiveStatus));
				customer.setUpdatedAt(now);
				if (tenantName != null && !tenantName.isBlank()) {
					customer.setTenantName(tenantName);
				}
				customerRepository.save(customer);
			});
		}
	}

	private static String itemError(String hiveStatus, CoreJobStatusResponse core) {
		if (ProvisioningStatuses.DB_FAILED.equals(hiveStatus)
				|| ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS.equals(hiveStatus)) {
			return ProvisioningStatuses.firstFailedStepDetail(core);
		}
		return null;
	}
}
