package com.gerardoicu.lookalike.api;

import java.net.URI;

import com.gerardoicu.lookalike.face.FaceAnalysisException;
import com.gerardoicu.lookalike.security.SecurityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(SecurityException.class)
	ProblemDetail handleSecurityException(SecurityException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
		problem.setType(URI.create("https://lookalike.local/problems/" + exception.errorCode().name().toLowerCase().replace('_', '-')));
		problem.setTitle(titleFor(exception.status()));
		problem.setProperty("code", exception.errorCode().name());
		exception.retryAfterSeconds().ifPresent(value -> problem.setProperty("retryAfterSeconds", value));
		return problem;
	}

	@ExceptionHandler(FaceAnalysisException.class)
	ProblemDetail handleFaceAnalysisException(FaceAnalysisException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
		problem.setType(URI.create("https://lookalike.local/problems/" + exception.errorCode().name().toLowerCase().replace('_', '-')));
		problem.setTitle(titleFor(exception.status()));
		problem.setProperty("code", exception.errorCode().name());
		return problem;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail handleMaxUploadSizeExceededException() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded image is too large.");
		problem.setType(URI.create("https://lookalike.local/problems/face-image-oversized"));
		problem.setTitle(titleFor(HttpStatus.PAYLOAD_TOO_LARGE));
		problem.setProperty("code", ErrorCode.FACE_IMAGE_OVERSIZED.name());
		return problem;
	}

	private static String titleFor(HttpStatus status) {
		return switch (status) {
			case BAD_REQUEST -> "Bad request";
			case TOO_MANY_REQUESTS -> "Too many requests";
			case PAYLOAD_TOO_LARGE -> "Payload too large";
			case SERVICE_UNAVAILABLE -> "Service unavailable";
			default -> "Request failed";
		};
	}
}
