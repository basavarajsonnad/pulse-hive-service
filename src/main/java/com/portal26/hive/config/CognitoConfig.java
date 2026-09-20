package com.portal26.hive.config;

import com.portal26.hive.config.CognitoProperties;
import com.portal26.hive.config.SessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
@EnableConfigurationProperties({ CognitoProperties.class, SessionProperties.class })
public class CognitoConfig {

	@Bean
	CognitoIdentityProviderClient cognitoIdentityProviderClient(CognitoProperties properties) {
		return CognitoIdentityProviderClient.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}
}
