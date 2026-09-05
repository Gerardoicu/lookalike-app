package com.gerardoicu.lookalike.fedelobo;

import org.springframework.stereotype.Component;

@Component
class SimilarityPercentageNormalizer {

	private static final double LOWER_ANCHOR = 0.05;
	private static final double UPPER_ANCHOR = 0.70;

	int normalize(double rawSimilarity) {
		if (!Double.isFinite(rawSimilarity)) {
			throw FedeloboProfileLoader.unavailable();
		}
		if (rawSimilarity <= LOWER_ANCHOR) {
			return 0;
		}
		if (rawSimilarity >= UPPER_ANCHOR) {
			return 100;
		}
		return (int) Math.round((rawSimilarity - LOWER_ANCHOR) / (UPPER_ANCHOR - LOWER_ANCHOR) * 100);
	}
}
