package com.portal26.hive.provisioning.service;

import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.core.client.dto.CoreJobStepResponse;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.customer.repository.TenantSigninConfigRepository;
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
	private final TenantSigninConfigRepository tenantSigninConfigRepository;

	@Autowired
	public ProvisioningPollWriteService(
			MspRlsSession mspRlsSession,
			ProvisioningJobRepository provisioningJobRepository,
			ProvisioningItemRepository provisioningItemRepository,
			CustomerRepository customerRepository,
			TenantSigninConfigRepository tenantSigninConfigRepository) {
		this.mspRlsSession = mspRlsSession;
		this.provisioningJobRepository = provisioningJobRepository;
		this.provisioningItemRepository = provisioningItemRepository;
		this.customerRepository = customerRepository;
		this.tenantSigninConfigRepository = tenantSigninConfigRepository;
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
		if (ProvisioningStatuses.DB_RUNNING.equals(hiveStatus)) {
			updateItemProgressOnly(jobId, core);
			return;
		}
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
		Optional<CoreJobStepResponse> saml = ProvisioningStatuses.succeededSamlRegistration(core);
		for (ProvisioningItem item : provisioningItemRepository.findByJobId(jobId)) {
			item.setStatus(hiveStatus);
			item.setUpdatedAt(now);
			item.setError(error);
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
			saml.ifPresent(step -> persistSamlRegistration(item.getCustomerId(), step, now));
		}
	}

	private void updateItemProgressOnly(UUID jobId, CoreJobStatusResponse core) {
		Instant now = Instant.now();
		String itemStatus = ProvisioningStatuses.midRunItemStatus(core);
		String itemError = ProvisioningStatuses.midRunItemError(core);
		Optional<String> tenantName = ProvisioningStatuses.resolvedTenantName(core);
		for (ProvisioningItem item : provisioningItemRepository.findByJobId(jobId)) {
			item.setStatus(itemStatus);
			item.setError(itemError);
			item.setUpdatedAt(now);
			provisioningItemRepository.save(item);
			tenantName.ifPresent(name -> writeTenantNameOnly(item.getCustomerId(), name, now));
		}
	}

	private void writeTenantNameOnly(UUID customerId, String tenantName, Instant now) {
		if (customerId == null) {
			return;
		}
		customerRepository.findById(customerId).ifPresent(customer -> {
			if (tenantName.equals(customer.getTenantName())) {
				return;
			}
			customer.setTenantName(tenantName);
			customer.setUpdatedAt(now);
			customerRepository.save(customer);
		});
	}

	private void persistSamlRegistration(UUID customerId, CoreJobStepResponse step, Instant now) {
		tenantSigninConfigRepository.findByCustomerId(customerId).ifPresent(config -> {
			config.setRegistrationOutput(step.detail());
			config.setUpdatedAt(now);
			if (config.getRegisteredAt() == null) {
				config.setRegisteredAt(step.endedAt() != null ? step.endedAt() : now);
			}
			tenantSigninConfigRepository.save(config);
		});
	}

	private static String itemError(String hiveStatus, CoreJobStatusResponse core) {
		if (ProvisioningStatuses.DB_FAILED.equals(hiveStatus)
				|| ProvisioningStatuses.DB_COMPLETED_WITH_ERRORS.equals(hiveStatus)) {
			return ProvisioningStatuses.failedStepDetails(core);
		}
		return null;
	}
}
