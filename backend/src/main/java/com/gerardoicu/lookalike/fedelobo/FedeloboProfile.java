package com.gerardoicu.lookalike.fedelobo;

import java.util.List;

record FedeloboProfile(List<ReferenceEmbedding> references) {

	static final int EMBEDDING_DIMENSION = 128;
	static final int MINIMUM_REFERENCE_COUNT = 3;

	FedeloboProfile {
		references = List.copyOf(references);
	}

	record ReferenceEmbedding(String label, String sha256, float[] values) {

		ReferenceEmbedding {
			values = values.clone();
		}

		@Override
		public float[] values() {
			return values.clone();
		}
	}
}
