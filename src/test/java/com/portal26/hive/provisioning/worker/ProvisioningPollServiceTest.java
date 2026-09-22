package com.portal26.hive.provisioning.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.ErrorCodes;
import com.portal26.hive.msp.CurrentMspResolver;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.entity.ProvisioningJob;
import com.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import com.portal26.hive.provisioning.service.ProvisioningPollWriteService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ProvisioningPollServiceTest {

	private static final UUID MSP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String JOB_ONE = "job_one";
	private static final String JOB_TWO = "job_two";

	@Mock
	private CurrentMspResolver currentMspResolver;

	@Mock
	private MspRlsSession mspRlsSession;

	@Mock
	private ProvisioningJobRepository provisioningJobRepository;

	@Mock
	private CoreTenantClient coreTenantClient;

	@Mock
	private ProvisioningPollWriteService pollWriteService;

	@Mock
	private PlatformTransactionManager transactionManager;

	private ProvisioningPollService pollService;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		pollService = new ProvisioningPollService(
				currentMspResolver,
				mspRlsSession,
				provisioningJobRepository,
				coreTenantClient,
				pollWriteService,
				transactionManager);
	}

	@Test
	void pollsEachRunningJob() {
		ProvisioningJob first = ProvisioningJob.singleRunning(MSP_ID, JOB_ONE);
		ProvisioningJob second = ProvisioningJob.singleRunning(MSP_ID, JOB_TWO);
		CoreJobStatusResponse firstCore = core(JOB_ONE);
		CoreJobStatusResponse secondCore = core(JOB_TWO);
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(provisioningJobRepository.findByMspIdAndStatus(MSP_ID, ProvisioningStatuses.DB_RUNNING))
				.thenReturn(List.of(first, second));
		when(coreTenantClient.getJob(JOB_ONE)).thenReturn(firstCore);
		when(coreTenantClient.getJob(JOB_TWO)).thenReturn(secondCore);

		pollService.pollRunningJobs();

		verify(mspRlsSession).apply(MSP_ID);
		verify(coreTenantClient).getJob(JOB_ONE);
		verify(coreTenantClient).getJob(JOB_TWO);
		verify(pollWriteService).applyCoreStatus(MSP_ID, first.getId(), firstCore);
		verify(pollWriteService).applyCoreStatus(MSP_ID, second.getId(), secondCore);
	}

	@Test
	void skipsWriteWhenCoreIsDown() {
		ProvisioningJob job = ProvisioningJob.singleRunning(MSP_ID, JOB_ONE);
		when(currentMspResolver.currentMspId()).thenReturn(MSP_ID);
		when(provisioningJobRepository.findByMspIdAndStatus(MSP_ID, ProvisioningStatuses.DB_RUNNING))
				.thenReturn(List.of(job));
		when(coreTenantClient.getJob(JOB_ONE))
				.thenThrow(new CoreApiException(ErrorCodes.VALIDATION_FAILED, "Unable to reach Core"));

		pollService.pollRunningJobs();

		verify(coreTenantClient).getJob(JOB_ONE);
		verify(pollWriteService, never()).applyCoreStatus(any(), any(), any());
	}

	private static CoreJobStatusResponse core(String jobId) {
		return new CoreJobStatusResponse(
				jobId, "acme-corp", null, ProvisioningStatuses.CORE_IN_PROGRESS, List.of());
	}
}
