package com.portal26.hive.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreJobResponse;
import com.portal26.hive.core.exception.CoreApiException;
import com.portal26.hive.provisioning.entity.MspTenant;
import com.portal26.hive.provisioning.repository.MspTenantRepository;
import com.portal26.hive.provisioning.worker.ProvisioningPollService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The poll function has no HTTP entry point, so this is the only way to exercise it. */
@ExtendWith(MockitoExtension.class)
class ProvisioningPollServiceTest {

	private static final String JOB_ID = "job_1a2b3c4d";

	@Mock
	private CoreTenantClient coreTenantClient;

	@Mock
	private MspTenantRepository mspTenantRepository;

	@InjectMocks
	private ProvisioningPollService provisioningPollService;

	private MspTenant inProgressTenant() {
		MspTenant tenant = new MspTenant();
		tenant.setJobId(JOB_ID);
		tenant.setMspId(UUID.randomUUID());
		tenant.setCustomerName("acme-corp");
		tenant.setStatus(MspTenant.STATUS_IN_PROGRESS);
		tenant.setUpdatedAt(OffsetDateTime.now());
		return tenant;
	}

	@Test
	void copiesCompleteStatusAndTenantNameFromCore() {
		MspTenant tenant = inProgressTenant();
		when(mspTenantRepository.findById(JOB_ID)).thenReturn(Optional.of(tenant));
		when(coreTenantClient.getJob(JOB_ID))
				.thenReturn(new CoreJobResponse(JOB_ID, "acme-corp", "t7k2m9q", "complete"));

		assertThat(provisioningPollService.pollJob(JOB_ID)).isTrue();
		assertThat(tenant.getStatus()).isEqualTo("complete");
		assertThat(tenant.getTenantName()).isEqualTo("t7k2m9q");
		verify(mspTenantRepository).save(tenant);
	}

	@Test
	void storesFailedWithoutTenantNameWhenACriticalStepFails() {
		MspTenant tenant = inProgressTenant();
		when(mspTenantRepository.findById(JOB_ID)).thenReturn(Optional.of(tenant));
		when(coreTenantClient.getJob(JOB_ID))
				.thenReturn(new CoreJobResponse(JOB_ID, "acme-corp", null, "failed"));

		assertThat(provisioningPollService.pollJob(JOB_ID)).isTrue();
		assertThat(tenant.getStatus()).isEqualTo("failed");
		assertThat(tenant.getTenantName()).isNull();
	}

	/** Core omits tenant_name rather than sending null, so absence must not erase a stored name. */
	@Test
	void doesNotBlankAStoredTenantNameWhenCoreOmitsIt() {
		MspTenant tenant = inProgressTenant();
		tenant.setTenantName("t7k2m9q");
		when(mspTenantRepository.findById(JOB_ID)).thenReturn(Optional.of(tenant));
		when(coreTenantClient.getJob(JOB_ID))
				.thenReturn(new CoreJobResponse(JOB_ID, "acme-corp", null, MspTenant.STATUS_IN_PROGRESS));

		assertThat(provisioningPollService.pollJob(JOB_ID)).isFalse();
		assertThat(tenant.getTenantName()).isEqualTo("t7k2m9q");
		verify(mspTenantRepository, never()).save(any());
	}

	/** A failed poll is not a failed job: a transient Core error must leave the row alone. */
	@Test
	void leavesRowUntouchedWhenCoreCallFails() {
		MspTenant tenant = inProgressTenant();
		when(mspTenantRepository.findById(JOB_ID)).thenReturn(Optional.of(tenant));
		when(coreTenantClient.getJob(JOB_ID))
				.thenThrow(new CoreApiException("JOB_NOT_FOUND", "No job with that id."));

		assertThat(provisioningPollService.pollJob(JOB_ID)).isFalse();
		assertThat(tenant.getStatus()).isEqualTo(MspTenant.STATUS_IN_PROGRESS);
		verify(mspTenantRepository, never()).save(any());
	}

	@Test
	void pollsOnlyUnfinishedJobs() {
		MspTenant tenant = inProgressTenant();
		when(mspTenantRepository.findByStatus(MspTenant.STATUS_IN_PROGRESS))
				.thenReturn(List.of(tenant));
		when(mspTenantRepository.findById(JOB_ID)).thenReturn(Optional.of(tenant));
		when(coreTenantClient.getJob(JOB_ID))
				.thenReturn(new CoreJobResponse(JOB_ID, "acme-corp", "t7k2m9q", "complete"));

		assertThat(provisioningPollService.pollDueJobs()).isEqualTo(1);
		verify(mspTenantRepository).findByStatus(MspTenant.STATUS_IN_PROGRESS);
	}
}
