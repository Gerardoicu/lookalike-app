package com.example.foundation.auth;

public enum ApplicationRole {
	USER,
	ADMIN;

	public String authority() {
		return "ROLE_" + name();
	}
}
