package com.gerardoicu.lookalike.fedelobo;

public record FedeloboAnalysisResult(
		int similarityPercentage,
		FedeloboSimilarityLevel level,
		String phrase
) {
}
