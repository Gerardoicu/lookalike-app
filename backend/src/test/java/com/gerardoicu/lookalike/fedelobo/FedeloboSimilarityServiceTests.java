package com.gerardoicu.lookalike.fedelobo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.gerardoicu.lookalike.face.FacialEmbedding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class FedeloboSimilarityServiceTests {

	@TempDir
	private Path tempDir;

	@Test
	void usesTopThreeMeanAggregationForSimilarity() throws Exception {
		Path profile = writeProfile(
				vectorForCosine(0.18),
				vectorForCosine(0.31),
				vectorForCosine(0.44),
				vectorForCosine(0.01)
		);
		FedeloboSimilarityService service = new FedeloboSimilarityService(
				new FedeloboProfileLoader(new FedeloboProperties(profile.toString()), new ObjectMapper()),
				new SimilarityPercentageNormalizer()
		);

		FedeloboAnalysisResult result = service.analyze(new FacialEmbedding(vectorForCosine(1)));

		assertThat(result.similarityPercentage()).isEqualTo(40);
		assertThat(result.level()).isEqualTo(FedeloboSimilarityLevel.MEDIUM);
		assertThat(result.phrase()).isEqualTo("There is a noticeable Fedelobo signal here.");
	}

	private Path writeProfile(float[]... references) throws Exception {
		StringBuilder json = new StringBuilder("""
				{"profile":"FEDELOBO","embeddingDimension":128,"references":[""");
		ObjectMapper objectMapper = new ObjectMapper();
		for (int index = 0; index < references.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append("{\"label\":\"ref-").append(index).append("\",\"sha256\":\"sha-").append(index).append("\",\"embedding\":");
			json.append(objectMapper.writeValueAsString(references[index]));
			json.append('}');
		}
		json.append("]}");
		Path profile = tempDir.resolve("fedelobo-profile.json");
		Files.writeString(profile, json.toString());
		return profile;
	}

	private static float[] vectorForCosine(double cosine) {
		float[] vector = new float[128];
		vector[0] = (float) cosine;
		vector[1] = (float) Math.sqrt(1 - cosine * cosine);
		return vector;
	}
}
