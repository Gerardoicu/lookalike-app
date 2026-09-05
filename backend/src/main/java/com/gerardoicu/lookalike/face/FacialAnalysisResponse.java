package com.gerardoicu.lookalike.face;

import com.gerardoicu.lookalike.fedelobo.FedeloboAnalysisResult;

public record FacialAnalysisResponse(
		boolean successful,
		int similarityPercentage,
		String level,
		String phrase
) {

	static FacialAnalysisResponse from(FedeloboAnalysisResult result) {
		return new FacialAnalysisResponse(true, result.similarityPercentage(), result.level().name(), result.phrase());
	}
}
