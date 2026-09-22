package ai.portal26.hive.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hive.session")
public record SessionProperties(
		String cookieName,
		Duration ttl,
		boolean cookieSecure) {
}
