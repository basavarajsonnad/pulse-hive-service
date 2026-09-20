package com.portal26.hive.provisioning.controller;

import com.portal26.hive.provisioning.dto.CustomerListResponse;
import com.portal26.hive.provisioning.service.TenantQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

	private final TenantQueryService tenantQueryService;

	@Autowired
	public TenantController(TenantQueryService tenantQueryService) {
		this.tenantQueryService = tenantQueryService;
	}

	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public CustomerListResponse list() {
		return tenantQueryService.listCustomers();
	}
}
