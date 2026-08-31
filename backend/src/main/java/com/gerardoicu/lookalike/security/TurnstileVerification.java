package com.gerardoicu.lookalike.security;

import java.util.List;

public record TurnstileVerification(boolean success, String hostname, String action, List<String> errorCodes) {

	public TurnstileVerification {
		hostname = hostname == null ? "" : hostname;
		action = action == null ? "" : action;
		errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
	}
}
