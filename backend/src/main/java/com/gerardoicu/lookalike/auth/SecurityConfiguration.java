package com.gerardoicu.lookalike.auth;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

	private static final String ADMIN_CHECK_ENDPOINT = "/api/v1/auth/admin-check";

	private static final String HMAC_SHA_256_JCA_ALGORITHM = "HmacSHA256";

	private static final String JWT_SECRET_ENVIRONMENT_VARIABLE = "APP_AUTH_JWT_SECRET";

	private static final int MINIMUM_JWT_SECRET_BYTES = 32;

	private final AuthProperties authProperties;

	SecurityConfiguration(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfTokenRepository())
				.requireCsrfProtectionMatcher(authenticationPostMatcher())
				.spa()
			)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers("/api/v1/health").permitAll()
				.requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
				.requestMatchers(ADMIN_CHECK_ENDPOINT).hasRole(ApplicationRole.ADMIN.name())
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
			.build();
	}

	private CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookiePath("/");
		repository.setCookieCustomizer(builder -> builder
			.secure(authProperties.xsrfCookie().secure())
			.sameSite("Strict"));
		return repository;
	}

	private RequestMatcher authenticationPostMatcher() {
		return request -> HttpMethod.POST.matches(request.getMethod())
			&& List.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
				.contains(request.getRequestURI());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtEncoder jwtEncoder(AuthProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(AuthProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecret(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(jwtValidator(properties));
		return decoder;
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(rolesAuthoritiesConverter());
		return converter;
	}

	private Converter<Jwt, Collection<GrantedAuthority>> rolesAuthoritiesConverter() {
		return jwt -> {
			Object rolesClaim = jwt.getClaims().get(JwtTokenService.ROLES_CLAIM);
			if (!(rolesClaim instanceof List<?> roles) || roles.isEmpty()) {
				throw new BadJwtException("Invalid roles claim.");
			}
			return roles.stream()
				.map(this::authorityFromRoleClaim)
				.toList();
		};
	}

	private GrantedAuthority authorityFromRoleClaim(Object role) {
		if (!(role instanceof String roleName)) {
			throw new BadJwtException("Invalid roles claim.");
		}
		try {
			return new SimpleGrantedAuthority(ApplicationRole.valueOf(roleName).authority());
		}
		catch (IllegalArgumentException ex) {
			throw new BadJwtException("Invalid roles claim.", ex);
		}
	}

	private OAuth2TokenValidator<Jwt> jwtValidator(AuthProperties properties) {
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(properties.clockSkew());
		return token -> {
			OAuth2TokenValidatorResult defaults = JwtValidators.createDefaultWithIssuer(properties.jwtIssuer()).validate(token);
			if (defaults.hasErrors()) {
				return defaults;
			}
			OAuth2TokenValidatorResult timestamp = timestampValidator.validate(token);
			if (timestamp.hasErrors()) {
				return timestamp;
			}
			OAuth2TokenValidatorResult audience = audienceValidator(properties.jwtAudience()).validate(token);
			if (audience.hasErrors()) {
				return audience;
			}
			OAuth2TokenValidatorResult roles = rolesValidator().validate(token);
			if (roles.hasErrors()) {
				return roles;
			}
			OAuth2TokenValidatorResult subject = subjectValidator().validate(token);
			if (subject.hasErrors()) {
				return subject;
			}
			if (token.getId() == null || token.getId().isBlank()) {
				return OAuth2TokenValidatorResult.failure(invalidToken("Missing jti."));
			}
			return OAuth2TokenValidatorResult.success();
		};
	}

	private OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
		return token -> token.getAudience().contains(expectedAudience)
			? OAuth2TokenValidatorResult.success()
			: OAuth2TokenValidatorResult.failure(invalidToken("Invalid audience."));
	}

	private OAuth2TokenValidator<Jwt> rolesValidator() {
		return token -> {
			Object rolesClaim = token.getClaims().get(JwtTokenService.ROLES_CLAIM);
			if (!(rolesClaim instanceof List<?> roles) || roles.isEmpty()) {
				return OAuth2TokenValidatorResult.failure(invalidToken("Invalid roles claim."));
			}
			for (Object role : roles) {
				if (!(role instanceof String roleName)) {
					return OAuth2TokenValidatorResult.failure(invalidToken("Invalid roles claim."));
				}
				try {
					ApplicationRole.valueOf(roleName);
				}
				catch (IllegalArgumentException ex) {
					return OAuth2TokenValidatorResult.failure(invalidToken("Invalid roles claim."));
				}
			}
			return OAuth2TokenValidatorResult.success();
		};
	}

	private OAuth2TokenValidator<Jwt> subjectValidator() {
		return token -> {
			Object subjectClaim = token.getClaims().get(JwtClaimNames.SUB);
			if (!(subjectClaim instanceof String subject) || subject.isBlank()) {
				return OAuth2TokenValidatorResult.failure(invalidToken("Invalid subject."));
			}
			try {
				UUID.fromString(subject);
			}
			catch (IllegalArgumentException ex) {
				return OAuth2TokenValidatorResult.failure(invalidToken("Invalid subject."));
			}
			return OAuth2TokenValidatorResult.success();
		};
	}

	private OAuth2Error invalidToken(String description) {
		return new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null);
	}

	private SecretKey jwtSecret(AuthProperties properties) {
		byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MINIMUM_JWT_SECRET_BYTES) {
			throw new IllegalStateException(
				JWT_SECRET_ENVIRONMENT_VARIABLE + " must be at least " + MINIMUM_JWT_SECRET_BYTES + " UTF-8 bytes."
			);
		}
		return new SecretKeySpec(secret, HMAC_SHA_256_JCA_ALGORITHM);
	}
}
