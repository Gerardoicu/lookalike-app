package com.example.foundation.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
	"app.auth.jwt-secret=test-secret-that-is-not-for-real-use",
	"app.auth.local-bootstrap.enabled=false"
})
class RefreshSessionIT {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserAccountRepository userAccounts;

	@Autowired
	private RefreshSessionRepository refreshSessions;

	@Autowired
	private RefreshTokenFamilyRepository refreshTokenFamilies;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void cleanDatabase() {
		refreshSessions.deleteAll();
		refreshTokenFamilies.deleteAll();
		userAccounts.deleteAll();
	}

	@Test
	void migrationCreatesLowercaseEmailAndRefreshFamilySchema() {
		UserAccount user = userAccounts.save(
			new UserAccount("USER@example.com", passwordEncoder.encode("secret"), Set.of(ApplicationRole.USER))
		);

		assertThat(userAccounts.findByEmail("user@example.com")).map(UserAccount::id).contains(user.id());
		assertThat(user.email()).isEqualTo("user@example.com");
	}

	@Test
	void replayRevocationCommitsAndRejectsReplacementTokenFromNewTransaction() {
		createUser();
		AuthService.LoginResult login = authService.login(new LoginRequest("user@example.com", "secret"));
		AuthService.RefreshResult firstRefresh = authService.refresh(login.refreshToken());
		String replacementToken = firstRefresh.cookie().orElseThrow().refreshToken();

		AuthService.RefreshResult replay = authService.refresh(login.refreshToken());

		assertThat(replay.response()).isEmpty();
		Boolean noActiveFamilies = transactionTemplate.execute(status ->
			refreshTokenFamilies.findAll().stream().noneMatch(family -> family.active(Instant.now()))
		);
		assertThat(noActiveFamilies).isTrue();
		AuthService.RefreshResult replacementRefresh = authService.refresh(replacementToken);
		assertThat(replacementRefresh.response()).isEmpty();
	}

	@Test
	void concurrentRefreshAllowsOnlyOneRotationAndReplayRevokesFamily() throws Exception {
		createUser();
		AuthService.LoginResult login = authService.login(new LoginRequest("user@example.com", "secret"));
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<AuthService.RefreshResult> first = executor.submit(() -> refreshAfterStart(start, login.refreshToken()));
			Future<AuthService.RefreshResult> second = executor.submit(() -> refreshAfterStart(start, login.refreshToken()));

			start.countDown();
			List<AuthService.RefreshResult> results = List.of(first.get(), second.get());

			assertThat(results).filteredOn(result -> result.response().isPresent()).hasSize(1);
			assertThat(results).filteredOn(result -> result.response().isEmpty()).hasSize(1);
			assertThat(refreshTokenFamilies.findAll()).noneMatch(family -> family.active(Instant.now().plus(Duration.ZERO)));
		}
	}

	private AuthService.RefreshResult refreshAfterStart(CountDownLatch start, String token) throws InterruptedException {
		start.await();
		return authService.refresh(token);
	}

	private void createUser() {
		userAccounts.save(new UserAccount("user@example.com", passwordEncoder.encode("secret"), Set.of(ApplicationRole.USER)));
	}
}
