package com.portal26.hive.provisioning.controller;

import com.portal26.hive.provisioning.dto.CreateTenantRequest;
import com.portal26.hive.provisioning.dto.CreateTenantResponse;
import com.portal26.hive.provisioning.dto.JobListResponse;
import com.portal26.hive.provisioning.dto.JobStatusResponse;
import com.portal26.hive.provisioning.service.TenantProvisioningService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three tenant APIs the Next.js layer forwards to. No {@code mspId} is accepted from the
 * client: it is resolved server-side.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

	private final TenantProvisioningService tenantProvisioningService;

	@Autowired
	public TenantController(TenantProvisioningService tenantProvisioningService) {
		this.tenantProvisioningService = tenantProvisioningService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CreateTenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
		return tenantProvisioningService.create(request);
	}

	@GetMapping
	public JobListResponse list() {
		return tenantProvisioningService.list();
	}

	@GetMapping("/{jobId}/status")
	public JobStatusResponse status(@PathVariable String jobId) {
		return tenantProvisioningService.status(jobId);
	}
}
