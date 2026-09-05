package com.gerardoicu.lookalike.fedelobo;

import java.util.Comparator;

import com.gerardoicu.lookalike.face.FacialEmbedding;
import org.springframework.stereotype.Service;

@Service
public class FedeloboSimilarityService implements PersonalitySimilarityService {

	private static final int TOP_REFERENCE_COUNT = 3;

	private final FedeloboProfileLoader profileLoader;
	private final SimilarityPercentageNormalizer normalizer;

	FedeloboSimilarityService(FedeloboProfileLoader profileLoader, SimilarityPercentageNormalizer normalizer) {
		this.profileLoader = profileLoader;
		this.normalizer = normalizer;
	}

	@Override
	public FedeloboAnalysisResult analyze(FacialEmbedding visitorEmbedding) {
		float[] visitor = SimilarityMath.normalized(visitorEmbedding.values());
		double rawSimilarity = profileLoader.load()
			.references()
			.stream()
			.map(reference -> SimilarityMath.cosine(visitor, reference.values()))
			.sorted(Comparator.reverseOrder())
			.limit(TOP_REFERENCE_COUNT)
			.mapToDouble(Double::doubleValue)
			.average()
			.orElseThrow(FedeloboProfileLoader::unavailable);
		int percentage = normalizer.normalize(rawSimilarity);
		FedeloboSimilarityLevel level = FedeloboSimilarityLevel.fromPercentage(percentage);
		return new FedeloboAnalysisResult(percentage, level, level.phrase());
	}
}
