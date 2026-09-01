package com.gerardoicu.lookalike.face;

import java.util.Arrays;

record FacialEmbedding(float[] values) {

	FacialEmbedding {
		values = Arrays.copyOf(values, values.length);
	}

	@Override
	public float[] values() {
		return Arrays.copyOf(values, values.length);
	}
}
