package com.gerardoicu.lookalike.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gerardoicu.lookalike.api.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InMemoryFixedWindowRateLimiter {

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final SecurityProperties properties;
	private final Clock clock;
	private Instant nextCleanupAt;

	@Autowired
	public InMemoryFixedWindowRateLimiter(SecurityProperties properties) {
		this(properties, Clock.systemUTC());
	}

	InMemoryFixedWindowRateLimiter(SecurityProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.nextCleanupAt = clock.instant().plus(properties.rateLimit().cleanupInterval());
	}

	void check(String key) {
		Instant now = clock.instant();
		cleanupIfDue(now);
		Window window = windows.compute(key, (ignored, current) -> updateWindow(current, now));
		if (window.count() > properties.rateLimit().capacity()) {
			long retryAfterSeconds = Math.max(1, Duration.between(now, window.startedAt().plus(properties.rateLimit().window())).toSeconds());
			throw new SecurityException(
					ErrorCode.SECURITY_RATE_LIMITED,
					HttpStatus.TOO_MANY_REQUESTS,
					"Too many requests.",
					retryAfterSeconds
			);
		}
	}

	private Window updateWindow(Window current, Instant now) {
		if (current == null || !now.isBefore(current.startedAt().plus(properties.rateLimit().window()))) {
			return new Window(now, 1);
		}
		return new Window(current.startedAt(), current.count() + 1);
	}

	private synchronized void cleanupIfDue(Instant now) {
		if (now.isBefore(nextCleanupAt)) {
			return;
		}
		Instant oldestActiveWindow = now.minus(properties.rateLimit().window());
		Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Window> entry = iterator.next();
			if (entry.getValue().startedAt().isBefore(oldestActiveWindow)) {
				iterator.remove();
			}
		}
		nextCleanupAt = now.plus(properties.rateLimit().cleanupInterval());
	}

	private record Window(Instant startedAt, int count) {
	}
}
