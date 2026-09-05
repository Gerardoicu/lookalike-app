package com.gerardoicu.lookalike.face;

import java.util.Arrays;

public record FacialEmbedding(float[] values) {

	public FacialEmbedding {
		values = Arrays.copyOf(values, values.length);
	}

	@Override
	public float[] values() {
		return Arrays.copyOf(values, values.length);
	}
}
