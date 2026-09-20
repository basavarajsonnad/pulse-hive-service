package com.portal26.hive.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Client for Core's partner gateway. {@code portal26.core.base-url} must already include the
 * {@code /partner-gateway} base path.
 */
@Configuration
public class CoreRestClientConfig {

	public static final String INTERNAL_CLIENT_ID_HEADER = "X-P26-Internal-Client-Id";

	/**
	 * Built from {@link RestClient#builder()} rather than an injected {@code RestClient.Builder}:
	 * this Boot setup has no auto-configured builder bean. The Core DTOs declare their own naming and
	 * inclusion strategies, so they do not depend on the application's {@code ObjectMapper}.
	 */
	@Bean
	RestClient coreRestClient(
			@Value("${portal26.core.base-url}") String baseUrl,
			@Value("${portal26.core.internal-client-id}") String internalClientId,
			@Value("${portal26.core.connect-timeout-ms}") long connectTimeoutMs,
			@Value("${portal26.core.read-timeout-ms}") long readTimeoutMs) {

		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(connectTimeoutMs))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader(INTERNAL_CLIENT_ID_HEADER, internalClientId)
				.build();
	}
}
