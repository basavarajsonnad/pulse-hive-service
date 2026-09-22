package com.portal26.hive.cognito;

import com.portal26.hive.config.CognitoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hive.cognito", name = "bootstrap-admin", havingValue = "true")
public class CognitoAdminUserBootstrap implements ApplicationRunner {

	private final CognitoIdentityProviderClient cognitoClient;
	private final CognitoProperties properties;

	@Override
	public void run(ApplicationArguments args) {
		String email = properties.bootstrapEmail();
		String provider = StringUtils.hasText(properties.bootstrapProvider())
				? properties.bootstrapProvider().trim()
				: "CinchIT";
		try {
			cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
					.userPoolId(properties.userPoolId())
					.username(email)
					.temporaryPassword(properties.bootstrapPassword())
					.messageAction(MessageActionType.SUPPRESS)
					.userAttributes(
							AttributeType.builder().name("email").value(email).build(),
							AttributeType.builder().name("email_verified").value("true").build(),
							AttributeType.builder().name("custom:provider").value(provider).build())
					.build());
			log.info("Created Cognito user {} with custom:provider={}", email, provider);
		}
		catch (UsernameExistsException ex) {
			log.info("Cognito user {} already exists — ensuring custom:provider={}", email, provider);
			cognitoClient.adminUpdateUserAttributes(AdminUpdateUserAttributesRequest.builder()
					.userPoolId(properties.userPoolId())
					.username(email)
					.userAttributes(AttributeType.builder().name("custom:provider").value(provider).build())
					.build());
		}

		cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
				.userPoolId(properties.userPoolId())
				.username(email)
				.password(properties.bootstrapPassword())
				.permanent(true)
				.build());
		log.info("Ensured permanent password for Cognito user {}", email);
	}
}
