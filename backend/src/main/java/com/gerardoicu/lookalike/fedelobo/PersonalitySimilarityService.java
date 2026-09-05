package com.gerardoicu.lookalike.fedelobo;

import com.gerardoicu.lookalike.face.FacialEmbedding;

public interface PersonalitySimilarityService {

	FedeloboAnalysisResult analyze(FacialEmbedding visitorEmbedding);
}
