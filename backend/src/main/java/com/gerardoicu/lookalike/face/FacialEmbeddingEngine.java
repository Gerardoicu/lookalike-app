package com.gerardoicu.lookalike.face;

interface FacialEmbeddingEngine {

	FacialEmbedding extractEmbedding(DecodedImage image);
}
