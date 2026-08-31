package com.gerardoicu.lookalike.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, UUID> {

	@Modifying
	@Query("""
		update RefreshTokenFamily family
		set family.revokedAt = :revokedAt
		where family.user.id = :userId and family.revokedAt is null
		""")
	int revokeActiveFamiliesForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
