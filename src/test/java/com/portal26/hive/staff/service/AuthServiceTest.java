package com.portal26.hive.staff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portal26.hive.cognito.CognitoAuthClient;
import com.portal26.hive.cognito.CognitoAuthResult;
import com.portal26.hive.config.SessionProperties;
import com.portal26.hive.session.HiveSession;
import com.portal26.hive.session.SessionStore;
import com.portal26.hive.staff.dto.AuthUserResponse;
import com.portal26.hive.staff.dto.LoginRequest;
import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.enums.HiveRole;
import com.portal26.hive.staff.repository.StaffRepository;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private CognitoAuthClient cognitoAuthClient;
	@Mock
	private StaffRepository staffRepository;
	@Mock
	private SessionStore sessionStore;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		RoleResolver roleResolver = new StaffDbRoleResolver();
		SessionProperties sessionProperties = new SessionProperties("HIVE_SESSION", Duration.ofDays(7), false);
		authService = new AuthService(cognitoAuthClient, staffRepository, roleResolver, sessionStore, sessionProperties);
	}

	@Test
	void loginCreatesSessionAndSetsCookie() {
		UUID staffId = UUID.randomUUID();
		Staff staff = new Staff();
		staff.setId(staffId);
		staff.setEmail("admin@portal26.ai");
		staff.setRole(HiveRole.MSP_HIVE_ADMIN);

		when(cognitoAuthClient.authenticate("admin@portal26.ai", "Admin@123"))
				.thenReturn(new CognitoAuthResult(
						"id-token",
						"access-token",
						"refresh-token",
						"admin@portal26.ai",
						Instant.now().plusSeconds(900)));
		when(staffRepository.findByEmailIgnoreCase("admin@portal26.ai")).thenReturn(Optional.of(staff));

		MockHttpServletResponse response = new MockHttpServletResponse();
		AuthUserResponse body = authService.login(new LoginRequest("admin@portal26.ai", "Admin@123"), response);

		assertThat(body.email()).isEqualTo("admin@portal26.ai");
		assertThat(body.role()).isEqualTo(HiveRole.MSP_HIVE_ADMIN);
		assertThat(response.getHeader("Set-Cookie")).contains("HIVE_SESSION=");

		ArgumentCaptor<HiveSession> sessionCaptor = ArgumentCaptor.forClass(HiveSession.class);
		verify(sessionStore).save(any(String.class), sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().refreshToken()).isEqualTo("refresh-token");
		assertThat(sessionCaptor.getValue().role()).isEqualTo(HiveRole.MSP_HIVE_ADMIN);
	}
}
