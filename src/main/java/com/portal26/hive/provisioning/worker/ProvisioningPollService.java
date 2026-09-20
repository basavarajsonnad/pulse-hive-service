package com.portal26.hive.provisioning.worker;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreJobResponse;
import com.portal26.hive.core.exception.CoreApiException;
import com.portal26.hive.provisioning.entity.MspTenant;
import com.portal26.hive.provisioning.repository.MspTenantRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Brings Hive's copy of a provisioning job up to date with Core.
 *
 * <p>Nothing schedules this yet -- there is no {@code @Scheduled} and no interval anywhere, which is
 * a deliberate deferral. Until a timer is added, rows stay {@code in_progress} even after Core
 * finishes. Adding the timer means one annotated method calling {@link #pollDueJobs()}; no code here
 * changes.
 *
 * <p>This is the only consumer of Core's job API. User-facing list and status read the table.
 */
@Service
public class ProvisioningPollService {

	private static final Logger log = LoggerFactory.getLogger(ProvisioningPollService.class);

	private final CoreTenantClient coreTenantClient;
	private final MspTenantRepository mspTenantRepository;

	@Autowired
	public ProvisioningPollService(
			CoreTenantClient coreTenantClient,
			MspTenantRepository mspTenantRepository) {
		this.coreTenantClient = coreTenantClient;
		this.mspTenantRepository = mspTenantRepository;
	}

	/** Ids of the jobs Core may still be working on. */
	@Transactional(readOnly = true)
	public List<String> findDueJobIds() {
		return mspTenantRepository.findByStatus(MspTenant.STATUS_IN_PROGRESS).stream()
				.map(MspTenant::getJobId)
				.toList();
	}

	/**
	 * Polls every unfinished job. Returns how many rows actually changed.
	 *
	 * <p>Deliberately not transactional: each job is its own unit of work, so one Core failure
	 * cannot roll back the jobs already updated in this pass, and no transaction stays open across a
	 * sequence of HTTP calls.
	 */
	public int pollDueJobs() {
		List<String> dueJobIds = findDueJobIds();
		int changed = 0;
		for (String jobId : dueJobIds) {
			if (pollJob(jobId)) {
				changed++;
			}
		}
		log.debug("Polled {} in-progress job(s), {} changed", dueJobIds.size(), changed);
		return changed;
	}

	/**
	 * Polls one job and copies Core's {@code status} and {@code tenant_name} across verbatim.
	 *
	 * <p>Core's states are stored as-is, including {@code complete} when a best-effort step failed:
	 * reinterpreting that here would contradict Core. A failed poll is not a failed job, so on any
	 * Core error the row is left untouched for the next pass rather than marked failed.
	 *
	 * <p>The {@code save} is explicit rather than relying on a dirty-checking flush, because
	 * {@link #pollDueJobs()} calls this method directly and so bypasses the transactional proxy.
	 *
	 * @return whether the stored row changed
	 */
	@Transactional
	public boolean pollJob(String jobId) {
		MspTenant tenant = mspTenantRepository.findById(jobId).orElse(null);
		if (tenant == null) {
			log.warn("Asked to poll unknown job {}", jobId);
			return false;
		}

		CoreJobResponse job;
		try {
			job = coreTenantClient.getJob(jobId);
		} catch (CoreApiException | RestClientException exception) {
			log.warn("Poll failed for job {}, leaving row unchanged: {}", jobId, exception.getMessage());
			return false;
		}

		boolean changed = false;
		if (job.status() != null && !Objects.equals(job.status(), tenant.getStatus())) {
			tenant.setStatus(job.status());
			changed = true;
		}
		// Core omits tenant_name until it is resolved, so absent must never blank a stored value.
		if (job.tenantName() != null && !Objects.equals(job.tenantName(), tenant.getTenantName())) {
			tenant.setTenantName(job.tenantName());
			changed = true;
		}

		if (changed) {
			tenant.setUpdatedAt(OffsetDateTime.now());
			mspTenantRepository.save(tenant);
			log.info("Job {} updated to status={} tenantName={}",
					jobId, tenant.getStatus(), tenant.getTenantName());
		}
		return changed;
	}
}
