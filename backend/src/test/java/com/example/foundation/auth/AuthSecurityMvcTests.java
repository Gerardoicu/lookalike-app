package com.example.foundation.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.when;

import com.example.foundation.health.HealthController;

@WebMvcTest({ HealthController.class, AuthController.class, CsrfController.class })
@Import({ SecurityConfiguration.class, AuthExceptionHandler.class, RefreshCookieService.class })
@TestPropertySource(properties = "app.auth.jwt-secret=test-secret-that-is-not-for-real-use")
class AuthSecurityMvcTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private UserAccountRepository userAccounts;

	@Autowired
	private AuthProperties authProperties;

	@Test
	void healthAllowsAnonymousAccess() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"healthy":true}
				"""));
	}

	@Test
	void protectedEndpointReturnsUnauthorizedWithoutToken() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void directErrorRequestRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/error"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void errorDispatchIsPermittedWithoutReplacingOriginalStatusWithUnauthorized() throws Exception {
		mockMvc.perform(get("/error").with(request -> {
				request.setDispatcherType(DispatcherType.ERROR);
				return request;
			}))
			.andExpect(status().isInternalServerError());
	}

	@Test
	void roleRestrictedEndpointReturnsForbiddenForInsufficientRole() throws Exception {
		mockMvc.perform(get("/api/v1/auth/admin-check")
				.with(jwt().authorities(new SimpleGrantedAuthority(ApplicationRole.USER.authority()))))
			.andExpect(status().isForbidden());
	}

	@Test
	void roleRestrictedEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/auth/admin-check"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void roleRestrictedEndpointAllowsAdminRole() throws Exception {
		mockMvc.perform(get("/api/v1/auth/admin-check")
				.with(jwt().authorities(new SimpleGrantedAuthority(ApplicationRole.ADMIN.authority()))))
			.andExpect(status().isOk());
	}

	@Test
	void csrfEndpointMaterializesReadableCookie() throws Exception {
		mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"));
	}

	@Test
	void authenticationPostRejectsMissingCsrf() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"user@example.com","password":"secret"}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void authenticationPostRejectsInvalidCsrf() throws Exception {
		Cookie xsrf = xsrfCookie();

		mockMvc.perform(post("/api/v1/auth/login")
				.cookie(xsrf)
				.header("X-XSRF-TOKEN", "invalid")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"user@example.com","password":"secret"}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void loginWithRealXsrfCookieHeaderExchangeReturnsAccessTokenAndRefreshCookie() throws Exception {
		AuthResponse response = new AuthResponse(
			"access",
			Instant.parse("2026-08-01T00:15:00Z"),
			new AuthenticatedUserResponse(
				UUID.fromString("8e31c835-756d-4609-a6d2-bf20d1505be3"),
				"user@example.com",
				Set.of(ApplicationRole.USER)
			)
		);
		when(authService.login(new LoginRequest("user@example.com", "secret")))
			.thenReturn(new AuthService.LoginResult(
				response,
				"refresh",
				Instant.parse("2026-08-08T00:00:00Z"),
				Instant.parse("2026-08-01T00:00:00Z")
			));
		Cookie xsrf = xsrfCookie();

		mockMvc.perform(post("/api/v1/auth/login")
				.cookie(xsrf)
				.header("X-XSRF-TOKEN", decodedValue(xsrf))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"user@example.com","password":"secret"}
					"""))
			.andExpect(status().isOk())
			.andExpect(cookie().exists(authProperties.refreshCookie().name()))
			.andExpect(content().json("""
				{
					"accessToken":"access",
					"accessTokenExpiresAt":"2026-08-01T00:15:00Z",
					"user":{"email":"user@example.com","roles":["USER"]}
				}
				"""));
	}

	@Test
	void loginWithInvalidCredentialsReturnsGenericUnauthorizedWithValidCsrf() throws Exception {
		when(authService.login(new LoginRequest("user@example.com", "wrong")))
			.thenThrow(new BadCredentialsException("Authentication failed."));
		Cookie xsrf = xsrfCookie();

		mockMvc.perform(post("/api/v1/auth/login")
				.cookie(xsrf)
				.header("X-XSRF-TOKEN", decodedValue(xsrf))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"user@example.com","password":"wrong"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
				{"message":"Authentication failed."}
				"""));
	}

	@Test
	void logoutRequiresCsrfBeforeReturningNoContent() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
				.cookie(new Cookie(authProperties.refreshCookie().name(), "refresh")))
			.andExpect(status().isForbidden());
		Cookie xsrf = xsrfCookie();
		mockMvc.perform(post("/api/v1/auth/logout")
				.cookie(xsrf)
				.header("X-XSRF-TOKEN", decodedValue(xsrf))
				.cookie(new Cookie(authProperties.refreshCookie().name(), "refresh")))
			.andExpect(status().isNoContent());
	}

	private Cookie xsrfCookie() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andReturn();
		return result.getResponse().getCookie("XSRF-TOKEN");
	}

	private String decodedValue(Cookie cookie) {
		return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
	}
}
