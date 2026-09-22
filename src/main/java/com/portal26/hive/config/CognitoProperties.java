package com.portal26.hive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hive.cognito")
public record CognitoProperties(
		String region,
		String userPoolId,
		String clientId,
		String clientSecret,
		String issuerUri,
		/** Hosted UI base URL, e.g. https://xxx.auth.ap-south-1.amazoncognito.com */
		String domain,
		/** Must match Cognito app-client callback URL (Hive API callback). */
		String redirectUri,
		String frontendSuccessUrl,
		String frontendErrorUrl,
		/** Cognito allowed sign-out URL (usually frontend). */
		String logoutUri,
		boolean bootstrapAdmin,
		String bootstrapEmail,
		String bootstrapPassword,
		/** Hardcoded custom:provider value written on admin bootstrap. */
		String bootstrapProvider) {
}
