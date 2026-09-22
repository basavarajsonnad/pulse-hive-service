package com.portal26.hive.staff.service;

import com.portal26.hive.cognito.CognitoAuthClient;
import com.portal26.hive.cognito.CognitoAuthResult;
import com.portal26.hive.cognito.CognitoAuthenticationException;
import com.portal26.hive.config.CognitoProperties;
import com.portal26.hive.config.SessionProperties;
import com.portal26.hive.msp.entity.Msp;
import com.portal26.hive.msp.repository.MspRepository;
import com.portal26.hive.session.HiveSession;
import com.portal26.hive.session.SessionStore;
import com.portal26.hive.staff.dto.AuthUserResponse;
import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.principal.HivePrincipal;
import com.portal26.hive.staff.repository.StaffRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final CognitoAuthClient cognitoAuthClient;
	private final StaffRepository staffRepository;
	private final MspRepository mspRepository;
	private final SessionStore sessionStore;
	private final SessionProperties sessionProperties;
	private final CognitoProperties cognitoProperties;
	private final UUID seedMspId;

	public AuthService(
			CognitoAuthClient cognitoAuthClient,
			StaffRepository staffRepository,
			MspRepository mspRepository,
			SessionStore sessionStore,
			SessionProperties sessionProperties,
			CognitoProperties cognitoProperties,
			@Value("${hive.seed-msp-id}") UUID seedMspId) {
		this.cognitoAuthClient = cognitoAuthClient;
		this.staffRepository = staffRepository;
		this.mspRepository = mspRepository;
		this.sessionStore = sessionStore;
		this.sessionProperties = sessionProperties;
		this.cognitoProperties = cognitoProperties;
		this.seedMspId = seedMspId;
	}

	public String beginLogin() {
		return cognitoAuthClient.buildAuthorizeUrl();
	}

	@Transactional
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

		Staff staff = findOrCreateStaff(cognitoResult.email());
		UUID mspId = resolveMspId(cognitoResult.provider());

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

	/**
	 * Cognito is the source of identity; Hive only stores a local staff row for FK/session.
	 * First login creates the row; later logins reuse it. No Hive-side role validation.
	 */
	private Staff findOrCreateStaff(String email) {
		return staffRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
			Staff created = new Staff();
			created.setEmail(email);
			created.setRole(null);
			try {
				Staff saved = staffRepository.save(created);
				log.info("Created staff record on first login for {}", email);
				return saved;
			}
			catch (DataIntegrityViolationException ex) {
				return staffRepository.findByEmailIgnoreCase(email)
						.orElseThrow(() -> ex);
			}
		});
	}

	/**
	 * Reads custom:provider from the ID token. If present, find-or-create an MSP row by name.
	 * If absent, fall back to the seeded MSP id (local/dev).
	 */
	private UUID resolveMspId(String provider) {
		if (!StringUtils.hasText(provider)) {
			return seedMspId;
		}
		String name = provider.trim();
		return mspRepository.findByNameIgnoreCase(name)
				.map(Msp::getId)
				.orElseGet(() -> {
					try {
						Msp saved = mspRepository.save(Msp.forProviderCreate(name));
						log.info("Created MSP '{}' from Cognito custom:provider on first login", name);
						return saved.getId();
					}
					catch (DataIntegrityViolationException ex) {
						return mspRepository.findByNameIgnoreCase(name)
								.map(Msp::getId)
								.orElseThrow(() -> ex);
					}
				});
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

	private void writeSessionCookie(HttpServletResponse response, String sessionId) {
		ResponseCookie cookie = ResponseCookie.from(sessionProperties.cookieName(), sessionId)
				.httpOnly(true)
				.secure(sessionProperties.cookieSecure())
				.sameSite("Lax")
				.path("/")
				.maxAge(sessionProperties.ttl())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private void clearSessionCookie(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(sessionProperties.cookieName(), "")
				.httpOnly(true)
				.secure(sessionProperties.cookieSecure())
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
