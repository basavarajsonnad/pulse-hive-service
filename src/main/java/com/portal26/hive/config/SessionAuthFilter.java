package com.portal26.hive.config;

import com.portal26.hive.cognito.CognitoAuthClient;
import com.portal26.hive.cognito.CognitoAuthResult;
import com.portal26.hive.cognito.CognitoAuthenticationException;
import com.portal26.hive.session.HiveSession;
import com.portal26.hive.session.SessionStore;
import com.portal26.hive.staff.principal.HivePrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registered only via {@link SecurityConfig} so it is not also auto-registered as a
 * servlet {@code Filter} through component scanning.
 */
public class SessionAuthFilter extends OncePerRequestFilter {

	private final SessionStore sessionStore;
	private final SessionProperties sessionProperties;
	private final CognitoAuthClient cognitoAuthClient;

	public SessionAuthFilter(
			SessionStore sessionStore,
			SessionProperties sessionProperties,
			CognitoAuthClient cognitoAuthClient) {
		this.sessionStore = sessionStore;
		this.sessionProperties = sessionProperties;
		this.cognitoAuthClient = cognitoAuthClient;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			readSessionId(request).flatMap(this::loadAndMaybeRefresh).ifPresent(session -> authenticate(session));
		}
		filterChain.doFilter(request, response);
	}

	private Optional<HiveSession> loadAndMaybeRefresh(String sessionId) {
		Optional<HiveSession> found = sessionStore.find(sessionId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		HiveSession session = found.get();
		if (session.expiresAt() != null && session.expiresAt().isBefore(Instant.now())) {
			sessionStore.delete(sessionId);
			return Optional.empty();
		}
		if (session.accessTokenExpiresAt() != null && session.accessTokenExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
			return Optional.of(session);
		}
		try {
			CognitoAuthResult refreshed = cognitoAuthClient.refresh(session.refreshToken(), session.email());
			HiveSession updated = new HiveSession(
					session.staffId(),
					session.mspId(),
					session.email(),
					session.role(),
					refreshed.accessToken(),
					refreshed.idToken(),
					refreshed.refreshToken(),
					refreshed.accessTokenExpiresAt(),
					session.createdAt(),
					session.expiresAt());
			sessionStore.save(sessionId, updated);
			return Optional.of(updated);
		}
		catch (CognitoAuthenticationException ex) {
			sessionStore.delete(sessionId);
			return Optional.empty();
		}
	}

	private void authenticate(HiveSession session) {
		HivePrincipal principal = new HivePrincipal(session);
		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private Optional<String> readSessionId(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (sessionProperties.cookieName().equals(cookie.getName())
					&& cookie.getValue() != null
					&& !cookie.getValue().isBlank()) {
				return Optional.of(cookie.getValue());
			}
		}
		return Optional.empty();
	}
}
