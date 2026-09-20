package com.portal26.hive.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hive.cognito")
public record CognitoProperties(
		String region,
		String userPoolId,
		String clientId,
		String clientSecret,
		String issuerUri,
		boolean bootstrapAdmin,
		String bootstrapEmail,
		String bootstrapPassword) {
}
