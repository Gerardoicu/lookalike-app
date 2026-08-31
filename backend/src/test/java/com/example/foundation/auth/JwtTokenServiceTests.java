package com.example.foundation.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTests {

	@Test
	void issuedTokenContainsRequiredContractClaims() {
		AuthProperties properties = properties("test-secret-that-is-not-for-real-use");
		SecurityConfiguration security = new SecurityConfiguration(properties);
		JwtTokenService service = new JwtTokenService(security.jwtEncoder(properties), properties);
		UserAccount user = new UserAccount("user@example.com", "hash", Set.of(ApplicationRole.USER));
		ReflectionTestUtils.setField(user, "id", java.util.UUID.fromString("8e31c835-756d-4609-a6d2-bf20d1505be3"));

		JwtTokenService.IssuedAccessToken token = service.issue(user);

		var jwt = security.jwtDecoder(properties).decode(token.value());
		assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo("foundation-backend");
		assertThat(jwt.getAudience()).containsExactly("foundation-api");
		assertThat(jwt.getSubject()).isNotBlank();
		assertThat(jwt.getIssuedAt()).isNotNull();
		assertThat(jwt.getExpiresAt()).isNotNull();
		assertThat(jwt.getExpiresAt().getEpochSecond()).isEqualTo(token.expiresAt().getEpochSecond());
		assertThat(jwt.getId()).isNotBlank();
		assertThat(jwt.getClaimAsStringList(JwtTokenService.ROLES_CLAIM)).containsExactly("USER");
	}

	@Test
	void decoderRejectsWrongSignatureIssuerAudienceExpirationAndUnknownRoles() {
		AuthProperties properties = properties("test-secret-that-is-not-for-real-use");
		SecurityConfiguration security = new SecurityConfiguration(properties);
		String validClaimsToken = token(security.jwtEncoder(properties), claims(properties, List.of(ApplicationRole.USER.name()), Instant.now().plus(Duration.ofMinutes(15))));

		assertThatThrownBy(() -> security.jwtDecoder(properties("abcdefghijklmnopqrstuvwxyz123456")).decode(validClaimsToken))
			.isInstanceOf(BadJwtException.class);
		assertThatThrownBy(() -> security.jwtDecoder(properties).decode(token(security.jwtEncoder(properties), claimsWithIssuer(properties, "other"))))
			.isInstanceOf(JwtValidationException.class);
		assertThatThrownBy(() -> security.jwtDecoder(properties).decode(token(security.jwtEncoder(properties), claimsWithAudience(properties, "other"))))
			.isInstanceOf(JwtValidationException.class);
		assertThatThrownBy(() -> security.jwtDecoder(properties).decode(token(security.jwtEncoder(properties), expiredClaims(properties))))
			.isInstanceOf(JwtValidationException.class);
		assertThatThrownBy(() -> security.jwtDecoder(properties).decode(token(security.jwtEncoder(properties), claims(properties, List.of("UNKNOWN"), Instant.now().plus(Duration.ofMinutes(15))))))
			.isInstanceOf(JwtValidationException.class);
	}

	@Test
	void decoderAcceptsValidUuidSubject() {
		AuthProperties properties = properties("test-secret-that-is-not-for-real-use");
		SecurityConfiguration security = new SecurityConfiguration(properties);
		String token = token(security.jwtEncoder(properties), claims(properties, List.of(ApplicationRole.USER.name()), Instant.now().plus(Duration.ofMinutes(15))));

		assertThat(security.jwtDecoder(properties).decode(token).getSubject())
			.isEqualTo("8e31c835-756d-4609-a6d2-bf20d1505be3");
	}

	@Test
	void decoderRejectsMissingBlankMalformedAndTypedSubjects() {
		AuthProperties properties = properties("test-secret-that-is-not-for-real-use");
		SecurityConfiguration security = new SecurityConfiguration(properties);
		JwtEncoder encoder = security.jwtEncoder(properties);
		JwtDecoder decoder = security.jwtDecoder(properties);

		assertThatThrownBy(() -> decoder.decode(token(encoder, claimsWithSubject(properties, null))))
			.isInstanceOf(JwtValidationException.class);
		assertThatThrownBy(() -> decoder.decode(token(encoder, claimsWithSubject(properties, ""))))
			.isInstanceOf(JwtValidationException.class);
		assertThatThrownBy(() -> decoder.decode(token(encoder, claimsWithSubject(properties, "not-a-uuid"))))
			.isInstanceOf(JwtValidationException.class);
		assertThatThrownBy(() -> decoder.decode(token(encoder, claimsWithTypedSubject(properties))))
			.isInstanceOf(JwtValidationException.class);
	}

	private AuthProperties properties(String secret) {
		AuthProperties properties = new AuthProperties();
		properties.setJwtSecret(secret);
		return properties;
	}

	private String token(JwtEncoder encoder, JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private JwtClaimsSet claims(AuthProperties properties, List<String> roles, Instant expiresAt) {
		return JwtClaimsSet.builder()
			.issuer(properties.jwtIssuer())
			.audience(List.of(properties.jwtAudience()))
			.subject("8e31c835-756d-4609-a6d2-bf20d1505be3")
			.id("token-id")
			.issuedAt(Instant.now())
			.expiresAt(expiresAt)
			.claim(JwtTokenService.ROLES_CLAIM, roles)
			.build();
	}

	private JwtClaimsSet claimsWithSubject(AuthProperties properties, String subject) {
		JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
			.issuer(properties.jwtIssuer())
			.audience(List.of(properties.jwtAudience()))
			.id("token-id")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
			.claim(JwtTokenService.ROLES_CLAIM, List.of(ApplicationRole.USER.name()));
		if (subject != null) {
			builder.subject(subject);
		}
		return builder.build();
	}

	private JwtClaimsSet claimsWithTypedSubject(AuthProperties properties) {
		return JwtClaimsSet.builder()
			.issuer(properties.jwtIssuer())
			.audience(List.of(properties.jwtAudience()))
			.id("token-id")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
			.claim(JwtClaimNames.SUB, 123)
			.claim(JwtTokenService.ROLES_CLAIM, List.of(ApplicationRole.USER.name()))
			.build();
	}

	private JwtClaimsSet claimsWithIssuer(AuthProperties properties, String issuer) {
		return JwtClaimsSet.builder()
			.issuer(issuer)
			.audience(List.of(properties.jwtAudience()))
			.subject("8e31c835-756d-4609-a6d2-bf20d1505be3")
			.id("token-id")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
			.claim(JwtTokenService.ROLES_CLAIM, List.of(ApplicationRole.USER.name()))
			.build();
	}

	private JwtClaimsSet claimsWithAudience(AuthProperties properties, String audience) {
		return JwtClaimsSet.builder()
			.issuer(properties.jwtIssuer())
			.audience(List.of(audience))
			.subject("8e31c835-756d-4609-a6d2-bf20d1505be3")
			.id("token-id")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
			.claim(JwtTokenService.ROLES_CLAIM, List.of(ApplicationRole.USER.name()))
			.build();
	}

	private JwtClaimsSet expiredClaims(AuthProperties properties) {
		return JwtClaimsSet.builder()
			.issuer(properties.jwtIssuer())
			.audience(List.of(properties.jwtAudience()))
			.subject("8e31c835-756d-4609-a6d2-bf20d1505be3")
			.id("token-id")
			.issuedAt(Instant.now().minus(Duration.ofHours(1)))
			.expiresAt(Instant.now().minus(Duration.ofMinutes(5)))
			.claim(JwtTokenService.ROLES_CLAIM, List.of(ApplicationRole.USER.name()))
			.build();
	}
}
