package com.gerardoicu.lookalike.security;

import java.util.OptionalLong;

import com.gerardoicu.lookalike.api.ErrorCode;
import org.springframework.http.HttpStatus;

public class SecurityException extends RuntimeException {

	private final ErrorCode errorCode;
	private final HttpStatus status;
	private final OptionalLong retryAfterSeconds;

	SecurityException(ErrorCode errorCode, HttpStatus status, String message) {
		this(errorCode, status, message, OptionalLong.empty());
	}

	SecurityException(ErrorCode errorCode, HttpStatus status, String message, long retryAfterSeconds) {
		this(errorCode, status, message, OptionalLong.of(retryAfterSeconds));
	}

	private SecurityException(ErrorCode errorCode, HttpStatus status, String message, OptionalLong retryAfterSeconds) {
		super(message);
		this.errorCode = errorCode;
		this.status = status;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public HttpStatus status() {
		return status;
	}

	public OptionalLong retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
