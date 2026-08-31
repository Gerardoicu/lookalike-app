package com.gerardoicu.lookalike.auth;

import java.time.Instant;

public record AuthResponse(
	String accessToken,
	Instant accessTokenExpiresAt,
	AuthenticatedUserResponse user
) {
}
