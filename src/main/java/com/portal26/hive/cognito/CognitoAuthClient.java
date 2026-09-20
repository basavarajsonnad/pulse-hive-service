package com.portal26.hive.cognito;

import com.portal26.hive.config.CognitoProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@Component
@RequiredArgsConstructor
public class CognitoAuthClient {

	private final CognitoIdentityProviderClient cognitoClient;
	private final CognitoProperties properties;
	private final AtomicReference<JwtDecoder> jwtDecoder = new AtomicReference<>();

	public CognitoAuthResult authenticate(String email, String password) {
		Map<String, String> authParams = new HashMap<>();
		authParams.put("USERNAME", email);
		authParams.put("PASSWORD", password);
		addSecretHash(authParams, email);

		try {
			// App client is configured for ADMIN_USER_PASSWORD_AUTH (server-side IAM).
			AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
					.userPoolId(properties.userPoolId())
					.clientId(properties.clientId())
					.authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
					.authParameters(authParams)
					.build());
			return toResult(response.authenticationResult(), email);
		}
		catch (NotAuthorizedException | UserNotFoundException ex) {
			throw new CognitoAuthenticationException("Invalid email or password", ex);
		}
		catch (Exception ex) {
			throw new CognitoAuthenticationException("Cognito authentication failed", ex);
		}
	}

	public CognitoAuthResult refresh(String refreshToken, String email) {
		Map<String, String> authParams = new HashMap<>();
		authParams.put("REFRESH_TOKEN", refreshToken);
		addSecretHash(authParams, email);

		try {
			InitiateAuthResponse response = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
					.authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
					.clientId(properties.clientId())
					.authParameters(authParams)
					.build());
			AuthenticationResultType result = response.authenticationResult();
			String nextRefresh = StringUtils.hasText(result.refreshToken()) ? result.refreshToken() : refreshToken;
			Jwt jwt = validateIdToken(result.idToken());
			assertTokenUseId(jwt);
			String tokenEmail = jwt.getClaimAsString("email");
			if (!StringUtils.hasText(tokenEmail)) {
				tokenEmail = email;
			}
			Instant expiresAt = jwt.getExpiresAt() != null ? jwt.getExpiresAt() : Instant.now().plusSeconds(900);
			return new CognitoAuthResult(
					result.idToken(),
					result.accessToken(),
					nextRefresh,
					tokenEmail,
					expiresAt);
		}
		catch (Exception ex) {
			throw new CognitoAuthenticationException("Cognito token refresh failed", ex);
		}
	}

	public Jwt validateIdToken(String idToken) {
		try {
			return jwtDecoder().decode(idToken);
		}
		catch (Exception ex) {
			throw new CognitoAuthenticationException("Invalid Cognito id token", ex);
		}
	}

	private CognitoAuthResult toResult(AuthenticationResultType result, String fallbackEmail) {
		if (result == null || !StringUtils.hasText(result.idToken()) || !StringUtils.hasText(result.accessToken())) {
			throw new CognitoAuthenticationException("Cognito did not return tokens");
		}
		Jwt jwt = validateIdToken(result.idToken());
		assertTokenUseId(jwt);
		String email = jwt.getClaimAsString("email");
		if (!StringUtils.hasText(email)) {
			email = fallbackEmail;
		}
		Instant expiresAt = jwt.getExpiresAt() != null ? jwt.getExpiresAt() : Instant.now().plusSeconds(900);
		return new CognitoAuthResult(
				result.idToken(),
				result.accessToken(),
				result.refreshToken(),
				email,
				expiresAt);
	}

	private void assertTokenUseId(Jwt jwt) {
		String tokenUse = jwt.getClaimAsString("token_use");
		if (!"id".equals(tokenUse)) {
			throw new CognitoAuthenticationException("Expected id token, got token_use=" + tokenUse);
		}
	}

	private void addSecretHash(Map<String, String> authParams, String username) {
		if (!StringUtils.hasText(properties.clientSecret())) {
			return;
		}
		authParams.put("SECRET_HASH", secretHash(username));
	}

	private String secretHash(String username) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] raw = mac.doFinal((username + properties.clientId()).getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(raw);
		}
		catch (Exception ex) {
			throw new CognitoAuthenticationException("Failed to compute Cognito SECRET_HASH", ex);
		}
	}

	private JwtDecoder jwtDecoder() {
		JwtDecoder existing = jwtDecoder.get();
		if (existing != null) {
			return existing;
		}
		NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuerUri());
		OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(properties.issuerUri());
		OAuth2TokenValidator<Jwt> audience = jwt -> {
			if (jwt.getAudience() != null && jwt.getAudience().contains(properties.clientId())) {
				return OAuth2TokenValidatorResult.success();
			}
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error("invalid_token", "Invalid audience", null));
		};
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, audience));
		jwtDecoder.compareAndSet(null, decoder);
		return jwtDecoder.get();
	}
}
