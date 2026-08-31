package com.example.foundation.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

@Service
class JwtTokenService {

	static final String ROLES_CLAIM = "roles";

	private final JwtEncoder jwtEncoder;

	private final AuthProperties authProperties;

	JwtTokenService(JwtEncoder jwtEncoder, AuthProperties authProperties) {
		this.jwtEncoder = jwtEncoder;
		this.authProperties = authProperties;
	}

	IssuedAccessToken issue(UserAccount user) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(authProperties.accessTokenTtl());
		List<String> roles = user.roles().stream().map(ApplicationRole::name).sorted().toList();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(authProperties.jwtIssuer())
			.audience(List.of(authProperties.jwtAudience()))
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.subject(user.id().toString())
			.claim(ROLES_CLAIM, roles)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedAccessToken(token, expiresAt);
	}

	record IssuedAccessToken(String value, Instant expiresAt) {
	}
}
