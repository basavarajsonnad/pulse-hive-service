package ai.portal26.hive.provisioning.controller;

import ai.portal26.hive.provisioning.dto.CreateTenantRequest;
import ai.portal26.hive.provisioning.dto.CreateTenantResponse;
import ai.portal26.hive.provisioning.dto.CustomerListResponse;
import ai.portal26.hive.provisioning.service.TenantProvisioningService;
import ai.portal26.hive.provisioning.service.TenantQueryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

	private final TenantQueryService tenantQueryService;
	private final TenantProvisioningService tenantProvisioningService;

	@Autowired
	public TenantController(
			TenantQueryService tenantQueryService,
			TenantProvisioningService tenantProvisioningService) {
		this.tenantQueryService = tenantQueryService;
		this.tenantProvisioningService = tenantProvisioningService;
	}

	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public CustomerListResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return tenantQueryService.listCustomers(page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CreateTenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
		return tenantProvisioningService.create(request);
	}
}
