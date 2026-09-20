package com.portal26.hive.provisioning.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portal26.hive.exception.GlobalExceptionHandler;
import com.portal26.hive.provisioning.dto.CustomerListItem;
import com.portal26.hive.provisioning.dto.CustomerListResponse;
import com.portal26.hive.provisioning.service.TenantQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantController.class)
@Import(GlobalExceptionHandler.class)
class TenantControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TenantQueryService tenantQueryService;

	@Test
	void listReturnsCustomersFromHiveDb() throws Exception {
		when(tenantQueryService.listCustomers())
				.thenReturn(new CustomerListResponse(List.of(
						new CustomerListItem("acme-corp", "acme-corp.portal26.ai"),
						new CustomerListItem("beta-inc", null))));

		mockMvc.perform(get("/api/v1/tenants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers[0].customerName").value("acme-corp"))
				.andExpect(jsonPath("$.customers[0].tenantName").value("acme-corp.portal26.ai"))
				.andExpect(jsonPath("$.customers[1].customerName").value("beta-inc"))
				.andExpect(jsonPath("$.customers[1].tenantName").isEmpty());
	}

	@Test
	void listReturnsEmptyArrayWhenNoCustomers() throws Exception {
		when(tenantQueryService.listCustomers()).thenReturn(new CustomerListResponse(List.of()));

		mockMvc.perform(get("/api/v1/tenants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers").isEmpty());
	}
}
