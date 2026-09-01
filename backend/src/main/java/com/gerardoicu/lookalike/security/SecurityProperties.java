package com.gerardoicu.lookalike.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lookalike.security")
public record SecurityProperties(
		Turnstile turnstile,
		VisitorCookie visitorCookie,
		RateLimit rateLimit,
		RequestLimit requestLimit,
		Siteverify siteverify
) {

	public SecurityProperties {
		turnstile = turnstile == null ? new Turnstile("", List.of(), "") : turnstile;
		visitorCookie = visitorCookie == null ? new VisitorCookie("", false, Duration.ofDays(30), Duration.ofMinutes(10)) : visitorCookie;
		rateLimit = rateLimit == null ? new RateLimit(20, Duration.ofMinutes(1), Duration.ofMinutes(5)) : rateLimit;
		requestLimit = requestLimit == null ? new RequestLimit(2_097_152L) : requestLimit;
		siteverify = siteverify == null ? new Siteverify(Duration.ofSeconds(2), Duration.ofSeconds(5)) : siteverify;
	}

	public record Turnstile(String secretKey, List<String> expectedHostnames, String expectedAction) {

		public Turnstile {
			expectedHostnames = expectedHostnames == null ? List.of() : expectedHostnames.stream()
				.filter(hostname -> hostname != null && !hostname.isBlank())
				.toList();
			secretKey = secretKey == null ? "" : secretKey;
			expectedAction = expectedAction == null ? "" : expectedAction;
		}
	}

	public record VisitorCookie(String signingSecret, boolean secure, Duration maxAge, Duration cooldown) {

		public VisitorCookie {
			signingSecret = signingSecret == null ? "" : signingSecret;
			maxAge = maxAge == null ? Duration.ofDays(30) : maxAge;
			cooldown = cooldown == null ? Duration.ofMinutes(10) : cooldown;
		}
	}

	public record RateLimit(int capacity, Duration window, Duration cleanupInterval) {

		public RateLimit {
			window = window == null ? Duration.ofMinutes(1) : window;
			cleanupInterval = cleanupInterval == null ? Duration.ofMinutes(5) : cleanupInterval;
		}
	}

	public record RequestLimit(long maxKnownContentLengthBytes) {
	}

	public record Siteverify(Duration connectTimeout, Duration readTimeout) {

		public Siteverify {
			connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
			readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
		}
	}
}
