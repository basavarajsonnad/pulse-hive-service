package com.portal26.hive.staff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.cognito.CognitoAuthClient;
import com.portal26.hive.cognito.CognitoAuthResult;
import com.portal26.hive.config.CognitoProperties;
import com.portal26.hive.config.SessionProperties;
import com.portal26.hive.msp.entity.Msp;
import com.portal26.hive.msp.repository.MspRepository;
import com.portal26.hive.session.HiveSession;
import com.portal26.hive.session.SessionStore;
import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.repository.StaffRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private CognitoAuthClient cognitoAuthClient;
	@Mock
	private StaffRepository staffRepository;
	@Mock
	private MspRepository mspRepository;
	@Mock
	private SessionStore sessionStore;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		SessionProperties sessionProperties = new SessionProperties("HIVE_SESSION", Duration.ofDays(7), false);
		CognitoProperties cognitoProperties = new CognitoProperties(
				"ap-south-1",
				"test-pool",
				"test-client",
				"test-secret",
				"https://cognito-idp.ap-south-1.amazonaws.com/test-pool",
				"https://test.auth.ap-south-1.amazoncognito.com",
				"http://localhost:8080/api/v1/auth/callback",
				"http://localhost:3000",
				"http://localhost:3000",
				"http://localhost:3000",
				false,
				"admin@portal26.ai",
				"Admin@123",
				"CinchIT");
		LoginIdentityService loginIdentityService = new LoginIdentityService(staffRepository, mspRepository);
		authService = new AuthService(
				cognitoAuthClient,
				loginIdentityService,
				sessionStore,
				sessionProperties,
				cognitoProperties);
	}

	@Test
	void beginLoginReturnsAuthorizeUrl() {
		when(cognitoAuthClient.buildAuthorizeUrl())
				.thenReturn("https://test.auth.ap-south-1.amazoncognito.com/oauth2/authorize?...");
		assertThat(authService.beginLogin()).contains("oauth2/authorize");
	}

	@Test
	void callbackReusesExistingStaffAndMspFromProviderClaim() {
		UUID staffId = UUID.randomUUID();
		Staff staff = new Staff();
		staff.setId(staffId);
		staff.setEmail("admin@portal26.ai");

		Msp msp = Msp.forProviderCreate("CinchIT");

		when(cognitoAuthClient.exchangeAuthorizationCode(eq("auth-code"), eq("oauth-state")))
				.thenReturn(new CognitoAuthResult(
						"id-token",
						"access-token",
						"refresh-token",
						"admin@portal26.ai",
						"CinchIT",
						Instant.now().plusSeconds(900)));
		when(staffRepository.findByEmailIgnoreCase("admin@portal26.ai")).thenReturn(Optional.of(staff));
		when(mspRepository.findByNameIgnoreCase("CinchIT")).thenReturn(Optional.of(msp));

		MockHttpServletResponse response = new MockHttpServletResponse();
		String redirect = authService.handleCallback("auth-code", "oauth-state", null, null, response);

		assertThat(redirect).isEqualTo("http://localhost:3000");
		assertThat(response.getHeader("Set-Cookie")).contains("HIVE_SESSION=");
		verify(staffRepository, never()).saveAndFlush(any());
		verify(mspRepository, never()).saveAndFlush(any());

		ArgumentCaptor<HiveSession> sessionCaptor = ArgumentCaptor.forClass(HiveSession.class);
		verify(sessionStore).save(any(String.class), sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().refreshToken()).isEqualTo("refresh-token");
		assertThat(sessionCaptor.getValue().staffId()).isEqualTo(staffId);
		assertThat(sessionCaptor.getValue().mspId()).isEqualTo(msp.getId());
	}

	@Test
	void callbackCreatesStaffOnFirstLogin() {
		Msp msp = Msp.forProviderCreate("CinchIT");

		when(cognitoAuthClient.exchangeAuthorizationCode(eq("auth-code"), eq("oauth-state")))
				.thenReturn(new CognitoAuthResult(
						"id-token",
						"access-token",
						"refresh-token",
						"newuser@portal26.ai",
						"CinchIT",
						Instant.now().plusSeconds(900)));
		when(staffRepository.findByEmailIgnoreCase("newuser@portal26.ai")).thenReturn(Optional.empty());
		when(staffRepository.saveAndFlush(any(Staff.class))).thenAnswer(invocation -> {
			Staff s = invocation.getArgument(0);
			if (s.getId() == null) {
				s.setId(UUID.randomUUID());
			}
			return s;
		});
		when(mspRepository.findByNameIgnoreCase("CinchIT")).thenReturn(Optional.of(msp));

		MockHttpServletResponse response = new MockHttpServletResponse();
		String redirect = authService.handleCallback("auth-code", "oauth-state", null, null, response);

		assertThat(redirect).isEqualTo("http://localhost:3000");
		assertThat(response.getHeader("Set-Cookie")).contains("HIVE_SESSION=");

		ArgumentCaptor<Staff> staffCaptor = ArgumentCaptor.forClass(Staff.class);
		verify(staffRepository).saveAndFlush(staffCaptor.capture());
		assertThat(staffCaptor.getValue().getEmail()).isEqualTo("newuser@portal26.ai");
		assertThat(staffCaptor.getValue().getRole()).isNull();

		ArgumentCaptor<HiveSession> sessionCaptor = ArgumentCaptor.forClass(HiveSession.class);
		verify(sessionStore).save(any(String.class), sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().mspId()).isEqualTo(msp.getId());
	}

	@Test
	void callbackRedirectsWhenProviderClaimMissing() {
		when(cognitoAuthClient.exchangeAuthorizationCode(eq("auth-code"), eq("oauth-state")))
				.thenReturn(new CognitoAuthResult(
						"id-token",
						"access-token",
						"refresh-token",
						"newuser@portal26.ai",
						null,
						Instant.now().plusSeconds(900)));
		when(staffRepository.findByEmailIgnoreCase("newuser@portal26.ai")).thenReturn(Optional.empty());
		when(staffRepository.saveAndFlush(any(Staff.class))).thenAnswer(invocation -> {
			Staff s = invocation.getArgument(0);
			if (s.getId() == null) {
				s.setId(UUID.randomUUID());
			}
			return s;
		});

		MockHttpServletResponse response = new MockHttpServletResponse();
		String redirect = authService.handleCallback("auth-code", "oauth-state", null, null, response);

		assertThat(redirect).isEqualTo("http://localhost:3000?error=login_failed");
		assertThat(response.getHeader("Set-Cookie")).isNull();
		verify(sessionStore, never()).save(any(), any());
	}

	@Test
	void callbackRedirectsToFrontendOnPersistenceFailure() {
		when(cognitoAuthClient.exchangeAuthorizationCode(eq("auth-code"), eq("oauth-state")))
				.thenReturn(new CognitoAuthResult(
						"id-token",
						"access-token",
						"refresh-token",
						"newuser@portal26.ai",
						"CinchIT",
						Instant.now().plusSeconds(900)));
		when(staffRepository.findByEmailIgnoreCase("newuser@portal26.ai")).thenReturn(Optional.empty());
		when(staffRepository.saveAndFlush(any(Staff.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		MockHttpServletResponse response = new MockHttpServletResponse();
		String redirect = authService.handleCallback("auth-code", "oauth-state", null, null, response);

		assertThat(redirect).isEqualTo("http://localhost:3000?error=login_failed");
		assertThat(response.getHeader("Set-Cookie")).isNull();
		verify(sessionStore, never()).save(any(), any());
	}

	@Test
	void logoutClearsSessionAndReturnsCognitoLogoutUrl() {
		when(cognitoAuthClient.buildLogoutUrl())
				.thenReturn("https://test.auth.ap-south-1.amazoncognito.com/logout?...");

		MockHttpServletResponse response = new MockHttpServletResponse();
		String redirect = authService.logout("session-1", response);

		verify(sessionStore).delete("session-1");
		assertThat(redirect).contains("/logout");
		assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
	}
}
