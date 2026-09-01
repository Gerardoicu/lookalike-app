package com.gerardoicu.lookalike.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.gerardoicu.lookalike.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AnonymousAnalysisSecurityGate {

	private static final int MAX_TURNSTILE_TOKEN_LENGTH = 2_048;
	private static final String TIMEOUT_OR_DUPLICATE = "timeout-or-duplicate";

	private final AnonymousVisitorCookieService visitorCookieService;
	private final InMemoryFixedWindowRateLimiter rateLimiter;
	private final TurnstileVerifier turnstileVerifier;
	private final SecurityProperties properties;
	private final Clock clock;

	@Autowired
	public AnonymousAnalysisSecurityGate(
			AnonymousVisitorCookieService visitorCookieService,
			InMemoryFixedWindowRateLimiter rateLimiter,
			TurnstileVerifier turnstileVerifier,
			SecurityProperties properties
	) {
		this(visitorCookieService, rateLimiter, turnstileVerifier, properties, Clock.systemUTC());
	}

	AnonymousAnalysisSecurityGate(
			AnonymousVisitorCookieService visitorCookieService,
			InMemoryFixedWindowRateLimiter rateLimiter,
			TurnstileVerifier turnstileVerifier,
			SecurityProperties properties,
			Clock clock
	) {
		this.visitorCookieService = visitorCookieService;
		this.rateLimiter = rateLimiter;
		this.turnstileVerifier = turnstileVerifier;
		this.properties = properties;
		this.clock = clock;
	}

	public AnonymousAnalysisAuthorization authorizePreAnalysis(HttpServletRequest request, HttpServletResponse response, String turnstileToken) {
		validateRequiredSecurityConfiguration();
		VisitorCookieValidation cookieValidation = visitorCookieService.validate(request);
		if (cookieValidation.tampered()) {
			visitorCookieService.clearCookie(response);
			throw new SecurityException(
					ErrorCode.SECURITY_COOKIE_TAMPERED,
					HttpStatus.BAD_REQUEST,
					"Visitor cookie is invalid."
			);
		}

		AnonymousVisitorCookie visitorCookie = cookieValidation.visitorCookie()
			.orElseGet(visitorCookieService::newVisitorCookie);
		rateLimiter.check(rateLimitKey(request, cookieValidation, visitorCookie));
		checkCooldown(visitorCookie);
		validateTurnstileTokenShape(turnstileToken);

		TurnstileVerification verification = turnstileVerifier.verify(turnstileToken, request.getRemoteAddr());
		validateTurnstileVerification(verification);
		return new AnonymousAnalysisAuthorization(visitorCookie);
	}

	public void recordSuccessfulAnalysis(HttpServletResponse response, AnonymousAnalysisAuthorization authorization) {
		visitorCookieService.writeSuccessfulAnalysisCookie(response, authorization.visitorCookie());
	}

	private void checkCooldown(AnonymousVisitorCookie visitorCookie) {
		visitorCookie.lastSuccessfulAnalysisAt().ifPresent(lastSuccessfulAt -> {
			Instant availableAt = lastSuccessfulAt.plus(properties.visitorCookie().cooldown());
			Instant now = clock.instant();
			if (now.isBefore(availableAt)) {
				long retryAfterSeconds = Math.max(1, Duration.between(now, availableAt).toSeconds());
				throw new SecurityException(
						ErrorCode.SECURITY_COOLDOWN_ACTIVE,
						HttpStatus.TOO_MANY_REQUESTS,
						"Analysis cooldown is active.",
						retryAfterSeconds
				);
			}
		});
	}

	private void validateRequiredSecurityConfiguration() {
		if (properties.visitorCookie().signingSecret().isBlank()) {
			throw new SecurityException(
					ErrorCode.SECURITY_CONFIGURATION_UNAVAILABLE,
					HttpStatus.SERVICE_UNAVAILABLE,
					"Visitor cookie signing is not configured."
			);
		}
		if (properties.turnstile().expectedHostnames().isEmpty()) {
			throw new SecurityException(
					ErrorCode.SECURITY_CONFIGURATION_UNAVAILABLE,
					HttpStatus.SERVICE_UNAVAILABLE,
					"Turnstile expected hostname is not configured."
			);
		}
	}

	private void validateTurnstileTokenShape(String token) {
		if (token == null || token.isBlank()) {
			throw new SecurityException(
					ErrorCode.SECURITY_TURNSTILE_MISSING,
					HttpStatus.BAD_REQUEST,
					"Turnstile token is required."
			);
		}
		if (token.length() > MAX_TURNSTILE_TOKEN_LENGTH) {
			throw new SecurityException(
					ErrorCode.SECURITY_TURNSTILE_OVERSIZED,
					HttpStatus.BAD_REQUEST,
					"Turnstile token is too large."
			);
		}
	}

	private void validateTurnstileVerification(TurnstileVerification verification) {
		if (!verification.success()) {
			if (verification.errorCodes().contains(TIMEOUT_OR_DUPLICATE)) {
				throw new SecurityException(
						ErrorCode.SECURITY_TURNSTILE_REUSED_OR_EXPIRED,
						HttpStatus.BAD_REQUEST,
						"Turnstile token is expired or already used."
				);
			}
			throw new SecurityException(
					ErrorCode.SECURITY_TURNSTILE_INVALID,
					HttpStatus.BAD_REQUEST,
					"Turnstile token is invalid."
			);
		}
		if (!properties.turnstile().expectedHostnames().isEmpty()
				&& !properties.turnstile().expectedHostnames().contains(verification.hostname())) {
			throw new SecurityException(
					ErrorCode.SECURITY_TURNSTILE_UNEXPECTED_HOSTNAME,
					HttpStatus.BAD_REQUEST,
					"Turnstile token hostname is not allowed."
			);
		}
		if (!properties.turnstile().expectedAction().isBlank()
				&& !properties.turnstile().expectedAction().equals(verification.action())) {
			throw new SecurityException(
					ErrorCode.SECURITY_TURNSTILE_UNEXPECTED_ACTION,
					HttpStatus.BAD_REQUEST,
					"Turnstile token action is not allowed."
			);
		}
	}

	private static String rateLimitKey(HttpServletRequest request, VisitorCookieValidation cookieValidation, AnonymousVisitorCookie visitorCookie) {
		if (cookieValidation.visitorCookie().isPresent()) {
			return "visitor:" + visitorCookie.visitorId();
		}
		return "remote:" + request.getRemoteAddr();
	}
}
