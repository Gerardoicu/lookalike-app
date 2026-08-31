package com.gerardoicu.lookalike.security;

import java.util.Optional;

record VisitorCookieValidation(Optional<AnonymousVisitorCookie> visitorCookie, boolean tampered) {

	static VisitorCookieValidation missing() {
		return new VisitorCookieValidation(Optional.empty(), false);
	}

	static VisitorCookieValidation valid(AnonymousVisitorCookie visitorCookie) {
		return new VisitorCookieValidation(Optional.of(visitorCookie), false);
	}

	static VisitorCookieValidation invalidSignature() {
		return new VisitorCookieValidation(Optional.empty(), true);
	}
}
