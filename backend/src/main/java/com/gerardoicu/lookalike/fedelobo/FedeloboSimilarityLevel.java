package com.gerardoicu.lookalike.fedelobo;

public enum FedeloboSimilarityLevel {

	VERY_LOW(0, "Barely a Fedelobo echo, but the vibe still counts."),
	LOW(20, "A light Fedelobo resemblance showed up."),
	MEDIUM(40, "There is a noticeable Fedelobo signal here."),
	HIGH(60, "Strong Fedelobo energy detected."),
	VERY_HIGH(80, "Peak Fedelobo mode.");

	private final int minimumPercentage;
	private final String phrase;

	FedeloboSimilarityLevel(int minimumPercentage, String phrase) {
		this.minimumPercentage = minimumPercentage;
		this.phrase = phrase;
	}

	static FedeloboSimilarityLevel fromPercentage(int percentage) {
		if (percentage >= VERY_HIGH.minimumPercentage) {
			return VERY_HIGH;
		}
		if (percentage >= HIGH.minimumPercentage) {
			return HIGH;
		}
		if (percentage >= MEDIUM.minimumPercentage) {
			return MEDIUM;
		}
		if (percentage >= LOW.minimumPercentage) {
			return LOW;
		}
		return VERY_LOW;
	}

	String phrase() {
		return phrase;
	}
}
