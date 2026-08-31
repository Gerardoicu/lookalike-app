package com.example.foundation.auth;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Profile("local")
class LocalUserBootstrap implements ApplicationRunner {

	private final AuthProperties authProperties;

	private final UserAccountRepository userAccounts;

	private final PasswordEncoder passwordEncoder;

	LocalUserBootstrap(
		AuthProperties authProperties,
		UserAccountRepository userAccounts,
		PasswordEncoder passwordEncoder
	) {
		this.authProperties = authProperties;
		this.userAccounts = userAccounts;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		AuthProperties.LocalBootstrap bootstrap = authProperties.localBootstrap();
		if (!bootstrap.enabled()) {
			return;
		}
		if (!StringUtils.hasText(bootstrap.email()) || !StringUtils.hasText(bootstrap.password())) {
			throw new IllegalStateException("Local user bootstrap requires email and password values.");
		}
		String normalizedEmail = UserAccount.normalizeEmail(bootstrap.email());
		if (userAccounts.existsByEmail(normalizedEmail)) {
			return;
		}
		Set<ApplicationRole> configuredRoles = bootstrap.roles();
		if (configuredRoles.isEmpty()) {
			throw new IllegalStateException("Local user bootstrap requires at least one role.");
		}
		userAccounts.save(new UserAccount(normalizedEmail, passwordEncoder.encode(bootstrap.password()), EnumSet.copyOf(configuredRoles)));
	}
}
