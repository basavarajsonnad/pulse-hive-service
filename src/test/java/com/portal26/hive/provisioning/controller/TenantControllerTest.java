package com.portal26.hive.provisioning.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.ErrorCodes;
import com.portal26.hive.exception.GlobalExceptionHandler;
import com.portal26.hive.provisioning.dto.CreateTenantRequest;
import com.portal26.hive.provisioning.dto.CreateTenantResponse;
import com.portal26.hive.provisioning.dto.CustomerListItem;
import com.portal26.hive.provisioning.dto.CustomerListResponse;
import com.portal26.hive.provisioning.service.TenantProvisioningService;
import com.portal26.hive.provisioning.service.TenantQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantController.class)
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
		when(tenantQueryService.listCustomers(0, 20))
				.thenReturn(new CustomerListResponse(
						List.of(
								new CustomerListItem(
										"acme-corp",
										"acme-corp.portal26.ai",
										"completed",
										createdAt,
										updatedAt),
								new CustomerListItem("beta-inc", null, "running", createdAt, updatedAt)),
						0,
						20,
						2,
						1));

		mockMvc.perform(get("/api/v1/tenants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers[0].customerName").value("acme-corp"))
				.andExpect(jsonPath("$.customers[0].tenantName").value("acme-corp.portal26.ai"))
				.andExpect(jsonPath("$.customers[0].status").value("completed"))
				.andExpect(jsonPath("$.customers[0].createdAt").value("2026-09-21T05:00:00Z"))
				.andExpect(jsonPath("$.customers[0].updatedAt").value("2026-09-21T05:30:00Z"))
				.andExpect(jsonPath("$.customers[1].customerName").value("beta-inc"))
				.andExpect(jsonPath("$.customers[1].tenantName").isEmpty())
				.andExpect(jsonPath("$.customers[1].status").value("running"))
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
	void createReturnsAcceptedJob() throws Exception {
		when(tenantProvisioningService.create(org.mockito.ArgumentMatchers.any(CreateTenantRequest.class)))
				.thenReturn(new CreateTenantResponse(
						"job_1a2b3c4d", "acme-corp", "running"));

		mockMvc.perform(post("/api/v1/tenants")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "customerName": "acme-corp",
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
				.andExpect(jsonPath("$.status").value("running"));
	}

	@Test
	void createReturnsBadRequestWhenCustomerNameInvalid() throws Exception {
		mockMvc.perform(post("/api/v1/tenants")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "customerName": "ab",
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

	@Test
	void createReturnsBadRequestWhenSsoFieldMissing() throws Exception {
		mockMvc.perform(post("/api/v1/tenants")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "customerName": "acme-corp",
								  "sso": {
								    "metadataUrl": "https://acme.okta.com/app/xyz/sso/saml/metadata",
								    "providerName": "Acme-Okta",
								    "emailAttribute": "email"
								  }
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
