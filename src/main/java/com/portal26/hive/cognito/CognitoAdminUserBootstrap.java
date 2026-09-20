package com.portal26.hive.cognito;

import com.portal26.hive.config.CognitoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
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
		try {
			cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
					.userPoolId(properties.userPoolId())
					.username(email)
					.temporaryPassword(properties.bootstrapPassword())
					.messageAction(MessageActionType.SUPPRESS)
					.userAttributes(
							AttributeType.builder().name("email").value(email).build(),
							AttributeType.builder().name("email_verified").value("true").build())
					.build());
			log.info("Created Cognito user {}", email);
		}
		catch (UsernameExistsException ex) {
			log.info("Cognito user {} already exists — skipping create", email);
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
