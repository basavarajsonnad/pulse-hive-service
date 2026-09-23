package com.portal26.hive.provisioning.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portal26.hive.config.SessionAuthFilter;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.ErrorCodes;
import com.portal26.hive.exception.GlobalExceptionHandler;
import com.portal26.hive.exception.NotFoundException;
import com.portal26.hive.provisioning.dto.CreateTenantRequest;
import com.portal26.hive.provisioning.dto.CreateTenantResponse;
import com.portal26.hive.provisioning.dto.CustomerListItem;
import com.portal26.hive.provisioning.dto.CustomerListResponse;
import com.portal26.hive.provisioning.dto.RegistrationOutputResponse;
import com.portal26.hive.provisioning.service.TenantProvisioningService;
import com.portal26.hive.provisioning.service.TenantQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = TenantController.class,
		excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SessionAuthFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TenantControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TenantQueryService tenantQueryService;

	@MockitoBean
	private TenantProvisioningService tenantProvisioningService;

	@Test
	void listReturnsCustomersFromHiveDb() throws Exception {
		Instant createdAt = Instant.parse("2026-09-21T05:00:00Z");
		Instant updatedAt = Instant.parse("2026-09-21T05:30:00Z");
		UUID mspId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID acmeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		UUID betaId = UUID.fromString("22222222-2222-2222-2222-222222222222");
		when(tenantQueryService.listCustomers(0, 20))
				.thenReturn(new CustomerListResponse(
						List.of(
								new CustomerListItem(
										acmeId,
										mspId,
										"acme-corp",
										"acme-corp.portal26.ai",
										"basic",
										"completed",
										createdAt,
										updatedAt),
								new CustomerListItem(
										betaId, mspId, "beta-inc", null, "intermediate", "in_progress", createdAt, updatedAt)),
						0,
						20,
						2,
						1));

		mockMvc.perform(get("/api/v1/tenants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers[0].customerId").value(acmeId.toString()))
				.andExpect(jsonPath("$.customers[0].mspId").value(mspId.toString()))
				.andExpect(jsonPath("$.customers[0].customerName").value("acme-corp"))
				.andExpect(jsonPath("$.customers[0].tenantName").value("acme-corp.portal26.ai"))
				.andExpect(jsonPath("$.customers[0].licensePackage").value("basic"))
				.andExpect(jsonPath("$.customers[0].status").value("completed"))
				.andExpect(jsonPath("$.customers[0].createdAt").value("2026-09-21T05:00:00Z"))
				.andExpect(jsonPath("$.customers[0].updatedAt").value("2026-09-21T05:30:00Z"))
				.andExpect(jsonPath("$.customers[1].customerId").value(betaId.toString()))
				.andExpect(jsonPath("$.customers[1].mspId").value(mspId.toString()))
				.andExpect(jsonPath("$.customers[1].customerName").value("beta-inc"))
				.andExpect(jsonPath("$.customers[1].tenantName").isEmpty())
				.andExpect(jsonPath("$.customers[1].licensePackage").value("intermediate"))
				.andExpect(jsonPath("$.customers[1].status").value("in_progress"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void listForwardsPaginationQueryParams() throws Exception {
		when(tenantQueryService.listCustomers(1, 10))
				.thenReturn(new CustomerListResponse(List.of(), 1, 10, 25, 3));

		mockMvc.perform(get("/api/v1/tenants").queryParam("page", "1").queryParam("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(25))
				.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void listReturnsEmptyArrayWhenNoCustomers() throws Exception {
		when(tenantQueryService.listCustomers(0, 20)).thenReturn(new CustomerListResponse(List.of(), 0, 20, 0, 0));

		mockMvc.perform(get("/api/v1/tenants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void listReturnsBadRequestWhenPaginationInvalid() throws Exception {
		when(tenantQueryService.listCustomers(0, 0))
				.thenThrow(new CoreApiException(ErrorCodes.VALIDATION_FAILED, "size must be between 1 and 100"));

		mockMvc.perform(get("/api/v1/tenants").queryParam("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
	}

	@Test
	void signinReturnsRegistrationOutput() throws Exception {
		UUID customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		UUID mspId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		Instant createdAt = Instant.parse("2026-09-21T05:00:00Z");
		Instant updatedAt = Instant.parse("2026-09-21T05:30:00Z");
		when(tenantQueryService.getRegistrationOutput(customerId))
				.thenReturn(new RegistrationOutputResponse(
						customerId,
						mspId,
						"acme-corp",
						"acme-corp.portal26.ai",
						"basic",
						"completed",
						createdAt,
						updatedAt,
						"MANUAL STEP — add these to the Entra app"));

		mockMvc.perform(get("/api/v1/tenants/{customerId}", customerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerId").value(customerId.toString()))
				.andExpect(jsonPath("$.mspId").value(mspId.toString()))
				.andExpect(jsonPath("$.customerName").value("acme-corp"))
				.andExpect(jsonPath("$.tenantName").value("acme-corp.portal26.ai"))
				.andExpect(jsonPath("$.licensePackage").value("basic"))
				.andExpect(jsonPath("$.status").value("completed"))
				.andExpect(jsonPath("$.createdAt").value("2026-09-21T05:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-09-21T05:30:00Z"))
				.andExpect(jsonPath("$.registrationOutput").value("MANUAL STEP — add these to the Entra app"));
	}

	@Test
	void signinReturnsNullWhenNotYetPolled() throws Exception {
		UUID customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		UUID mspId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		Instant createdAt = Instant.parse("2026-09-21T05:00:00Z");
		Instant updatedAt = Instant.parse("2026-09-21T05:30:00Z");
		when(tenantQueryService.getRegistrationOutput(customerId))
				.thenReturn(new RegistrationOutputResponse(
						customerId,
						mspId,
						"acme-corp",
						null,
						"basic",
						"in_progress",
						createdAt,
						updatedAt,
						null));

		mockMvc.perform(get("/api/v1/tenants/{customerId}", customerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerId").value(customerId.toString()))
				.andExpect(jsonPath("$.customerName").value("acme-corp"))
				.andExpect(jsonPath("$.registrationOutput").isEmpty());
	}

	@Test
	void signinReturnsNotFoundWhenCustomerUnknown() throws Exception {
		UUID missingId = UUID.fromString("99999999-9999-9999-9999-999999999999");
		when(tenantQueryService.getRegistrationOutput(missingId))
				.thenThrow(new NotFoundException("customer not found"));

		mockMvc.perform(get("/api/v1/tenants/{customerId}", missingId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("customer not found"));
	}

	@Test
	void createReturnsAcceptedJob() throws Exception {
		when(tenantProvisioningService.create(org.mockito.ArgumentMatchers.any(CreateTenantRequest.class)))
				.thenReturn(new CreateTenantResponse(
						"job_1a2b3c4d", "acme-corp", "basic", "running"));

		mockMvc.perform(post("/api/v1/tenants")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "customerName": "acme-corp",
								  "licensePackage": "basic",
								  "sso": {
								    "metadataUrl": "https://acme.okta.com/app/xyz/sso/saml/metadata",
								    "providerName": "Acme-Okta",
								    "emailAttribute": "email",
								    "groupsAttribute": "groups"
								  }
								}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value("job_1a2b3c4d"))
				.andExpect(jsonPath("$.customerName").value("acme-corp"))
				.andExpect(jsonPath("$.licensePackage").value("basic"))
				.andExpect(jsonPath("$.status").value("running"));
	}

	@Test
	void createForwardsCustomerNameAndSsoToServiceWithoutCoreShapeChecks() throws Exception {
		when(tenantProvisioningService.create(org.mockito.ArgumentMatchers.any(CreateTenantRequest.class)))
				.thenReturn(new CreateTenantResponse("job_1a2b3c4d", "ab", "basic", "running"));

		mockMvc.perform(post("/api/v1/tenants")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "customerName": "ab",
								  "licensePackage": "basic",
								  "sso": {
								    "metadataUrl": "https://acme.okta.com/app/xyz/sso/saml/metadata",
								    "providerName": "Acme-Okta",
								    "emailAttribute": "email"
								  }
								}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value("job_1a2b3c4d"));
	}

	@Test
	void createReturnsBadRequestWhenLicensePackageInvalid() throws Exception {
		mockMvc.perform(post("/api/v1/tenants")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "customerName": "acme-corp",
								  "licensePackage": "premium",
								  "sso": {
								    "metadataUrl": "https://acme.okta.com/app/xyz/sso/saml/metadata",
								    "providerName": "Acme-Okta",
								    "emailAttribute": "email",
								    "groupsAttribute": "groups"
								  }
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
