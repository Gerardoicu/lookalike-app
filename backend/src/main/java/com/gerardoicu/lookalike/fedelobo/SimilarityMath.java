package com.gerardoicu.lookalike.fedelobo;

final class SimilarityMath {

	private SimilarityMath() {
	}

	static double cosine(float[] first, float[] second) {
		if (first.length != second.length || first.length == 0) {
			throw FedeloboProfileLoader.unavailable();
		}
		double dot = 0;
		for (int index = 0; index < first.length; index++) {
			dot += first[index] * second[index];
		}
		return dot;
	}

	static float[] normalized(float[] values) {
		if (values.length == 0) {
			throw FedeloboProfileLoader.unavailable();
		}
		double norm = 0;
		for (float value : values) {
			if (!Float.isFinite(value)) {
				throw FedeloboProfileLoader.unavailable();
			}
			norm += value * value;
		}
		if (norm == 0 || !Double.isFinite(norm)) {
			throw FedeloboProfileLoader.unavailable();
		}
		double divisor = Math.sqrt(norm);
		float[] normalized = new float[values.length];
		for (int index = 0; index < values.length; index++) {
			normalized[index] = (float) (values[index] / divisor);
		}
		return normalized;
	}
}
