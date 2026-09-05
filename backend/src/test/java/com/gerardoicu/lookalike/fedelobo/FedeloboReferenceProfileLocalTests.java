package com.gerardoicu.lookalike.fedelobo;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.gerardoicu.lookalike.face.DecodedImage;
import com.gerardoicu.lookalike.face.FaceAnalysisProperties;
import com.gerardoicu.lookalike.face.FacialEmbedding;
import com.gerardoicu.lookalike.face.OpenCvOnnxFacialEmbeddingEngine;
import com.gerardoicu.lookalike.face.UploadedImageValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FedeloboReferenceProfileLocalTests {

	private static final String AI_ASSET_DIR = "AI001_ASSET_DIR";
	private static final String REFERENCE_DIR = "FEDELOBO_REFERENCE_DIR";
	private static final String PROFILE_PATH = "FEDELOBO_PROFILE_PATH";

	@Test
	void generatesLocalFedeloboProfileWhenIgnoredAssetsAreAvailable() throws Exception {
		Path assetDirectory = configuredDirectory(AI_ASSET_DIR);
		Path referenceDirectory = configuredDirectory(REFERENCE_DIR);
		Path profilePath = configuredPath(PROFILE_PATH);
		assumeTrue(assetDirectory != null && Files.isDirectory(assetDirectory), () -> AI_ASSET_DIR + " is not configured");
		assumeTrue(referenceDirectory != null && Files.isDirectory(referenceDirectory), () -> REFERENCE_DIR + " is not configured");
		assumeTrue(profilePath != null, () -> PROFILE_PATH + " is not configured");
		assumeTrue(Files.isRegularFile(assetDirectory.resolve("models/face_detection_yunet_2023mar.onnx")), "YuNet model is missing");
		assumeTrue(Files.isRegularFile(assetDirectory.resolve("models/face_recognition_sface_2021dec.onnx")), "SFace model is missing");

		FaceAnalysisProperties properties = new FaceAnalysisProperties(
				6_291_456,
				4_096,
				4_096,
				12_000_000,
				320,
				0.9f,
				assetDirectory.resolve("models").toString()
		);
		UploadedImageValidator validator = new UploadedImageValidator(properties);
		OpenCvOnnxFacialEmbeddingEngine engine = new OpenCvOnnxFacialEmbeddingEngine(properties);
		List<Path> sourceImages = Files.list(referenceDirectory)
			.filter(path -> path.toString().toLowerCase().matches(".*\\.(jpg|jpeg)$"))
			.sorted()
			.toList();

		List<ReferenceDocument> references = sourceImages.stream()
			.map(path -> validatedReference(path, validator, engine))
			.flatMap(List::stream)
			.toList();
		assumeTrue(references.size() >= FedeloboProfile.MINIMUM_REFERENCE_COUNT, "At least 3 usable Fedelobo references are required");
		printCalibration(references);

		Files.createDirectories(profilePath.getParent());
		new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(profilePath.toFile(), Map.of(
				"profile", "FEDELOBO",
				"embeddingDimension", FedeloboProfile.EMBEDDING_DIMENSION,
				"references", references
		));
	}

	private static List<ReferenceDocument> validatedReference(
			Path path,
			UploadedImageValidator validator,
			OpenCvOnnxFacialEmbeddingEngine engine
	) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			DecodedImage image = validator.validate(bytes);
			try {
				FacialEmbedding embedding = engine.extractEmbedding(image);
				return List.of(new ReferenceDocument(path.getFileName().toString(), sha256(bytes), SimilarityMath.normalized(embedding.values())));
			}
			finally {
				image.mat().release();
			}
		}
		catch (Exception ex) {
			System.out.printf("Rejected Fedelobo reference %s: %s%n", path.getFileName(), ex.getMessage());
			return List.of();
		}
	}

	private static void printCalibration(List<ReferenceDocument> references) {
		for (int visitor = 0; visitor < references.size(); visitor++) {
			int current = visitor;
			double[] scores = references.stream()
				.filter(reference -> reference != references.get(current))
				.mapToDouble(reference -> SimilarityMath.cosine(references.get(current).embedding(), reference.embedding()))
				.toArray();
			System.out.printf("LOO %s max=%.6f mean=%.6f median=%.6f top3mean=%.6f%n",
					references.get(current).label(),
					max(scores),
					mean(scores),
					median(scores),
					top3mean(scores)
			);
		}
	}

	private static double max(double[] values) {
		double max = Double.NEGATIVE_INFINITY;
		for (double value : values) {
			max = Math.max(max, value);
		}
		return max;
	}

	private static double mean(double[] values) {
		double sum = 0;
		for (double value : values) {
			sum += value;
		}
		return sum / values.length;
	}

	private static double median(double[] values) {
		double[] sorted = values.clone();
		java.util.Arrays.sort(sorted);
		int length = sorted.length;
		return length % 2 == 1 ? sorted[length / 2] : (sorted[length / 2 - 1] + sorted[length / 2]) / 2;
	}

	private static double top3mean(double[] values) {
		double[] sorted = values.clone();
		java.util.Arrays.sort(sorted);
		double sum = 0;
		for (int index = sorted.length - 3; index < sorted.length; index++) {
			sum += sorted[index];
		}
		return sum / 3;
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static Path configuredDirectory(String variable) {
		Path path = configuredPath(variable);
		return path == null ? null : path;
	}

	private static Path configuredPath(String variable) {
		String value = System.getenv(variable);
		if (value == null || value.isBlank()) {
			return null;
		}
		return Path.of(value);
	}

	record ReferenceDocument(String label, String sha256, float[] embedding) {
	}
}
