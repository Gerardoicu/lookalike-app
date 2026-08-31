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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_sessions")
class RefreshSession {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private RefreshTokenFamily family;

	@Column(nullable = false, unique = true, length = 64, name = "token_hash", columnDefinition = "char(64)")
	@JdbcTypeCode(SqlTypes.CHAR)
	private String tokenHash;

	@Column(nullable = false, name = "issued_at")
	private Instant issuedAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected RefreshSession() {
	}

	RefreshSession(RefreshTokenFamily family, String tokenHash, Instant issuedAt) {
		this.family = family;
		this.tokenHash = tokenHash;
		this.issuedAt = issuedAt;
	}

	UUID id() {
		return id;
	}

	RefreshTokenFamily family() {
		return family;
	}

	boolean consumed() {
		return consumedAt != null;
	}

	void consume(Instant now) {
		consumedAt = now;
	}

	String tokenHash() {
		return tokenHash;
	}
}
