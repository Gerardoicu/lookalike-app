package com.gerardoicu.lookalike.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTests {

	private final UserAccountRepository userAccounts = org.mockito.Mockito.mock(UserAccountRepository.class);

	private final RefreshSessionRepository refreshSessions = org.mockito.Mockito.mock(RefreshSessionRepository.class);

	private final RefreshTokenFamilyRepository refreshTokenFamilies = org.mockito.Mockito.mock(RefreshTokenFamilyRepository.class);

	private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);

	private final JwtTokenService jwtTokenService = org.mockito.Mockito.mock(JwtTokenService.class);

	private final RefreshTokenCodec refreshTokenCodec = org.mockito.Mockito.mock(RefreshTokenCodec.class);

	private final AuthProperties authProperties = new AuthProperties();

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authProperties.setRefreshFamilyTtl(Duration.ofDays(7));
		when(refreshTokenFamilies.save(any(RefreshTokenFamily.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		authService = new AuthService(
			userAccounts,
			refreshSessions,
			refreshTokenFamilies,
			passwordEncoder,
			jwtTokenService,
			refreshTokenCodec,
			authProperties
		);
	}

	@Test
	void validCredentialsCreateAccessTokenAndHashedRefreshSession() {
		UserAccount user = new UserAccount("User@Example.com", "hash", Set.of(ApplicationRole.USER));
		Instant accessExpiresAt = Instant.now().plus(Duration.ofMinutes(15));
		when(userAccounts.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
		when(jwtTokenService.issue(user)).thenReturn(new JwtTokenService.IssuedAccessToken("access", accessExpiresAt));
		when(refreshTokenCodec.generate()).thenReturn("refresh");
		when(refreshTokenCodec.hash("refresh")).thenReturn("refresh-hash");

		AuthService.LoginResult result = authService.login(new LoginRequest("USER@example.com", "secret"));

		assertThat(result.response().accessToken()).isEqualTo("access");
		assertThat(result.refreshToken()).isEqualTo("refresh");
		assertThat(result.response().user().roles()).containsExactly(ApplicationRole.USER);
		assertThat(result.familyExpiresAt()).isAfter(result.issuedAt());
		ArgumentCaptor<RefreshSession> session = ArgumentCaptor.forClass(RefreshSession.class);
		verify(refreshSessions).save(session.capture());
		assertThat(session.getValue().tokenHash()).isEqualTo("refresh-hash");
		assertThat(session.getValue().consumed()).isFalse();
	}

	@Test
	void invalidCredentialsReturnGenericAuthenticationFailure() {
		when(userAccounts.findByEmail("user@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "secret")))
			.isInstanceOf(BadCredentialsException.class)
			.hasMessage("Authentication failed.");
	}

	@Test
	void refreshConsumesExistingTokenAndCreatesReplacementInSameFamily() {
		UserAccount user = new UserAccount("user@example.com", "hash", Set.of(ApplicationRole.USER));
		RefreshTokenFamily family = new RefreshTokenFamily(user, Instant.now(), Instant.now().plus(Duration.ofDays(7)));
		RefreshSession current = new RefreshSession(family, "old-hash", Instant.now());
		Instant accessExpiresAt = Instant.now().plus(Duration.ofMinutes(15));
		when(refreshTokenCodec.hash("old-refresh")).thenReturn("old-hash");
		when(refreshSessions.findByTokenHashForUpdate("old-hash")).thenReturn(Optional.of(current));
		when(jwtTokenService.issue(user)).thenReturn(new JwtTokenService.IssuedAccessToken("access", accessExpiresAt));
		when(refreshTokenCodec.generate()).thenReturn("new-refresh");
		when(refreshTokenCodec.hash("new-refresh")).thenReturn("new-hash");

		AuthService.RefreshResult result = authService.refresh("old-refresh");

		assertThat(result.response()).isPresent();
		assertThat(result.cookie()).isPresent();
		assertThat(result.cookie().orElseThrow().refreshToken()).isEqualTo("new-refresh");
		assertThat(current.consumed()).isTrue();
		ArgumentCaptor<RefreshSession> replacement = ArgumentCaptor.forClass(RefreshSession.class);
		verify(refreshSessions).save(replacement.capture());
		assertThat(replacement.getValue().family()).isSameAs(family);
	}

	@Test
	void replayRevokesFamilyAndReturnsFailureOutcome() {
		UserAccount user = new UserAccount("user@example.com", "hash", Set.of(ApplicationRole.USER));
		RefreshTokenFamily family = new RefreshTokenFamily(user, Instant.now(), Instant.now().plus(Duration.ofDays(7)));
		RefreshSession consumed = new RefreshSession(family, "old-hash", Instant.now());
		consumed.consume(Instant.now());
		when(refreshTokenCodec.hash("old-refresh")).thenReturn("old-hash");
		when(refreshSessions.findByTokenHashForUpdate("old-hash")).thenReturn(Optional.of(consumed));

		AuthService.RefreshResult result = authService.refresh("old-refresh");

		assertThat(result.response()).isEmpty();
		assertThat(result.clearCookie()).isTrue();
		assertThat(family.active(Instant.now())).isFalse();
	}

	@Test
	void disabledUserRefreshRevokesAllUserFamiliesAndReturnsFailureOutcome() {
		UserAccount user = new UserAccount("user@example.com", "hash", Set.of(ApplicationRole.USER));
		UUID userId = UUID.fromString("8e31c835-756d-4609-a6d2-bf20d1505be3");
		ReflectionTestUtils.setField(user, "id", userId);
		user.disable();
		RefreshTokenFamily family = new RefreshTokenFamily(user, Instant.now(), Instant.now().plus(Duration.ofDays(7)));
		RefreshSession current = new RefreshSession(family, "old-hash", Instant.now());
		when(refreshTokenCodec.hash("old-refresh")).thenReturn("old-hash");
		when(refreshSessions.findByTokenHashForUpdate("old-hash")).thenReturn(Optional.of(current));

		AuthService.RefreshResult result = authService.refresh("old-refresh");

		assertThat(result.response()).isEmpty();
		assertThat(result.clearCookie()).isTrue();
		verify(refreshTokenFamilies).revokeActiveFamiliesForUser(eq(userId), any(Instant.class));
	}

	@Test
	void logoutRevokesRepresentedFamilyWithoutRevealingTokenState() {
		UserAccount user = new UserAccount("user@example.com", "hash", Set.of(ApplicationRole.USER));
		RefreshTokenFamily family = new RefreshTokenFamily(user, Instant.now(), Instant.now().plus(Duration.ofDays(7)));
		RefreshSession current = new RefreshSession(family, "refresh-hash", Instant.now());
		when(refreshTokenCodec.hash("refresh")).thenReturn("refresh-hash");
		when(refreshSessions.findByTokenHashForUpdate("refresh-hash")).thenReturn(Optional.of(current));

		authService.logout("refresh");

		assertThat(family.active(Instant.now())).isFalse();
	}
}
