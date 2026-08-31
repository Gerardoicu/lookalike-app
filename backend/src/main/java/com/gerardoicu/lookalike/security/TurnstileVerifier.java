package com.gerardoicu.lookalike.security;

public interface TurnstileVerifier {

	TurnstileVerification verify(String token, String remoteAddress);
}
