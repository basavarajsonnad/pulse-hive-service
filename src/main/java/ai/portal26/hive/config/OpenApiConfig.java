package ai.portal26.hive.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI pulseHiveOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Pulse Hive Service API")
						.version("v1")
						.description("Portal26 Hive backend — Pulse Hive Service"));
	}
}
