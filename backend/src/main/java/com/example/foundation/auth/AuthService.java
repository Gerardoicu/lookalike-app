package com.example.foundation.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

	private static final String AUTHENTICATION_FAILED = "Authentication failed.";

	private final UserAccountRepository userAccounts;

	private final RefreshSessionRepository refreshSessions;

	private final RefreshTokenFamilyRepository refreshTokenFamilies;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenService jwtTokenService;

	private final RefreshTokenCodec refreshTokenCodec;

	private final AuthProperties authProperties;

	AuthService(
		UserAccountRepository userAccounts,
		RefreshSessionRepository refreshSessions,
		RefreshTokenFamilyRepository refreshTokenFamilies,
		PasswordEncoder passwordEncoder,
		JwtTokenService jwtTokenService,
		RefreshTokenCodec refreshTokenCodec,
		AuthProperties authProperties
	) {
		this.userAccounts = userAccounts;
		this.refreshSessions = refreshSessions;
		this.refreshTokenFamilies = refreshTokenFamilies;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenCodec = refreshTokenCodec;
		this.authProperties = authProperties;
	}

	@Transactional
	LoginResult login(LoginRequest request) {
		UserAccount user = userAccounts.findByEmail(UserAccount.normalizeEmail(request.email()))
			.filter(UserAccount::enabled)
			.filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
			.orElseThrow(() -> new BadCredentialsException(AUTHENTICATION_FAILED));
		return issueLoginSession(user);
	}

	@Transactional
	RefreshResult refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return RefreshResult.failure(true);
		}
		Instant now = Instant.now();
		Optional<RefreshSession> currentSession = refreshSessions.findByTokenHashForUpdate(refreshTokenCodec.hash(refreshToken));
		if (currentSession.isEmpty()) {
			return RefreshResult.failure(true);
		}
		RefreshSession current = currentSession.get();
		RefreshTokenFamily family = current.family();
		if (current.consumed()) {
			family.revoke(now);
			return RefreshResult.failure(true);
		}
		if (!family.active(now)) {
			return RefreshResult.failure(true);
		}
		if (!family.user().enabled()) {
			refreshTokenFamilies.revokeActiveFamiliesForUser(family.user().id(), now);
			return RefreshResult.failure(true);
		}
		current.consume(now);
		return RefreshResult.success(issueReplacement(family, now));
	}

	@Transactional
	void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		Instant now = Instant.now();
		refreshSessions.findByTokenHashForUpdate(refreshTokenCodec.hash(refreshToken))
			.ifPresent(session -> session.family().revoke(now));
	}

	@Transactional
	void revokeActiveFamiliesForUser(UUID userId) {
		refreshTokenFamilies.revokeActiveFamiliesForUser(userId, Instant.now());
	}

	private LoginResult issueLoginSession(UserAccount user) {
		Instant now = Instant.now();
		RefreshTokenFamily family = refreshTokenFamilies.save(
			new RefreshTokenFamily(user, now, now.plus(authProperties.refreshFamilyTtl()))
		);
		IssuedRefreshSession refreshSession = issueRefreshSession(family, now);
		return new LoginResult(refreshSession.response(), refreshSession.refreshToken(), family.expiresAt(), now);
	}

	private IssuedRefreshSession issueReplacement(RefreshTokenFamily family, Instant now) {
		return issueRefreshSession(family, now);
	}

	private IssuedRefreshSession issueRefreshSession(RefreshTokenFamily family, Instant now) {
		JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(family.user());
		String refreshToken = refreshTokenCodec.generate();
		refreshSessions.save(new RefreshSession(family, refreshTokenCodec.hash(refreshToken), now));
		return new IssuedRefreshSession(
			new AuthResponse(
				accessToken.value(),
				accessToken.expiresAt(),
				AuthenticatedUserResponse.from(family.user())
			),
			refreshToken,
			family.expiresAt(),
			now
		);
	}

	record LoginResult(AuthResponse response, String refreshToken, Instant familyExpiresAt, Instant issuedAt) {
	}

	record RefreshResult(Optional<AuthResponse> response, Optional<RefreshCookie> cookie, boolean clearCookie) {

		static RefreshResult success(IssuedRefreshSession session) {
			return new RefreshResult(
				Optional.of(session.response()),
				Optional.of(new RefreshCookie(session.refreshToken(), session.familyExpiresAt(), session.issuedAt())),
				false
			);
		}

		static RefreshResult failure(boolean clearCookie) {
			return new RefreshResult(Optional.empty(), Optional.empty(), clearCookie);
		}
	}

	record RefreshCookie(String refreshToken, Instant familyExpiresAt, Instant issuedAt) {
	}

	private record IssuedRefreshSession(
		AuthResponse response,
		String refreshToken,
		Instant familyExpiresAt,
		Instant issuedAt
	) {
	}
}
