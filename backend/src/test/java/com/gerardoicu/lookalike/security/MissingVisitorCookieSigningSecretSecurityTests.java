package com.gerardoicu.lookalike.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gerardoicu.lookalike.api.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SecurityHarnessController.class)
@Import({
		AnonymousAnalysisSecurityGate.class,
		AnonymousVisitorCookieService.class,
		ApiExceptionHandler.class,
		InMemoryFixedWindowRateLimiter.class,
		RequestSizeLimitFilter.class,
		SecurityConfiguration.class,
		MissingVisitorCookieSigningSecretSecurityTests.TestSecurityConfiguration.class
})
@TestPropertySource(properties = {
		"lookalike.security.visitor-cookie.signing-secret=",
		"lookalike.security.turnstile.expected-hostnames=localhost",
		"lookalike.security.rate-limit.capacity=2",
		"lookalike.security.rate-limit.window=1m",
		"lookalike.security.rate-limit.cleanup-interval=5m"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MissingVisitorCookieSigningSecretSecurityTests {

	private static final String ENDPOINT = "/api/v1/security-test/analysis";

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void resetHarness() {
		SecurityHarnessController.inferenceCalls = 0;
		TestSecurityConfiguration.turnstileCalls = 0;
	}

	@Test
	void missingVisitorCookieSigningSecretRejectsBeforeTurnstileAndInference() throws Exception {
		mockMvc.perform(post(ENDPOINT).header("X-Turnstile-Token", "valid"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SECURITY_CONFIGURATION_UNAVAILABLE"));

		assertThat(TestSecurityConfiguration.turnstileCalls).isZero();
		assertThat(SecurityHarnessController.inferenceCalls).isZero();
	}

	@TestConfiguration
	static class TestSecurityConfiguration {

		static int turnstileCalls;

		@Bean
		TurnstileVerifier turnstileVerifier() {
			return (token, remoteAddress) -> {
				turnstileCalls++;
				return new TurnstileVerification(true, "localhost", "", List.of());
			};
		}
	}
}
