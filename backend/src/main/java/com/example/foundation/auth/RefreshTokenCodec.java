package com.example.foundation.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
class RefreshTokenCodec {

	private static final int TOKEN_BYTES = 32;

	private static final String REFRESH_TOKEN_HASH_ALGORITHM = "SHA-256";

	private final SecureRandom secureRandom = new SecureRandom();

	String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance(REFRESH_TOKEN_HASH_ALGORITHM);
			byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(REFRESH_TOKEN_HASH_ALGORITHM + " is required for refresh-token hashing.", ex);
		}
	}
}
