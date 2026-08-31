package com.gerardoicu.lookalike.auth;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
class UserAccount {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(nullable = false, name = "password_hash")
	private String passwordHash;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false, name = "created_at")
	private Instant createdAt;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 32)
	private Set<ApplicationRole> roles = EnumSet.noneOf(ApplicationRole.class);

	protected UserAccount() {
	}

	UserAccount(String email, String passwordHash, Set<ApplicationRole> roles) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.roles = EnumSet.copyOf(roles);
		this.enabled = true;
		this.createdAt = Instant.now();
	}

	UUID id() {
		return id;
	}

	String email() {
		return email;
	}

	String passwordHash() {
		return passwordHash;
	}

	boolean enabled() {
		return enabled;
	}

	void disable() {
		enabled = false;
	}

	Set<ApplicationRole> roles() {
		return Set.copyOf(roles);
	}

	static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
