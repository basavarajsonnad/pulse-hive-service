package com.portal26.hive.staff.controller;

import com.portal26.hive.config.SessionProperties;
import com.portal26.hive.staff.dto.AuthUserResponse;
import com.portal26.hive.staff.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final SessionProperties sessionProperties;

	@GetMapping("/login")
	public void login(HttpServletResponse response) throws IOException {
		response.sendRedirect(authService.beginLogin());
	}

	@GetMapping("/callback")
	public void callback(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			@RequestParam(name = "error_description", required = false) String errorDescription,
			HttpServletResponse response) throws IOException {
		String redirectUrl = authService.handleCallback(code, state, error, errorDescription, response);
		response.sendRedirect(redirectUrl);
	}

	@PostMapping("/logout")
	@PreAuthorize("isAuthenticated()")
	public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String logoutUrl = authService.logout(readSessionId(request), response);
		response.sendRedirect(logoutUrl);
	}

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public AuthUserResponse me() {
		return authService.me();
	}

	private String readSessionId(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (sessionProperties.cookieName().equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
