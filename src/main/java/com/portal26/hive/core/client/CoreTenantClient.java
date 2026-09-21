package com.portal26.hive.core.client;

import com.portal26.hive.core.client.dto.CoreJobStatusResponse;
import com.portal26.hive.core.client.dto.CoreStartProvisioningResponse;
import com.portal26.hive.exception.CoreApiException;
import com.portal26.hive.exception.ErrorCodes;
import com.portal26.hive.provisioning.dto.CreateTenantRequest;
import com.portal26.hive.provisioning.dto.SsoConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CoreTenantClient {

	private final RestClient coreRestClient;

	@Autowired
	public CoreTenantClient(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	public String startProvisioning(CreateTenantRequest request) {
		try {
			CoreStartProvisioningResponse response = coreRestClient.post()
					.uri("/v1/tenants")
					.contentType(MediaType.APPLICATION_JSON)
					.body(toCoreBody(request))
					.retrieve()
					.body(CoreStartProvisioningResponse.class);
			if (response == null || response.jobId() == null || response.jobId().isBlank()) {
				throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, "Core did not return a job id");
			}
			return response.jobId();
		} catch (RestClientResponseException ex) {
			throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, coreMessage(ex));
		} catch (ResourceAccessException ex) {
			throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, "Unable to reach Core");
		}
	}

	public CoreJobStatusResponse getJob(String jobId) {
		try {
			CoreJobStatusResponse response = coreRestClient.get()
					.uri("/v1/tenants/{jobId}", jobId)
					.retrieve()
					.body(CoreJobStatusResponse.class);
			if (response == null) {
				throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, "Core did not return a job");
			}
			return response;
		} catch (RestClientResponseException ex) {
			throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, coreMessage(ex));
		} catch (ResourceAccessException ex) {
			throw new CoreApiException(ErrorCodes.VALIDATION_FAILED, "Unable to reach Core");
		}
	}

	private static Map<String, Object> toCoreBody(CreateTenantRequest request) {
		SsoConfig sso = request.sso();
		Map<String, Object> ssoBody = new LinkedHashMap<>();
		ssoBody.put("metadata_url", sso.metadataUrl());
		ssoBody.put("provider_name", sso.providerName());
		ssoBody.put("email_attribute", sso.emailAttribute());
		ssoBody.put("groups_attribute", sso.groupsAttribute());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("customer_name", request.customerName());
		body.put("sso", ssoBody);
		return body;
	}

	private static String coreMessage(RestClientResponseException ex) {
		String body = ex.getResponseBodyAsString();
		if (body != null && !body.isBlank()) {
			return body;
		}
		return "Core rejected the request";
	}
}
