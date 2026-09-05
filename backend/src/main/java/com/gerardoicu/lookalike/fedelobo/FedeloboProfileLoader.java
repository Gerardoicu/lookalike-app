package com.gerardoicu.lookalike.fedelobo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gerardoicu.lookalike.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class FedeloboProfileLoader {

	private static final String PROFILE_NAME = "FEDELOBO";

	private final FedeloboProperties properties;
	private final ObjectMapper objectMapper;
	private FedeloboProfile profile;

	FedeloboProfileLoader(FedeloboProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	synchronized FedeloboProfile load() {
		if (profile == null) {
			profile = loadProfile();
		}
		return profile;
	}

	private FedeloboProfile loadProfile() {
		if (properties.profilePath().isBlank()) {
			throw unavailable();
		}
		Path profilePath = Path.of(properties.profilePath());
		if (!Files.isRegularFile(profilePath)) {
			throw unavailable();
		}
		try {
			ProfileDocument document = objectMapper.readValue(profilePath.toFile(), ProfileDocument.class);
			if (!PROFILE_NAME.equals(document.profile()) || document.embeddingDimension() != FedeloboProfile.EMBEDDING_DIMENSION) {
				throw unavailable();
			}
			List<FedeloboProfile.ReferenceEmbedding> references = document.references()
				.stream()
				.map(this::validatedReference)
				.toList();
			if (references.size() < FedeloboProfile.MINIMUM_REFERENCE_COUNT) {
				throw unavailable();
			}
			return new FedeloboProfile(references);
		}
		catch (RuntimeException ex) {
			if (ex instanceof com.gerardoicu.lookalike.face.FaceAnalysisException faceAnalysisException) {
				throw faceAnalysisException;
			}
			throw unavailable();
		}
	}

	private FedeloboProfile.ReferenceEmbedding validatedReference(ReferenceDocument reference) {
		float[] values = reference.embedding();
		if (values == null || values.length != FedeloboProfile.EMBEDDING_DIMENSION) {
			throw unavailable();
		}
		float[] normalized = SimilarityMath.normalized(values);
		return new FedeloboProfile.ReferenceEmbedding(nonBlank(reference.label()), nonBlank(reference.sha256()), normalized);
	}

	private static String nonBlank(String value) {
		if (value == null || value.isBlank()) {
			throw unavailable();
		}
		return value;
	}

	static com.gerardoicu.lookalike.face.FaceAnalysisException unavailable() {
		return new com.gerardoicu.lookalike.face.FaceAnalysisException(
				ErrorCode.FEDELOBO_PROFILE_UNAVAILABLE,
				HttpStatus.SERVICE_UNAVAILABLE,
				"Fedelobo profile is unavailable."
		);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ProfileDocument(String profile, int embeddingDimension, List<ReferenceDocument> references) {

		ProfileDocument {
			references = references == null ? List.of() : List.copyOf(references);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReferenceDocument(String label, String sha256, float[] embedding) {
	}
}
