package com.portal26.hive.provisioning.service;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreCreateTenantRequest;
import com.portal26.hive.core.client.dto.CoreCreateTenantResponse;
import com.portal26.hive.core.client.dto.CoreSsoConfig;
import com.portal26.hive.msp.service.CurrentMspResolver;
import com.portal26.hive.provisioning.dto.CreateTenantRequest;
import com.portal26.hive.provisioning.dto.CreateTenantResponse;
import com.portal26.hive.provisioning.dto.JobListItem;
import com.portal26.hive.provisioning.dto.JobListResponse;
import com.portal26.hive.provisioning.dto.JobStatusResponse;
import com.portal26.hive.provisioning.dto.SsoConfigDto;
import com.portal26.hive.provisioning.entity.MspTenant;
import com.portal26.hive.provisioning.exception.JobNotFoundException;
import com.portal26.hive.provisioning.repository.MspTenantRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Create goes to Core; list and status read Hive's own table and never call Core. */
@Service
public class TenantProvisioningService {

	private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

	private final CoreTenantClient coreTenantClient;
	private final MspTenantRepository mspTenantRepository;
	private final CurrentMspResolver currentMspResolver;

	@Autowired
	public TenantProvisioningService(
			CoreTenantClient coreTenantClient,
			MspTenantRepository mspTenantRepository,
			CurrentMspResolver currentMspResolver) {
		this.coreTenantClient = coreTenantClient;
		this.mspTenantRepository = mspTenantRepository;
		this.currentMspResolver = currentMspResolver;
	}

	/**
	 * Not transactional on purpose: the Core call must not hold a database connection for its
	 * duration. The row is written only after Core accepts, so a rejected create leaves no trace.
	 *
	 * <p>Repeating a create for the same customer yields a new job id from Core and therefore a new
	 * row. The newest row is the current one.
	 */
	public CreateTenantResponse create(CreateTenantRequest request) {
		UUID mspId = currentMspResolver.currentMspId();

		CoreCreateTenantResponse coreResponse = coreTenantClient.startProvisioning(
				new CoreCreateTenantRequest(request.customerName(), toCoreSso(request.sso())));

		MspTenant tenant = new MspTenant();
		tenant.setJobId(coreResponse.jobId());
		tenant.setMspId(mspId);
		tenant.setCustomerName(coreResponse.customerName());
		tenant.setStatus(coreResponse.status());
		tenant.setUpdatedAt(OffsetDateTime.now());
		mspTenantRepository.save(tenant);

		log.info("Stored provisioning job jobId={} mspId={} customerName={} status={}",
				tenant.getJobId(), mspId, tenant.getCustomerName(), tenant.getStatus());

		return new CreateTenantResponse(
				tenant.getJobId(), tenant.getCustomerName(), tenant.getStatus());
	}

	@Transactional(readOnly = true)
	public JobListResponse list() {
		UUID mspId = currentMspResolver.currentMspId();
		List<JobListItem> jobs = mspTenantRepository.findByMspIdOrderByCreatedAtDesc(mspId).stream()
				.map(tenant -> new JobListItem(
						tenant.getJobId(),
						tenant.getCustomerName(),
						tenant.getStatus(),
						tenant.getCreatedAt()))
				.toList();
		return new JobListResponse(jobs);
	}

	@Transactional(readOnly = true)
	public JobStatusResponse status(String jobId) {
		UUID mspId = currentMspResolver.currentMspId();
		MspTenant tenant = mspTenantRepository.findByJobIdAndMspId(jobId, mspId)
				.orElseThrow(() -> new JobNotFoundException(jobId));
		return new JobStatusResponse(
				tenant.getJobId(), tenant.getCustomerName(), tenant.getStatus());
	}

	private CoreSsoConfig toCoreSso(SsoConfigDto sso) {
		if (sso == null) {
			return null;
		}
		return new CoreSsoConfig(
				sso.metadataUrl(), sso.providerName(), sso.emailAttribute(), sso.groupsAttribute());
	}
}
