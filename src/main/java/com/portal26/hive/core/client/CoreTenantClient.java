package com.portal26.hive.core.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal26.hive.core.client.dto.CoreCreateTenantRequest;
import com.portal26.hive.core.client.dto.CoreCreateTenantResponse;
import com.portal26.hive.core.client.dto.CoreJobResponse;
import com.portal26.hive.core.exception.CoreApiException;
import com.portal26.hive.core.exception.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Hive's only route to Core's partner gateway.
 *
 * <p>There is no retry: resilience4j is not a dependency, and a retried create is not safe anyway
 * because each attempt can hand back a different {@code job_id}. If retries are added later, apply
 * them to {@link #getJob(String)} only.
 */
@Component
public class CoreTenantClient {

	private static final Logger log = LoggerFactory.getLogger(CoreTenantClient.class);
	private static final String REQUEST_ID_HEADER = "X-P26-Request-Id";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Autowired
	public CoreTenantClient(RestClient coreRestClient, ObjectMapper objectMapper) {
		this.restClient = coreRestClient;
		this.objectMapper = objectMapper;
	}

	/** Starts provisioning. Core answers {@code 202} with a job id; nothing is provisioned yet. */
	public CoreCreateTenantResponse startProvisioning(CoreCreateTenantRequest request) {
		String requestId = UUID.randomUUID().toString();

		ResponseEntity<CoreCreateTenantResponse> response = restClient.post()
				.uri("/v1/tenants")
				.header(REQUEST_ID_HEADER, requestId)
				.body(request)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.toEntity(CoreCreateTenantResponse.class);

		log.info("Core create tenant customerName={} requestId={} coreRequestId={}",
				request.customerName(), requestId, echoedRequestId(response));
		return response.getBody();
	}

	/** Reads a provisioning job. Used only by the poll worker, never on a user request path. */
	public CoreJobResponse getJob(String jobId) {
		String requestId = UUID.randomUUID().toString();

		ResponseEntity<CoreJobResponse> response = restClient.get()
				.uri("/v1/tenants/{jobId}", jobId)
				.header(REQUEST_ID_HEADER, requestId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.toEntity(CoreJobResponse.class);

		log.debug("Core get job jobId={} requestId={} coreRequestId={}",
				jobId, requestId, echoedRequestId(response));
		return response.getBody();
	}

	private String echoedRequestId(ResponseEntity<?> response) {
		return response.getHeaders().getFirst(REQUEST_ID_HEADER);
	}

	/**
	 * Translates Core's error envelope into {@link CoreApiException}. Core's own {@code code} is
	 * preserved when present so it reaches the UI unchanged.
	 */
	private void raise(HttpRequest request, ClientHttpResponse response) throws IOException {
		int status = response.getStatusCode().value();
		String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

		String code = null;
		String message = null;
		try {
			JsonNode json = objectMapper.readTree(body);
			if (json.hasNonNull("code")) {
				code = json.get("code").asText();
			}
			if (json.hasNonNull("message")) {
				message = json.get("message").asText();
			}
		} catch (IOException e) {
			log.warn("Core error body was not JSON. status={} body={}", status, body);
		}

		if (code == null) {
			code = status == 404 ? ErrorCodes.JOB_NOT_FOUND : ErrorCodes.VALIDATION_FAILED;
		}
		if (message == null) {
			message = "Core rejected the request";
		}

		log.warn("Core error status={} code={} message={}", status, code, message);
		throw new CoreApiException(code, message);
	}
}
