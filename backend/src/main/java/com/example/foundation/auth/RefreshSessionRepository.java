package com.example.foundation.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from RefreshSession session join fetch session.family family join fetch family.user where session.tokenHash = :tokenHash")
	Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
