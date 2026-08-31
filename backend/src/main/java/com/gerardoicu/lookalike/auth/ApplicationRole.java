package com.gerardoicu.lookalike.auth;

public enum ApplicationRole {
	USER,
	ADMIN;

	public String authority() {
		return "ROLE_" + name();
	}
}
