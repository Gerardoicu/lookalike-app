package com.gerardoicu.lookalike.fedelobo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gerardoicu.lookalike.api.ErrorCode;
import com.gerardoicu.lookalike.face.FaceAnalysisException;
import org.junit.jupiter.api.Test;

class SimilarityPercentageNormalizerTests {

	private final SimilarityPercentageNormalizer normalizer = new SimilarityPercentageNormalizer();

	@Test
	void clampsAtLowerBoundary() {
		assertThat(normalizer.normalize(-1)).isZero();
		assertThat(normalizer.normalize(0.05)).isZero();
	}

	@Test
	void clampsAtUpperBoundary() {
		assertThat(normalizer.normalize(0.70)).isEqualTo(100);
		assertThat(normalizer.normalize(1)).isEqualTo(100);
	}

	@Test
	void mapsInteriorValuesDeterministically() {
		assertThat(normalizer.normalize(0.252910)).isEqualTo(31);
		assertThat(normalizer.normalize(0.375)).isEqualTo(50);
		assertThat(normalizer.normalize(0.625806)).isEqualTo(89);
	}

	@Test
	void rejectsNonFiniteValues() {
		assertThatThrownBy(() -> normalizer.normalize(Double.NaN))
			.isInstanceOf(FaceAnalysisException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.FEDELOBO_PROFILE_UNAVAILABLE);
	}
}
