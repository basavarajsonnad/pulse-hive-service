package com.portal26.hive.staff.service;

import com.portal26.hive.cognito.CognitoAuthClient;
import com.portal26.hive.cognito.CognitoAuthResult;
import com.portal26.hive.cognito.CognitoAuthenticationException;
import com.portal26.hive.config.CognitoProperties;
import com.portal26.hive.config.SessionProperties;
import com.portal26.hive.session.HiveSession;
import com.portal26.hive.session.SessionStore;
import com.portal26.hive.staff.dto.AuthUserResponse;
import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.principal.HivePrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final CognitoAuthClient cognitoAuthClient;
	private final LoginIdentityService loginIdentityService;
	private final SessionStore sessionStore;
	private final SessionProperties sessionProperties;
	private final CognitoProperties cognitoProperties;

	public AuthService(
			CognitoAuthClient cognitoAuthClient,
			LoginIdentityService loginIdentityService,
			SessionStore sessionStore,
			SessionProperties sessionProperties,
			CognitoProperties cognitoProperties) {
		this.cognitoAuthClient = cognitoAuthClient;
		this.loginIdentityService = loginIdentityService;
		this.sessionStore = sessionStore;
		this.sessionProperties = sessionProperties;
		this.cognitoProperties = cognitoProperties;
	}

	public String beginLogin() {
		return cognitoAuthClient.buildAuthorizeUrl();
	}

	public String handleCallback(String code, String state, String error, String errorDescription,
			HttpServletResponse response) {
		if (StringUtils.hasText(error)) {
			log.warn("Cognito callback error: {} — {}", error, errorDescription);
			return frontendErrorRedirect("cognito_" + error);
		}
		if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
			return frontendErrorRedirect("missing_code");
		}

		CognitoAuthResult cognitoResult;
		try {
			cognitoResult = cognitoAuthClient.exchangeAuthorizationCode(code, state);
		}
		catch (CognitoAuthenticationException ex) {
			log.warn("Cognito code exchange failed: {}", ex.getMessage(), ex);
			String detail = ex.getMessage() != null && ex.getMessage().contains("Invalid or expired OAuth state")
					? "invalid_state"
					: "exchange_failed";
			return frontendErrorRedirect(detail);
		}

		try {
			Staff staff = loginIdentityService.findOrCreateStaff(cognitoResult.email());
			UUID mspId = loginIdentityService.resolveMspId(cognitoResult.provider());

			Instant now = Instant.now();
			String sessionId = UUID.randomUUID().toString();
			HiveSession session = new HiveSession(
					staff.getId(),
					mspId,
					staff.getEmail(),
					staff.getRole(),
					cognitoResult.accessToken(),
					cognitoResult.idToken(),
					cognitoResult.refreshToken(),
					cognitoResult.accessTokenExpiresAt(),
					now,
					now.plus(sessionProperties.ttl()));

			sessionStore.save(sessionId, session);
			writeSessionCookie(response, sessionId);
			return cognitoProperties.frontendSuccessUrl();
		}
		catch (RuntimeException ex) {
			log.warn("Login persistence/session failed after Cognito exchange: {}", ex.getMessage(), ex);
			return frontendErrorRedirect("login_failed");
		}
	}

	public String logout(String sessionId, HttpServletResponse response) {
		if (sessionId != null && !sessionId.isBlank()) {
			sessionStore.delete(sessionId);
		}
		clearSessionCookie(response);
		SecurityContextHolder.clearContext();
		return cognitoAuthClient.buildLogoutUrl();
	}

	public AuthUserResponse me() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof HivePrincipal principal)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
		}
		return new AuthUserResponse(principal.getStaffId(), principal.getEmail(), principal.getRole());
	}

	private String frontendErrorRedirect(String errorCode) {
		String base = cognitoProperties.frontendErrorUrl();
		String separator = base.contains("?") ? "&" : "?";
		return base + separator + "error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
	}

	/** SameSite=Lax is correct when UI and API share a host (or UI proxies the callback). */
	private void writeSessionCookie(HttpServletResponse response, String sessionId) {
		response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(sessionId, sessionProperties.ttl()).toString());
	}

	private void clearSessionCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", java.time.Duration.ZERO).toString());
	}

	private ResponseCookie sessionCookie(String value, java.time.Duration maxAge) {
		return ResponseCookie.from(sessionProperties.cookieName(), value)
				.httpOnly(true)
				.secure(true)
				.sameSite("None")
				.path("/")
				.maxAge(maxAge)
				.build();
	}
}
