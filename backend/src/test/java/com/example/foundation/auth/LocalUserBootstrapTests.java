package com.example.foundation.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalUserBootstrapTests {

	private final AuthProperties properties = new AuthProperties();

	private final UserAccountRepository userAccounts = org.mockito.Mockito.mock(UserAccountRepository.class);

	private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);

	private LocalUserBootstrap bootstrap;

	@BeforeEach
	void setUp() {
		bootstrap = new LocalUserBootstrap(properties, userAccounts, passwordEncoder);
	}

	@Test
	void disabledBootstrapDoesNothing() {
		bootstrap.run(new DefaultApplicationArguments());

		verify(userAccounts, never()).save(any(UserAccount.class));
	}

	@Test
	void enabledBootstrapRequiresEmailPasswordAndRoles() {
		properties.localBootstrap().setEnabled(true);

		assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("email and password");
	}

	@Test
	void enabledBootstrapCreatesLowercaseUserWithoutOverwritingExistingUsers() {
		properties.localBootstrap().setEnabled(true);
		properties.localBootstrap().setEmail("LOCAL.User@Example.com");
		properties.localBootstrap().setPassword("secret");
		properties.localBootstrap().setRoles(Set.of(ApplicationRole.USER, ApplicationRole.ADMIN));
		when(userAccounts.existsByEmail("local.user@example.com")).thenReturn(false);
		when(passwordEncoder.encode("secret")).thenReturn("hash");

		bootstrap.run(new DefaultApplicationArguments());

		ArgumentCaptor<UserAccount> savedUser = ArgumentCaptor.forClass(UserAccount.class);
		verify(userAccounts).save(savedUser.capture());
		assertThat(savedUser.getValue().email()).isEqualTo("local.user@example.com");
		assertThat(savedUser.getValue().passwordHash()).isEqualTo("hash");
		assertThat(savedUser.getValue().roles()).containsExactlyInAnyOrder(ApplicationRole.USER, ApplicationRole.ADMIN);

		when(userAccounts.existsByEmail("local.user@example.com")).thenReturn(true);
		bootstrap.run(new DefaultApplicationArguments());

		verify(userAccounts, times(1)).save(any(UserAccount.class));
	}
}
