package com.gerardoicu.lookalike.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record AnonymousVisitorCookie(UUID visitorId, Optional<Instant> lastSuccessfulAnalysisAt) {
}
