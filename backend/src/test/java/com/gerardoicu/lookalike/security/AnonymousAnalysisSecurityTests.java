package com.gerardoicu.lookalike.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gerardoicu.lookalike.api.ApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(SecurityHarnessController.class)
@Import({
		AnonymousAnalysisSecurityGate.class,
		AnonymousVisitorCookieService.class,
		ApiExceptionHandler.class,
		InMemoryFixedWindowRateLimiter.class,
		RequestSizeLimitFilter.class,
		SecurityConfiguration.class,
		AnonymousAnalysisSecurityTests.TestSecurityConfiguration.class
})
@TestPropertySource(properties = {
		"lookalike.security.visitor-cookie.signing-secret=test-signing-secret-with-enough-entropy",
		"lookalike.security.visitor-cookie.cooldown=10m",
		"lookalike.security.visitor-cookie.max-age=30d",
		"lookalike.security.turnstile.expected-hostnames=localhost",
		"lookalike.security.turnstile.expected-action=analysis",
		"lookalike.security.rate-limit.capacity=2",
		"lookalike.security.rate-limit.window=1m",
		"lookalike.security.rate-limit.cleanup-interval=5m",
		"lookalike.security.request-limit.max-known-content-length-bytes=16"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AnonymousAnalysisSecurityTests {

	private static final String ENDPOINT = "/api/v1/security-test/analysis";

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void resetHarness() {
		SecurityHarnessController.inferenceCalls = 0;
	}

	@Test
	void successfulAuthorizationRecordsVisitorCookieAfterCompletion() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk())
			.andExpect(header().exists(HttpHeaders.SET_COOKIE))
			.andExpect(cookie().httpOnly(AnonymousVisitorCookieService.COOKIE_NAME, true));

		assertThat(SecurityHarnessController.inferenceCalls).isEqualTo(1);
	}

	@Test
	void passingTurnstileWithoutSuccessfulCompletionDoesNotStartCooldown() throws Exception {
		MvcResult incomplete = mockMvc.perform(post(ENDPOINT).param("complete", "false").header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(incomplete.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk());

		assertThat(SecurityHarnessController.inferenceCalls).isEqualTo(2);
	}

	@Test
	void missingTurnstileTokenIsRejectedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SECURITY_TURNSTILE_MISSING"));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void oversizedTurnstileTokenIsRejectedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "a".repeat(2_049)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SECURITY_TURNSTILE_OVERSIZED"));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void invalidTurnstileTokenIsRejectedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "invalid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SECURITY_TURNSTILE_INVALID"));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void duplicateOrExpiredTurnstileTokenIsRejectedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "duplicate"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SECURITY_TURNSTILE_REUSED_OR_EXPIRED"));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void unexpectedTurnstileHostnameIsRejectedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "wrong-host"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SECURITY_TURNSTILE_UNEXPECTED_HOSTNAME"));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void unexpectedTurnstileActionIsRejectedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "wrong-action"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SECURITY_TURNSTILE_UNEXPECTED_ACTION"));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void activeCooldownReturnsRetryDurationBeforeInference() throws Exception {
		MvcResult first = mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk())
			.andReturn();

		String visitorCookie = extractVisitorCookie(first);
		SecurityHarnessController.inferenceCalls = 0;

		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "valid").cookie(new Cookie(AnonymousVisitorCookieService.COOKIE_NAME, visitorCookie)))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SECURITY_COOLDOWN_ACTIVE"))
			.andExpect(jsonPath("$.retryAfterSeconds").isNumber());

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void tamperedCookieIsRejectedAndClearedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "valid").cookie(new Cookie(AnonymousVisitorCookieService.COOKIE_NAME, "tampered")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SECURITY_COOKIE_TAMPERED"))
			.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Max-Age=0"))));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void requestBurstIsRateLimitedBeforeInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).param("complete", "false").header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk());
		mockMvc.perform(post(ENDPOINT).param("complete", "false").header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk());

		SecurityHarnessController.inferenceCalls = 0;

		mockMvc.perform(post(ENDPOINT).param("complete", "false").header("X-Turnstile-Token", "valid"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SECURITY_RATE_LIMITED"))
			.andExpect(jsonPath("$.retryAfterSeconds").isNumber());

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@Test
	void knownOversizedContentLengthIsRejectedWithoutInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).content("01234567890123456").header("X-Turnstile-Token", "valid"))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("SECURITY_REQUEST_TOO_LARGE"));

		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	private static String extractVisitorCookie(MvcResult result) {
		return result.getResponse().getCookie(AnonymousVisitorCookieService.COOKIE_NAME).getValue();
	}

	@TestConfiguration
	static class TestSecurityConfiguration {

		@Bean
		TurnstileVerifier turnstileVerifier() {
			return (token, remoteAddress) -> switch (token) {
				case "valid" -> new TurnstileVerification(true, "localhost", "analysis", List.of());
				case "duplicate" -> new TurnstileVerification(false, "", "", List.of("timeout-or-duplicate"));
				case "wrong-host" -> new TurnstileVerification(true, "example.com", "analysis", List.of());
				case "wrong-action" -> new TurnstileVerification(true, "localhost", "other", List.of());
				default -> new TurnstileVerification(false, "", "", List.of("invalid-input-response"));
			};
		}
	}
}
