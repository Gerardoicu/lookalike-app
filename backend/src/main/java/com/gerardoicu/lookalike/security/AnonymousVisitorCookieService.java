package com.gerardoicu.lookalike.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import com.gerardoicu.lookalike.api.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AnonymousVisitorCookieService {

	static final String COOKIE_NAME = "lookalike_visitor";
	private static final String VERSION = "v1";
	private static final String DELIMITER = ".";
	private static final String PAYLOAD_SEPARATOR = ":";
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final SecurityProperties properties;
	private final Clock clock;

	@Autowired
	public AnonymousVisitorCookieService(SecurityProperties properties) {
		this(properties, Clock.systemUTC());
	}

	AnonymousVisitorCookieService(SecurityProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	VisitorCookieValidation validate(HttpServletRequest request) {
		ensureSigningConfigured();
		return findCookieValue(request)
			.map(this::validate)
			.orElseGet(VisitorCookieValidation::missing);
	}

	AnonymousVisitorCookie newVisitorCookie() {
		return new AnonymousVisitorCookie(UUID.randomUUID(), Optional.empty());
	}

	void writeSuccessfulAnalysisCookie(HttpServletResponse response, AnonymousVisitorCookie existingCookie) {
		AnonymousVisitorCookie updatedCookie = new AnonymousVisitorCookie(existingCookie.visitorId(), Optional.of(clock.instant()));
		response.addHeader(HttpHeaders.SET_COOKIE, buildCookieHeader(encode(updatedCookie), properties.visitorCookie().maxAge()));
	}

	void clearCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, buildCookieHeader("", java.time.Duration.ZERO));
	}

	private VisitorCookieValidation validate(String cookieValue) {
		String[] parts = cookieValue.split("\\" + DELIMITER, -1);
		if (parts.length != 2) {
			return VisitorCookieValidation.invalidSignature();
		}
		byte[] expectedSignature = sign(parts[0]);
		byte[] actualSignature;
		try {
			actualSignature = DECODER.decode(parts[1]);
		}
		catch (IllegalArgumentException ex) {
			return VisitorCookieValidation.invalidSignature();
		}
		if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
			return VisitorCookieValidation.invalidSignature();
		}
		try {
			String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
			String[] payloadParts = payload.split(PAYLOAD_SEPARATOR, -1);
			if (payloadParts.length != 3 || !VERSION.equals(payloadParts[0])) {
				return VisitorCookieValidation.invalidSignature();
			}
			UUID visitorId = UUID.fromString(payloadParts[1]);
			Optional<Instant> lastSuccessfulAt = payloadParts[2].isBlank()
					? Optional.empty()
					: Optional.of(Instant.ofEpochMilli(Long.parseLong(payloadParts[2])));
			return VisitorCookieValidation.valid(new AnonymousVisitorCookie(visitorId, lastSuccessfulAt));
		}
		catch (RuntimeException ex) {
			return VisitorCookieValidation.invalidSignature();
		}
	}

	private String encode(AnonymousVisitorCookie visitorCookie) {
		ensureSigningConfigured();
		String lastSuccessfulAt = visitorCookie.lastSuccessfulAnalysisAt()
			.map(Instant::toEpochMilli)
			.map(String::valueOf)
			.orElse("");
		String payload = VERSION + PAYLOAD_SEPARATOR + visitorCookie.visitorId() + PAYLOAD_SEPARATOR + lastSuccessfulAt;
		String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		return encodedPayload + DELIMITER + ENCODER.encodeToString(sign(encodedPayload));
	}

	private byte[] sign(String encodedPayload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(properties.visitorCookie().signingSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to sign visitor cookie.", ex);
		}
	}

	private String buildCookieHeader(String value, java.time.Duration maxAge) {
		return ResponseCookie.from(COOKIE_NAME, value)
			.httpOnly(true)
			.secure(properties.visitorCookie().secure())
			.sameSite("Lax")
			.path("/")
			.maxAge(maxAge)
			.build()
			.toString();
	}

	private static Optional<String> findCookieValue(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
			.findFirst()
			.map(Cookie::getValue);
	}

	private void ensureSigningConfigured() {
		if (properties.visitorCookie().signingSecret().isBlank()) {
			throw new SecurityException(
					ErrorCode.SECURITY_CONFIGURATION_UNAVAILABLE,
					HttpStatus.SERVICE_UNAVAILABLE,
					"Visitor cookie signing is not configured."
			);
		}
	}
}
