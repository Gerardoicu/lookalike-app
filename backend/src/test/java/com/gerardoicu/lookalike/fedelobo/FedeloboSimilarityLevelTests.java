package com.gerardoicu.lookalike.fedelobo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FedeloboSimilarityLevelTests {

	@Test
	void classifiesBoundaryValues() {
		assertThat(FedeloboSimilarityLevel.fromPercentage(0)).isEqualTo(FedeloboSimilarityLevel.VERY_LOW);
		assertThat(FedeloboSimilarityLevel.fromPercentage(19)).isEqualTo(FedeloboSimilarityLevel.VERY_LOW);
		assertThat(FedeloboSimilarityLevel.fromPercentage(20)).isEqualTo(FedeloboSimilarityLevel.LOW);
		assertThat(FedeloboSimilarityLevel.fromPercentage(39)).isEqualTo(FedeloboSimilarityLevel.LOW);
		assertThat(FedeloboSimilarityLevel.fromPercentage(40)).isEqualTo(FedeloboSimilarityLevel.MEDIUM);
		assertThat(FedeloboSimilarityLevel.fromPercentage(59)).isEqualTo(FedeloboSimilarityLevel.MEDIUM);
		assertThat(FedeloboSimilarityLevel.fromPercentage(60)).isEqualTo(FedeloboSimilarityLevel.HIGH);
		assertThat(FedeloboSimilarityLevel.fromPercentage(79)).isEqualTo(FedeloboSimilarityLevel.HIGH);
		assertThat(FedeloboSimilarityLevel.fromPercentage(80)).isEqualTo(FedeloboSimilarityLevel.VERY_HIGH);
		assertThat(FedeloboSimilarityLevel.fromPercentage(100)).isEqualTo(FedeloboSimilarityLevel.VERY_HIGH);
	}

	@Test
	void selectsDeterministicPhrases() {
		assertThat(FedeloboSimilarityLevel.VERY_LOW.phrase()).isEqualTo("Barely a Fedelobo echo, but the vibe still counts.");
		assertThat(FedeloboSimilarityLevel.LOW.phrase()).isEqualTo("A light Fedelobo resemblance showed up.");
		assertThat(FedeloboSimilarityLevel.MEDIUM.phrase()).isEqualTo("There is a noticeable Fedelobo signal here.");
		assertThat(FedeloboSimilarityLevel.HIGH.phrase()).isEqualTo("Strong Fedelobo energy detected.");
		assertThat(FedeloboSimilarityLevel.VERY_HIGH.phrase()).isEqualTo("Peak Fedelobo mode.");
	}
}
