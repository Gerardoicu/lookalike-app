package com.example.foundation.auth;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class RefreshCookieService {

	private static final String PATH = "/";

	private final AuthProperties authProperties;

	RefreshCookieService(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		String cookieName = authProperties.refreshCookie().name();
		for (Cookie cookie : cookies) {
			if (cookieName.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	void write(HttpServletResponse response, String refreshToken, Instant familyExpiresAt, Instant now) {
		long maxAgeSeconds = Math.max(0L, Duration.between(now, familyExpiresAt).toSeconds());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, Duration.ofSeconds(maxAgeSeconds)).toString());
	}

	void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
	}

	private ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(authProperties.refreshCookie().name(), value)
			.httpOnly(true)
			.secure(authProperties.refreshCookie().secure())
			.sameSite("Strict")
			.path(PATH)
			.maxAge(maxAge)
			.build();
	}
}
