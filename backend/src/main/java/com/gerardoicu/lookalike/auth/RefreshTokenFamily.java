package com.gerardoicu.lookalike.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token_families")
class RefreshTokenFamily {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(nullable = false, name = "created_at")
	private Instant createdAt;

	@Column(nullable = false, name = "expires_at")
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected RefreshTokenFamily() {
	}

	RefreshTokenFamily(UserAccount user, Instant createdAt, Instant expiresAt) {
		this.user = user;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	UUID id() {
		return id;
	}

	UserAccount user() {
		return user;
	}

	Instant expiresAt() {
		return expiresAt;
	}

	boolean active(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}
}
