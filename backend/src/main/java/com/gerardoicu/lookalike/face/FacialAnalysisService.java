package com.gerardoicu.lookalike.face;

import com.gerardoicu.lookalike.fedelobo.FedeloboAnalysisResult;
import com.gerardoicu.lookalike.fedelobo.PersonalitySimilarityService;
import org.springframework.stereotype.Service;

@Service
class FacialAnalysisService {

	private final UploadedImageValidator imageValidator;
	private final FacialEmbeddingEngine embeddingEngine;
	private final PersonalitySimilarityService fedeloboSimilarityService;

	FacialAnalysisService(
			UploadedImageValidator imageValidator,
			FacialEmbeddingEngine embeddingEngine,
			PersonalitySimilarityService fedeloboSimilarityService
	) {
		this.imageValidator = imageValidator;
		this.embeddingEngine = embeddingEngine;
		this.fedeloboSimilarityService = fedeloboSimilarityService;
	}

	FedeloboAnalysisResult analyze(byte[] imageBytes) {
		DecodedImage image = imageValidator.validate(imageBytes);
		try {
			FacialEmbedding embedding = embeddingEngine.extractEmbedding(image);
			return fedeloboSimilarityService.analyze(embedding);
		}
		finally {
			image.mat().release();
		}
	}
}
