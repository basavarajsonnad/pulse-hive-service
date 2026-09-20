package com.portal26.hive.staff.controller;

import com.portal26.hive.config.SessionProperties;
import com.portal26.hive.staff.dto.AuthUserResponse;
import com.portal26.hive.staff.dto.LoginRequest;
import com.portal26.hive.staff.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final SessionProperties sessionProperties;

	@PostMapping("/login")
	public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
		return authService.login(request, response);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("isAuthenticated()")
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(readSessionId(request), response);
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
