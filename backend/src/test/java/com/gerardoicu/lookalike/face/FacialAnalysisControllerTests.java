package com.gerardoicu.lookalike.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gerardoicu.lookalike.api.ApiExceptionHandler;
import com.gerardoicu.lookalike.security.AnonymousAnalysisSecurityGate;
import com.gerardoicu.lookalike.security.AnonymousVisitorCookieService;
import com.gerardoicu.lookalike.security.InMemoryFixedWindowRateLimiter;
import com.gerardoicu.lookalike.security.RequestSizeLimitFilter;
import com.gerardoicu.lookalike.security.SecurityConfiguration;
import com.gerardoicu.lookalike.security.TurnstileVerification;
import com.gerardoicu.lookalike.security.TurnstileVerifier;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(FacialAnalysisController.class)
@Import({
		AnonymousAnalysisSecurityGate.class,
		AnonymousVisitorCookieService.class,
		ApiExceptionHandler.class,
		FacialAnalysisControllerTests.TestFaceConfiguration.class,
		InMemoryFixedWindowRateLimiter.class,
		RequestSizeLimitFilter.class,
		SecurityConfiguration.class
})
@TestPropertySource(properties = {
		"lookalike.security.visitor-cookie.signing-secret=test-signing-secret-with-enough-entropy",
		"lookalike.security.visitor-cookie.cooldown=10m",
		"lookalike.security.turnstile.expected-hostnames=localhost",
		"lookalike.security.rate-limit.capacity=10",
		"lookalike.security.rate-limit.window=1m",
		"lookalike.security.rate-limit.cleanup-interval=5m",
		"lookalike.security.request-limit.max-known-content-length-bytes=8388608"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FacialAnalysisControllerTests {

	private static final String ENDPOINT = "/api/v1/facial-analyses";
	private static final String VISITOR_COOKIE = "lookalike_visitor";

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void resetHarness() {
		TestFaceConfiguration.analysisCalls = 0;
		TestFaceConfiguration.failAnalysis = false;
	}

	@Test
	void validSingleImageReturnsSuccessAndRecordsCooldownCookie() throws Exception {
		mockMvc.perform(multipart(ENDPOINT)
				.file(jpegFile())
				.header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.successful").value(true))
			.andExpect(header().exists(HttpHeaders.SET_COOKIE))
			.andExpect(cookie().httpOnly(VISITOR_COOKIE, true));

		assertThat(TestFaceConfiguration.analysisCalls).isEqualTo(1);
	}

	@Test
	void missingImageIsRejectedBeforeAnalysis() throws Exception {
		mockMvc.perform(multipart(ENDPOINT).header("X-Turnstile-Token", "valid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FACE_IMAGE_MISSING"));

		assertThat(TestFaceConfiguration.analysisCalls).isZero();
	}

	@Test
	void multipleImageFilesAreRejectedBeforeAnalysis() throws Exception {
		mockMvc.perform(multipart(ENDPOINT)
				.file(jpegFile())
				.file(new MockMultipartFile("image", "other.jpg", "image/jpeg", ImageTestData.jpeg(16, 16)))
				.header("X-Turnstile-Token", "valid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FACE_IMAGE_MULTIPLE_FILES"));

		assertThat(TestFaceConfiguration.analysisCalls).isZero();
	}

	@Test
	void failedAnalysisDoesNotRecordCooldownCookie() throws Exception {
		TestFaceConfiguration.failAnalysis = true;

		mockMvc.perform(multipart(ENDPOINT)
				.file(jpegFile())
				.header("X-Turnstile-Token", "valid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FACE_NO_USABLE_FACE"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void activeCooldownRejectsBeforeAnalysis() throws Exception {
		MvcResult first = mockMvc.perform(multipart(ENDPOINT)
				.file(jpegFile())
				.header("X-Turnstile-Token", "valid"))
			.andExpect(status().isOk())
			.andReturn();
		String visitorCookie = first.getResponse().getCookie(VISITOR_COOKIE).getValue();
		TestFaceConfiguration.analysisCalls = 0;

		mockMvc.perform(multipart(ENDPOINT)
				.file(jpegFile())
				.cookie(new Cookie(VISITOR_COOKIE, visitorCookie))
				.header("X-Turnstile-Token", "valid"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SECURITY_COOLDOWN_ACTIVE"))
			.andExpect(jsonPath("$.retryAfterSeconds").isNumber());

		assertThat(TestFaceConfiguration.analysisCalls).isZero();
	}

	private static MockMultipartFile jpegFile() {
		return new MockMultipartFile("image", "face.jpg", "image/jpeg", ImageTestData.jpeg(32, 32));
	}

	@TestConfiguration
	static class TestFaceConfiguration {

		static int analysisCalls;
		static boolean failAnalysis;

		@Bean
		FacialAnalysisService facialAnalysisService() {
			return new FacialAnalysisService(new UploadedImageValidator(new FaceAnalysisProperties(1_024 * 1_024, 1_024, 1_024, 1_048_576, 320, 0.9f, "")), image -> {
				analysisCalls++;
				if (failAnalysis) {
					throw new FaceAnalysisException(com.gerardoicu.lookalike.api.ErrorCode.FACE_NO_USABLE_FACE, org.springframework.http.HttpStatus.BAD_REQUEST, "No usable face was detected.");
				}
				return new FacialEmbedding(new float[128]);
			});
		}

		@Bean
		TurnstileVerifier turnstileVerifier() {
			return (token, remoteAddress) -> new TurnstileVerification(true, "localhost", "", List.of());
		}
	}
}
