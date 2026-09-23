package com.portal26.hive.provisioning.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.ErrorCodes;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.msp.entity.Msp;
import com.portal26.hive.msp.repository.MspRepository;
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

	private static final String JOB_ONE = "job_one";
	private static final String JOB_TWO = "job_two";

	@Mock
	private MspRepository mspRepository;

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
	private Msp msp;

	@BeforeEach
	void setUp() {
		lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		msp = Msp.forProviderCreate("CinchIT");
		pollService = new ProvisioningPollService(
				mspRepository,
				mspRlsSession,
				provisioningJobRepository,
				coreTenantClient,
				pollWriteService,
				transactionManager);
	}

	@Test
	void pollsEachRunningJobPerMsp() {
		UUID mspId = msp.getId();
		ProvisioningJob first = ProvisioningJob.singleRunning(mspId, JOB_ONE);
		ProvisioningJob second = ProvisioningJob.singleRunning(mspId, JOB_TWO);
		CoreJobStatusResponse firstCore = core(JOB_ONE);
		CoreJobStatusResponse secondCore = core(JOB_TWO);
		when(mspRepository.findAll()).thenReturn(List.of(msp));
		when(provisioningJobRepository.findByMspIdAndStatus(mspId, ProvisioningStatuses.DB_RUNNING))
				.thenReturn(List.of(first, second));
		when(coreTenantClient.getJob(JOB_ONE)).thenReturn(firstCore);
		when(coreTenantClient.getJob(JOB_TWO)).thenReturn(secondCore);

		pollService.pollRunningJobs();

		verify(mspRlsSession).apply(mspId);
		verify(coreTenantClient).getJob(JOB_ONE);
		verify(coreTenantClient).getJob(JOB_TWO);
		verify(pollWriteService).applyCoreStatus(mspId, first.getId(), firstCore);
		verify(pollWriteService).applyCoreStatus(mspId, second.getId(), secondCore);
	}

	@Test
	void skipsWriteWhenCoreIsDown() {
		UUID mspId = msp.getId();
		ProvisioningJob job = ProvisioningJob.singleRunning(mspId, JOB_ONE);
		when(mspRepository.findAll()).thenReturn(List.of(msp));
		when(provisioningJobRepository.findByMspIdAndStatus(mspId, ProvisioningStatuses.DB_RUNNING))
				.thenReturn(List.of(job));
		when(coreTenantClient.getJob(JOB_ONE))
				.thenThrow(new CoreApiException(ErrorCodes.VALIDATION_FAILED, "Unable to reach Core"));

		pollService.pollRunningJobs();

		verify(coreTenantClient).getJob(JOB_ONE);
		verify(pollWriteService, never()).applyCoreStatus(any(), any(), any());
	}

	@Test
	void doesNothingWhenNoMsps() {
		when(mspRepository.findAll()).thenReturn(List.of());

		pollService.pollRunningJobs();

		verify(provisioningJobRepository, never()).findByMspIdAndStatus(any(), any());
		verify(coreTenantClient, never()).getJob(any());
	}

	private static CoreJobStatusResponse core(String jobId) {
		return new CoreJobStatusResponse(
				jobId, "acme-corp", null, ProvisioningStatuses.CORE_IN_PROGRESS, List.of());
	}
}
