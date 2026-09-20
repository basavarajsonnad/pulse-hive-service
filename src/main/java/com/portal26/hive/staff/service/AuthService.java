package com.portal26.hive.staff.service;

import com.portal26.hive.cognito.CognitoAuthClient;
import com.portal26.hive.cognito.CognitoAuthResult;
import com.portal26.hive.cognito.CognitoAuthenticationException;
import com.portal26.hive.config.SessionProperties;
import com.portal26.hive.session.HiveSession;
import com.portal26.hive.session.SessionStore;
import com.portal26.hive.staff.dto.AuthUserResponse;
import com.portal26.hive.staff.dto.LoginRequest;
import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.enums.HiveRole;
import com.portal26.hive.staff.principal.HivePrincipal;
import com.portal26.hive.staff.repository.StaffRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final CognitoAuthClient cognitoAuthClient;
	private final StaffRepository staffRepository;
	private final RoleResolver roleResolver;
	private final SessionStore sessionStore;
	private final SessionProperties sessionProperties;

	@Transactional(readOnly = true)
	public AuthUserResponse login(LoginRequest request, HttpServletResponse response) {
		CognitoAuthResult cognitoResult;
		try {
			cognitoResult = cognitoAuthClient.authenticate(request.email().trim(), request.password());
		}
		catch (CognitoAuthenticationException ex) {
			log.warn("Cognito authentication failed for {}: {}", request.email(), ex.getMessage(), ex);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password", ex);
		}

		if (!request.email().trim().equalsIgnoreCase(cognitoResult.email())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}

		Staff staff = staffRepository.findByEmailIgnoreCase(request.email().trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

		HiveRole role = roleResolver.resolve(staff);
		Instant now = Instant.now();
		String sessionId = UUID.randomUUID().toString();
		HiveSession session = new HiveSession(
				staff.getId(),
				staff.getEmail(),
				role,
				cognitoResult.accessToken(),
				cognitoResult.idToken(),
				cognitoResult.refreshToken(),
				cognitoResult.accessTokenExpiresAt(),
				now,
				now.plus(sessionProperties.ttl()));

		sessionStore.save(sessionId, session);
		writeSessionCookie(response, sessionId);
		return new AuthUserResponse(staff.getId(), staff.getEmail(), role);
	}

	public void logout(String sessionId, HttpServletResponse response) {
		if (sessionId != null && !sessionId.isBlank()) {
			sessionStore.delete(sessionId);
		}
		clearSessionCookie(response);
		SecurityContextHolder.clearContext();
	}

	public AuthUserResponse me() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof HivePrincipal principal)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
		}
		return new AuthUserResponse(principal.getStaffId(), principal.getEmail(), principal.getRole());
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
