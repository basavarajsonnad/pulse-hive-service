package com.portal26.hive.cognito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal26.hive.config.CognitoProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
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
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;

@Component
@RequiredArgsConstructor
public class CognitoAuthClient {

	private final CognitoIdentityProviderClient cognitoClient;
	private final CognitoProperties properties;
	private final OAuthStateStore oauthStateStore;
	private final ObjectMapper objectMapper;
	private final AtomicReference<JwtDecoder> jwtDecoder = new AtomicReference<>();
	private final HttpClient httpClient = HttpClient.newHttpClient();

	/**
	 * Builds Cognito Hosted UI authorize URL and stores PKCE verifier keyed by state.
	 */
	public String buildAuthorizeUrl() {
		String state = UUID.randomUUID().toString();
		String codeVerifier = generateCodeVerifier();
		String codeChallenge = codeChallengeS256(codeVerifier);
		oauthStateStore.save(state, codeVerifier);

		StringBuilder url = new StringBuilder(trimTrailingSlash(properties.domain()))
				.append("/oauth2/authorize")
				.append("?client_id=").append(encode(properties.clientId()))
				.append("&response_type=code")
				.append("&scope=").append(encode("openid email"))
				.append("&redirect_uri=").append(encode(properties.redirectUri()))
				.append("&state=").append(encode(state))
				.append("&code_challenge_method=S256")
				.append("&code_challenge=").append(encode(codeChallenge));
		return url.toString();
	}

	public String buildLogoutUrl() {
		return trimTrailingSlash(properties.domain())
				+ "/logout"
				+ "?client_id=" + encode(properties.clientId())
				+ "&logout_uri=" + encode(properties.logoutUri());
	}

	public CognitoAuthResult exchangeAuthorizationCode(String code, String state) {
		String codeVerifier = oauthStateStore.consume(state)
				.orElseThrow(() -> new CognitoAuthenticationException("Invalid or expired OAuth state"));

		try {
			String body = "grant_type=authorization_code"
					+ "&client_id=" + encode(properties.clientId())
					+ "&code=" + encode(code)
					+ "&redirect_uri=" + encode(properties.redirectUri())
					+ "&code_verifier=" + encode(codeVerifier);
			if (StringUtils.hasText(properties.clientSecret())) {
				body += "&client_secret=" + encode(properties.clientSecret());
			}

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(trimTrailingSlash(properties.domain()) + "/oauth2/token"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body));

			HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new CognitoAuthenticationException(
						"Cognito token exchange failed: HTTP " + response.statusCode() + " body=" + response.body());
			}

			JsonNode json = objectMapper.readTree(response.body());
			if (json.hasNonNull("error")) {
				throw new CognitoAuthenticationException(
						"Cognito token exchange failed: " + text(json, "error")
								+ " — " + text(json, "error_description"));
			}
			String idToken = text(json, "id_token");
			String accessToken = text(json, "access_token");
			String refreshToken = text(json, "refresh_token");
			if (!StringUtils.hasText(idToken) || !StringUtils.hasText(accessToken)) {
				throw new CognitoAuthenticationException("Cognito did not return tokens");
			}

			Jwt jwt = validateIdToken(idToken);
			assertTokenUseId(jwt);
			String email = jwt.getClaimAsString("email");
			if (!StringUtils.hasText(email)) {
				throw new CognitoAuthenticationException("Cognito id token missing email claim");
			}
			String provider = jwt.getClaimAsString("custom:provider");
			Instant expiresAt = jwt.getExpiresAt() != null ? jwt.getExpiresAt() : Instant.now().plusSeconds(900);
			return new CognitoAuthResult(idToken, accessToken, refreshToken, email, provider, expiresAt);
		}
		catch (CognitoAuthenticationException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new CognitoAuthenticationException("Cognito authorization code exchange failed", ex);
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
			String provider = jwt.getClaimAsString("custom:provider");
			Instant expiresAt = jwt.getExpiresAt() != null ? jwt.getExpiresAt() : Instant.now().plusSeconds(900);
			return new CognitoAuthResult(
					result.idToken(),
					result.accessToken(),
					nextRefresh,
					tokenEmail,
					provider,
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

	private static String generateCodeVerifier() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String codeChallengeS256(String codeVerifier) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (Exception ex) {
			throw new CognitoAuthenticationException("Failed to compute PKCE code challenge", ex);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			throw new CognitoAuthenticationException("Cognito domain is not configured");
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String text(JsonNode json, String field) {
		JsonNode node = json.get(field);
		return node == null || node.isNull() ? null : node.asText();
	}
}
