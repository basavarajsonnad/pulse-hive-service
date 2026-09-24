package com.portal26.hive.provisioning.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.core.client.CoreTenantClient;
import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.customer.entity.Customer;
import com.portal26.hive.customer.repository.CustomerRepository;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.ErrorCodes;
import com.portal26.hive.msp.MspRlsSession;
import com.portal26.hive.msp.entity.Msp;
import com.portal26.hive.msp.repository.MspRepository;
import com.portal26.hive.provisioning.ProvisioningStatuses;
import com.portal26.hive.provisioning.entity.ProvisioningItem;
import com.portal26.hive.provisioning.entity.ProvisioningJob;
import com.portal26.hive.provisioning.repository.ProvisioningItemRepository;
import com.portal26.hive.provisioning.repository.ProvisioningJobRepository;
import com.portal26.hive.provisioning.service.ProvisioningPollWriteService;
import java.util.List;
import java.util.Optional;
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
	private CustomerRepository customerRepository;

	@Mock
	private ProvisioningItemRepository provisioningItemRepository;

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
				customerRepository,
				provisioningItemRepository,
				provisioningJobRepository,
				coreTenantClient,
				pollWriteService,
				transactionManager);
	}

	@Test
	void pollsJobsForInProgressCustomersOnly() {
		UUID mspId = msp.getId();
		Customer firstCustomer = Customer.forCreate(mspId, "ok-acme", Customer.LICENSE_PACKAGE_BASIC);
		Customer secondCustomer = Customer.forCreate(mspId, "c1-acme", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningJob first = ProvisioningJob.singleRunning(mspId, JOB_ONE);
		ProvisioningJob second = ProvisioningJob.singleRunning(mspId, JOB_TWO);
		ProvisioningItem firstItem =
				ProvisioningItem.firstRow(mspId, first.getId(), firstCustomer.getId(), "ok-acme");
		ProvisioningItem secondItem =
				ProvisioningItem.firstRow(mspId, second.getId(), secondCustomer.getId(), "c1-acme");
		CoreJobStatusResponse firstCore = core(JOB_ONE);
		CoreJobStatusResponse secondCore = core(JOB_TWO);
		when(mspRepository.findAll()).thenReturn(List.of(msp));
		when(customerRepository.findByMspIdAndStatus(mspId, ProvisioningStatuses.CUSTOMER_IN_PROGRESS))
				.thenReturn(List.of(firstCustomer, secondCustomer));
		when(provisioningItemRepository.findByCustomerId(firstCustomer.getId())).thenReturn(List.of(firstItem));
		when(provisioningItemRepository.findByCustomerId(secondCustomer.getId())).thenReturn(List.of(secondItem));
		when(provisioningJobRepository.findById(first.getId())).thenReturn(Optional.of(first));
		when(provisioningJobRepository.findById(second.getId())).thenReturn(Optional.of(second));
		when(coreTenantClient.getJob(JOB_ONE)).thenReturn(firstCore);
		when(coreTenantClient.getJob(JOB_TWO)).thenReturn(secondCore);

		pollService.pollRunningJobs();

		verify(mspRlsSession).apply(mspId);
		verify(provisioningJobRepository, never()).findByMspIdAndStatus(any(), any());
		verify(coreTenantClient).getJob(JOB_ONE);
		verify(coreTenantClient).getJob(JOB_TWO);
		verify(pollWriteService).applyCoreStatus(mspId, first.getId(), firstCore);
		verify(pollWriteService).applyCoreStatus(mspId, second.getId(), secondCore);
	}

	@Test
	void skipsFinishedJobsEvenIfCustomerIsStillInProgress() {
		UUID mspId = msp.getId();
		Customer customer = Customer.forCreate(mspId, "ok-acme", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningJob job = ProvisioningJob.singleRunning(mspId, JOB_ONE);
		job.setStatus(ProvisioningStatuses.DB_COMPLETED);
		ProvisioningItem item = ProvisioningItem.firstRow(mspId, job.getId(), customer.getId(), "ok-acme");
		when(mspRepository.findAll()).thenReturn(List.of(msp));
		when(customerRepository.findByMspIdAndStatus(mspId, ProvisioningStatuses.CUSTOMER_IN_PROGRESS))
				.thenReturn(List.of(customer));
		when(provisioningItemRepository.findByCustomerId(customer.getId())).thenReturn(List.of(item));
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

		pollService.pollRunningJobs();

		verify(coreTenantClient, never()).getJob(any());
		verify(pollWriteService, never()).applyCoreStatus(any(), any(), any());
	}

	@Test
	void skipsWriteWhenCoreIsDown() {
		UUID mspId = msp.getId();
		Customer customer = Customer.forCreate(mspId, "ok-acme", Customer.LICENSE_PACKAGE_BASIC);
		ProvisioningJob job = ProvisioningJob.singleRunning(mspId, JOB_ONE);
		ProvisioningItem item = ProvisioningItem.firstRow(mspId, job.getId(), customer.getId(), "ok-acme");
		when(mspRepository.findAll()).thenReturn(List.of(msp));
		when(customerRepository.findByMspIdAndStatus(mspId, ProvisioningStatuses.CUSTOMER_IN_PROGRESS))
				.thenReturn(List.of(customer));
		when(provisioningItemRepository.findByCustomerId(customer.getId())).thenReturn(List.of(item));
		when(provisioningJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
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

		verify(customerRepository, never()).findByMspIdAndStatus(any(), any());
		verify(provisioningJobRepository, never()).findByMspIdAndStatus(any(), any());
		verify(coreTenantClient, never()).getJob(any());
	}

	private static CoreJobStatusResponse core(String jobId) {
		return new CoreJobStatusResponse(
				jobId, "acme-corp", null, ProvisioningStatuses.CORE_IN_PROGRESS, List.of());
	}
}
