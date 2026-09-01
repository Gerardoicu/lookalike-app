package com.gerardoicu.lookalike.face;

import com.gerardoicu.lookalike.api.ErrorCode;
import org.springframework.http.HttpStatus;

public class FaceAnalysisException extends RuntimeException {

	private final ErrorCode errorCode;
	private final HttpStatus status;

	FaceAnalysisException(ErrorCode errorCode, HttpStatus status, String message) {
		super(message);
		this.errorCode = errorCode;
		this.status = status;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public HttpStatus status() {
		return status;
	}
}
