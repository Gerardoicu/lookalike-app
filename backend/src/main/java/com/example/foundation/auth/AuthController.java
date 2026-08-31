package com.example.foundation.auth;

import java.security.Principal;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

	private final AuthService authService;

	private final UserAccountRepository userAccounts;

	private final RefreshCookieService refreshCookies;

	AuthController(AuthService authService, UserAccountRepository userAccounts, RefreshCookieService refreshCookies) {
		this.authService = authService;
		this.userAccounts = userAccounts;
		this.refreshCookies = refreshCookies;
	}

	@PostMapping("/api/v1/auth/login")
	AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
		AuthService.LoginResult result = authService.login(request);
		refreshCookies.write(response, result.refreshToken(), result.familyExpiresAt(), result.issuedAt());
		return result.response();
	}

	@PostMapping("/api/v1/auth/refresh")
	AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
		String refreshToken = refreshCookies.read(request);
		AuthService.RefreshResult result = authService.refresh(refreshToken);
		if (result.clearCookie()) {
			refreshCookies.clear(response);
		}
		result.cookie().ifPresent(cookie -> refreshCookies.write(response, cookie.refreshToken(), cookie.familyExpiresAt(), cookie.issuedAt()));
		return result.response()
			.orElseThrow(() -> new BadCredentialsException("Authentication failed."));
	}

	@PostMapping("/api/v1/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(refreshCookies.read(request));
		refreshCookies.clear(response);
	}

	@GetMapping("/api/v1/auth/me")
	AuthenticatedUserResponse me(Principal principal) {
		UUID userId = UUID.fromString(principal.getName());
		return userAccounts.findById(userId)
			.filter(UserAccount::enabled)
			.map(AuthenticatedUserResponse::from)
			.orElseThrow(() -> new BadCredentialsException("Authentication failed."));
	}

	@GetMapping("/api/v1/auth/admin-check")
	void adminCheck() {
	}
}
