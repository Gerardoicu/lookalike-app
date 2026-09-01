package com.gerardoicu.lookalike.face;

import org.springframework.stereotype.Service;

@Service
class FacialAnalysisService {

	private final UploadedImageValidator imageValidator;
	private final FacialEmbeddingEngine embeddingEngine;

	FacialAnalysisService(UploadedImageValidator imageValidator, FacialEmbeddingEngine embeddingEngine) {
		this.imageValidator = imageValidator;
		this.embeddingEngine = embeddingEngine;
	}

	void analyze(byte[] imageBytes) {
		DecodedImage image = imageValidator.validate(imageBytes);
		try {
			embeddingEngine.extractEmbedding(image);
		}
		finally {
			image.mat().release();
		}
	}
}
