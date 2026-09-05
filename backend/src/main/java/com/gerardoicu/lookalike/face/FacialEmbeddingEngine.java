package com.gerardoicu.lookalike.face;

public interface FacialEmbeddingEngine {

	FacialEmbedding extractEmbedding(DecodedImage image);
}
