package ai.portal26.hive.config;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreRestClientConfig {

	private static final Logger log = LoggerFactory.getLogger(CoreRestClientConfig.class);

	@Bean
	RestClient coreRestClient(
			@Value("${core.base-url}") String baseUrl,
			@Value("${core.internal-client-id:}") String internalClientId,
			@Value("${core.connect-timeout-ms}") long connectTimeoutMs,
			@Value("${core.read-timeout-ms}") long readTimeoutMs) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
		factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.defaultHeader("X-P26-Internal-Client-Id", internalClientId)
				.requestInterceptor((request, body, execution) -> {
					String requestId = UUID.randomUUID().toString();
					request.getHeaders().set("X-P26-Request-Id", requestId);
					var response = execution.execute(request, body);
					String echoed = response.getHeaders().getFirst("X-P26-Request-Id");
					log.debug(
							"Core {} {} requestId={} echoed={}",
							request.getMethod(),
							request.getURI(),
							requestId,
							echoed);
					return response;
				})
				.build();
	}
}
