package com.portal26.hive.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Shared WebClient for Portal26 Core with bounded connection pool and Micrometer-ready metrics.
 */
@Configuration
public class WebClientConfig {

	@Bean
	WebClient.Builder coreWebClientBuilder(
			@Value("${portal26.core.base-url}") String baseUrl,
			@Value("${portal26.core.http.connect-timeout-ms}") int connectTimeoutMs,
			@Value("${portal26.core.http.response-timeout-ms}") int responseTimeoutMs,
			@Value("${portal26.core.http.max-connections}") int maxConnections,
			@Value("${portal26.core.http.max-idle-time-ms}") int maxIdleTimeMs,
			@Value("${portal26.core.http.pending-acquire-timeout-ms}") int pendingAcquireTimeoutMs,
			@Value("${portal26.core.http.metrics-enabled}") boolean metricsEnabled) {

		ConnectionProvider.Builder providerBuilder = ConnectionProvider.builder("portal26-core")
				.maxConnections(maxConnections)
				.maxIdleTime(Duration.ofMillis(maxIdleTimeMs))
				.pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))
				.evictInBackground(Duration.ofSeconds(30));

		if (metricsEnabled) {
			providerBuilder.metrics(true);
		}

		HttpClient httpClient = HttpClient.create(providerBuilder.build())
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
				.responseTimeout(Duration.ofMillis(responseTimeoutMs));

		return WebClient.builder()
				.baseUrl(baseUrl)
				.clientConnector(new ReactorClientHttpConnector(httpClient));
	}
}
