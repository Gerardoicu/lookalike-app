package com.gerardoicu.lookalike.fedelobo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;

import com.gerardoicu.lookalike.api.ErrorCode;
import com.gerardoicu.lookalike.face.FaceAnalysisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class FedeloboProfileLoaderTests {

	@TempDir
	private Path tempDir;

	@Test
	void loadsAndNormalizesValidProfile() throws Exception {
		Path profile = writeProfile(reference(1, 2), reference(2, 3), reference(3, 4));

		FedeloboProfile loaded = loader(profile).load();

		assertThat(loaded.references()).hasSize(3);
		assertThat(norm(loaded.references().getFirst().values())).isCloseTo(1.0, within(0.000001));
	}

	@Test
	void missingProfilePathFailsClosed() {
		assertUnavailable(loader(tempDir.resolve("missing.json"))::load);
	}

	@Test
	void corruptProfileFailsClosed() throws Exception {
		Path profile = tempDir.resolve("fedelobo-profile.json");
		Files.writeString(profile, "{not-json");

		assertUnavailable(loader(profile)::load);
	}

	@Test
	void profileWithFewerThanThreeReferencesFailsClosed() throws Exception {
		Path profile = writeProfile(reference(1, 2), reference(2, 3));

		assertUnavailable(loader(profile)::load);
	}

	@Test
	void wrongEmbeddingDimensionFailsClosed() throws Exception {
		Path profile = tempDir.resolve("fedelobo-profile.json");
		Files.writeString(profile, """
				{"profile":"FEDELOBO","embeddingDimension":128,"references":[
				{"label":"one","sha256":"a","embedding":[1.0]},
				{"label":"two","sha256":"b","embedding":[1.0]},
				{"label":"three","sha256":"c","embedding":[1.0]}
				]}""");

		assertUnavailable(loader(profile)::load);
	}

	@Test
	void nonFiniteEmbeddingValueFailsClosed() throws Exception {
		Path profile = writeProfile(reference(1, Float.NaN), reference(2, 3), reference(3, 4));

		assertUnavailable(loader(profile)::load);
	}

	@Test
	void zeroLengthOrZeroVectorEmbeddingFailsClosed() throws Exception {
		Path profile = writeProfile(zeroVector(), reference(2, 3), reference(3, 4));

		assertUnavailable(loader(profile)::load);
	}

	private FedeloboProfileLoader loader(Path profile) {
		return new FedeloboProfileLoader(new FedeloboProperties(profile.toString()), new ObjectMapper());
	}

	private Path writeProfile(float[]... references) throws Exception {
		StringBuilder json = new StringBuilder("""
				{"profile":"FEDELOBO","embeddingDimension":128,"references":[""");
		for (int index = 0; index < references.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append("{\"label\":\"ref-").append(index).append("\",\"sha256\":\"sha-").append(index).append("\",\"embedding\":");
			json.append(new ObjectMapper().writeValueAsString(references[index]));
			json.append('}');
		}
		json.append("]}");
		Path profile = tempDir.resolve("fedelobo-profile.json");
		Files.writeString(profile, json.toString());
		return profile;
	}

	private static float[] reference(int first, float second) {
		float[] values = new float[128];
		values[0] = first;
		values[1] = second;
		return values;
	}

	private static float[] zeroVector() {
		return new float[128];
	}

	private static double norm(float[] values) {
		double sum = 0;
		for (float value : values) {
			sum += value * value;
		}
		return Math.sqrt(sum);
	}

	private static void assertUnavailable(ThrowingCall call) {
		assertThatThrownBy(call::run)
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FEDELOBO_PROFILE_UNAVAILABLE);
	}

	@FunctionalInterface
	private interface ThrowingCall {

		void run() throws Exception;
	}
}
