package com.gerardoicu.lookalike.face;

import java.io.IOException;
import java.util.List;

import com.gerardoicu.lookalike.api.ErrorCode;
import com.gerardoicu.lookalike.security.AnonymousAnalysisAuthorization;
import com.gerardoicu.lookalike.security.AnonymousAnalysisSecurityGate;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
class FacialAnalysisController {

	private static final String IMAGE_PART = "image";

	private final AnonymousAnalysisSecurityGate securityGate;
	private final FacialAnalysisService facialAnalysisService;

	FacialAnalysisController(AnonymousAnalysisSecurityGate securityGate, FacialAnalysisService facialAnalysisService) {
		this.securityGate = securityGate;
		this.facialAnalysisService = facialAnalysisService;
	}

	@PostMapping(path = "/api/v1/facial-analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<FacialAnalysisResponse> analyze(
			@RequestHeader(name = "X-Turnstile-Token", required = false) String turnstileToken,
			MultipartHttpServletRequest request,
			HttpServletResponse response
	) {
		AnonymousAnalysisAuthorization authorization = securityGate.authorizePreAnalysis(request, response, turnstileToken);
		MultipartFile image = requiredSingleImage(request);
		facialAnalysisService.analyze(bytesOf(image));
		securityGate.recordSuccessfulAnalysis(response, authorization);
		return ResponseEntity.ok(new FacialAnalysisResponse(true));
	}

	private static MultipartFile requiredSingleImage(MultipartHttpServletRequest request) {
		int fileCount = request.getMultiFileMap()
			.values()
			.stream()
			.mapToInt(List::size)
			.sum();
		if (fileCount == 0 || request.getFiles(IMAGE_PART).isEmpty()) {
			throw new FaceAnalysisException(ErrorCode.FACE_IMAGE_MISSING, HttpStatus.BAD_REQUEST, "Uploaded image is required.");
		}
		if (fileCount != 1 || request.getFiles(IMAGE_PART).size() != 1) {
			throw new FaceAnalysisException(ErrorCode.FACE_IMAGE_MULTIPLE_FILES, HttpStatus.BAD_REQUEST, "Exactly one uploaded image is allowed.");
		}
		return request.getFile(IMAGE_PART);
	}

	private static byte[] bytesOf(MultipartFile image) {
		try {
			return image.getBytes();
		}
		catch (IOException ex) {
			throw new FaceAnalysisException(ErrorCode.FACE_IMAGE_CORRUPT, HttpStatus.BAD_REQUEST, "Uploaded image is corrupt.");
		}
	}
}
