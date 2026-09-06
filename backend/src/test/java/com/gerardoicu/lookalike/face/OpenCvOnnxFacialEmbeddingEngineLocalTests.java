package com.gerardoicu.lookalike.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.gerardoicu.lookalike.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

class OpenCvOnnxFacialEmbeddingEngineLocalTests {

	private static final String ASSET_DIR = "AI001_ASSET_DIR";
	private static final String TOLERANT_FACE_IMAGE = "WEB001_TOLERANT_FACE_IMAGE";

	@Test
	void extractsEmbeddingWhenLocalAssetsAreAvailable() {
		Path assetDir = configuredAssetDirectory();
		Path modelDirectory = configuredModelDirectory(assetDir);
		Path oneFaceImage = assetDir.resolve("fixtures/same-person-1.jpg");
		assumeTrue(Files.isRegularFile(oneFaceImage), "One-face fixture is missing");

		FacialEmbedding embedding = extractEmbedding(oneFaceImage, modelDirectory);

		assertThat(embedding.values()).hasSize(128);
	}

	@Test
	void tolerantDetectorSettingsAcceptLessIdealLocalFaceWhenAvailable() {
		Path assetDir = configuredAssetDirectory();
		Path modelDirectory = configuredModelDirectory(assetDir);
		Path tolerantFaceImage = configuredPath(TOLERANT_FACE_IMAGE);
		assumeTrue(tolerantFaceImage != null && Files.isRegularFile(tolerantFaceImage), () -> TOLERANT_FACE_IMAGE + " is not configured");

		FacialEmbedding embedding = extractEmbedding(tolerantFaceImage, modelDirectory);

		assertThat(embedding.values()).hasSize(128);
	}

	@Test
	void noFaceImageRemainsRejectedWithTolerantDetectorSettings() {
		Path assetDir = configuredAssetDirectory();
		Path modelDirectory = configuredModelDirectory(assetDir);
		OpenCvOnnxFacialEmbeddingEngine engine = engine(modelDirectory);
		OpenCvLibrary.load();
		Mat blank = new Mat(720, 720, CvType.CV_8UC3, new Scalar(220, 220, 220));

		try {
			assertThatThrownBy(() -> engine.extractEmbedding(new DecodedImage(blank, blank.width(), blank.height())))
				.isInstanceOf(FaceAnalysisException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.FACE_NO_USABLE_FACE);
		}
		finally {
			blank.release();
		}
	}

	private static FacialEmbedding extractEmbedding(Path imagePath, Path modelDirectory) {
		OpenCvLibrary.load();
		MatOfByte imageBytes = new MatOfByte(readAllBytes(imagePath));
		Mat image;
		try {
			image = Imgcodecs.imdecode(imageBytes, Imgcodecs.IMREAD_COLOR);
		}
		finally {
			imageBytes.release();
		}
		try {
			return engine(modelDirectory).extractEmbedding(new DecodedImage(image, image.width(), image.height()));
		}
		finally {
			image.release();
		}
	}

	private static OpenCvOnnxFacialEmbeddingEngine engine(Path modelDirectory) {
		return new OpenCvOnnxFacialEmbeddingEngine(new FaceAnalysisProperties(
				6_291_456L,
				4_096,
				4_096,
				12_000_000L,
				320,
				0.8f,
				modelDirectory.toString()
		));
	}

	private static Path configuredModelDirectory(Path assetDir) {
		assumeTrue(assetDir != null, () -> ASSET_DIR + " is not configured");
		Path modelDirectory = assetDir.resolve("models");
		assumeTrue(Files.isRegularFile(modelDirectory.resolve("face_detection_yunet_2023mar.onnx")), "YuNet model is missing");
		assumeTrue(Files.isRegularFile(modelDirectory.resolve("face_recognition_sface_2021dec.onnx")), "SFace model is missing");
		return modelDirectory;
	}

	private static Path configuredAssetDirectory() {
		return configuredPath(ASSET_DIR);
	}

	private static Path configuredPath(String variable) {
		String configured = System.getenv(variable);
		if (configured == null || configured.isBlank()) {
			return null;
		}
		return Path.of(configured);
	}

	private static byte[] readAllBytes(Path path) {
		try {
			return Files.readAllBytes(path);
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException("Unable to read local fixture.", ex);
		}
	}
}
