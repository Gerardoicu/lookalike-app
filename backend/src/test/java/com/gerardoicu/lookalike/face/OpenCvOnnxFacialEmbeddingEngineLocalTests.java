package com.gerardoicu.lookalike.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

class OpenCvOnnxFacialEmbeddingEngineLocalTests {

	private static final String ASSET_DIR = "AI001_ASSET_DIR";

	@Test
	void extractsEmbeddingWhenLocalAssetsAreAvailable() {
		Path assetDir = configuredAssetDirectory();
		assumeTrue(assetDir != null, () -> ASSET_DIR + " is not configured");
		Path modelDirectory = assetDir.resolve("models");
		Path oneFaceImage = assetDir.resolve("fixtures/same-person-1.jpg");
		assumeTrue(Files.isRegularFile(modelDirectory.resolve("face_detection_yunet_2023mar.onnx")), "YuNet model is missing");
		assumeTrue(Files.isRegularFile(modelDirectory.resolve("face_recognition_sface_2021dec.onnx")), "SFace model is missing");
		assumeTrue(Files.isRegularFile(oneFaceImage), "One-face fixture is missing");

		OpenCvLibrary.load();
		MatOfByte imageBytes = new MatOfByte(readAllBytes(oneFaceImage));
		Mat image;
		try {
			image = Imgcodecs.imdecode(imageBytes, Imgcodecs.IMREAD_COLOR);
		}
		finally {
			imageBytes.release();
		}
		OpenCvOnnxFacialEmbeddingEngine engine = new OpenCvOnnxFacialEmbeddingEngine(new FaceAnalysisProperties(
				6_291_456L,
				4_096,
				4_096,
				12_000_000L,
				320,
				0.9f,
				modelDirectory.toString()
		));

		FacialEmbedding embedding;
		try {
			embedding = engine.extractEmbedding(new DecodedImage(image, image.width(), image.height()));
		}
		finally {
			image.release();
		}

		assertThat(embedding.values()).hasSize(128);
	}

	private static Path configuredAssetDirectory() {
		String configured = System.getenv(ASSET_DIR);
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
