package com.gerardoicu.lookalike.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gerardoicu.lookalike.api.ErrorCode;
import com.gerardoicu.lookalike.fedelobo.FedeloboAnalysisResult;
import com.gerardoicu.lookalike.fedelobo.FedeloboSimilarityLevel;
import org.junit.jupiter.api.Test;

class FacialAnalysisServiceTests {

	private final FaceAnalysisProperties properties = new FaceAnalysisProperties(
			1_024 * 1_024,
			1_024,
			1_024,
			1_048_576,
			320,
			0.9f,
			""
	);

	@Test
	void validJpegWithOneUsableFaceSucceeds() {
		FacialAnalysisService service = serviceWith(image -> new FacialEmbedding(new float[128]));

		assertThat(service.analyze(ImageTestData.jpeg(64, 64)))
			.isEqualTo(new FedeloboAnalysisResult(31, FedeloboSimilarityLevel.LOW, "A light Fedelobo resemblance showed up."));
	}

	@Test
	void emptyImageIsRejectedBeforeFaceDetection() {
		FacialAnalysisService service = serviceWith(image -> {
			throw new AssertionError("Face engine should not be called.");
		});

		assertThatThrownBy(() -> service.analyze(new byte[0]))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_IMAGE_EMPTY);
	}

	@Test
	void unsupportedFormatIsRejectedBeforeFaceDetection() {
		FacialAnalysisService service = serviceWith(image -> {
			throw new AssertionError("Face engine should not be called.");
		});

		assertThatThrownBy(() -> service.analyze(ImageTestData.png()))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_IMAGE_UNSUPPORTED_FORMAT);
	}

	@Test
	void corruptJpegIsRejectedBeforeFaceDetection() {
		FacialAnalysisService service = serviceWith(image -> {
			throw new AssertionError("Face engine should not be called.");
		});

		assertThatThrownBy(() -> service.analyze(new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2, 3 }))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_IMAGE_CORRUPT);
	}

	@Test
	void oversizedImageIsRejectedBeforeDecode() {
		FacialAnalysisService service = new FacialAnalysisService(
				new UploadedImageValidator(new FaceAnalysisProperties(4, 1_024, 1_024, 1_048_576, 320, 0.9f, "")),
				image -> {
					throw new AssertionError("Face engine should not be called.");
				},
				embedding -> {
					throw new AssertionError("Fedelobo service should not be called.");
				}
		);

		assertThatThrownBy(() -> service.analyze(ImageTestData.jpeg(8, 8)))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_IMAGE_OVERSIZED);
	}

	@Test
	void excessiveDimensionsAreRejectedBeforeDecode() {
		FacialAnalysisService service = new FacialAnalysisService(
				new UploadedImageValidator(new FaceAnalysisProperties(1_024 * 1_024, 32, 32, 1_024, 320, 0.9f, "")),
				image -> {
					throw new AssertionError("Face engine should not be called.");
				},
				embedding -> {
					throw new AssertionError("Fedelobo service should not be called.");
				}
		);

		assertThatThrownBy(() -> service.analyze(ImageTestData.jpeg(64, 64)))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_IMAGE_DIMENSIONS_UNSUPPORTED);
	}

	@Test
	void noUsableFaceIsRejected() {
		FacialAnalysisService service = serviceWith(image -> {
			throw new FaceAnalysisException(ErrorCode.FACE_NO_USABLE_FACE, org.springframework.http.HttpStatus.BAD_REQUEST, "No usable face was detected.");
		});

		assertThatThrownBy(() -> service.analyze(ImageTestData.jpeg(64, 64)))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_NO_USABLE_FACE);
	}

	@Test
	void multipleUsableFacesAreRejected() {
		FacialAnalysisService service = serviceWith(image -> {
			throw new FaceAnalysisException(ErrorCode.FACE_MULTIPLE_USABLE_FACES, org.springframework.http.HttpStatus.BAD_REQUEST, "Multiple usable faces were detected.");
		});

		assertThatThrownBy(() -> service.analyze(ImageTestData.jpeg(64, 64)))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_MULTIPLE_USABLE_FACES);
	}

	@Test
	void missingModelsReturnAnalysisUnavailableAfterImageValidation() {
		FacialAnalysisService service = serviceWith(new OpenCvOnnxFacialEmbeddingEngine(properties));

		assertThatThrownBy(() -> service.analyze(ImageTestData.jpeg(64, 64)))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FACE_ANALYSIS_UNAVAILABLE);
	}

	@Test
	void fedeloboFailurePropagatesAfterEmbeddingExtraction() {
		FacialAnalysisService service = new FacialAnalysisService(
				new UploadedImageValidator(properties),
				image -> new FacialEmbedding(new float[128]),
				embedding -> {
					throw new FaceAnalysisException(ErrorCode.FEDELOBO_PROFILE_UNAVAILABLE, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Fedelobo profile is unavailable.");
				}
		);

		assertThatThrownBy(() -> service.analyze(ImageTestData.jpeg(64, 64)))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FEDELOBO_PROFILE_UNAVAILABLE);
	}

	private FacialAnalysisService serviceWith(FacialEmbeddingEngine engine) {
		return new FacialAnalysisService(
				new UploadedImageValidator(properties),
				engine,
				embedding -> new FedeloboAnalysisResult(31, FedeloboSimilarityLevel.LOW, "A light Fedelobo resemblance showed up.")
		);
	}
}
