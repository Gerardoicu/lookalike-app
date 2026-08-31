package com.gerardoicu.lookalike.auth;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUserResponse(UUID id, String email, Set<ApplicationRole> roles) {

	static AuthenticatedUserResponse from(UserAccount user) {
		return new AuthenticatedUserResponse(user.id(), user.email(), user.roles());
	}
}
