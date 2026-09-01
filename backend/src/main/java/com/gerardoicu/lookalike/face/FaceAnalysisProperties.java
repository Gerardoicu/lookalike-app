package com.gerardoicu.lookalike.face;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lookalike.face.analysis")
public record FaceAnalysisProperties(
		long maxImageBytes,
		int maxWidth,
		int maxHeight,
		long maxPixels,
		int detectorInputSize,
		float minFaceConfidence,
		String modelDirectory
) {

	public FaceAnalysisProperties {
		maxImageBytes = maxImageBytes <= 0 ? 6_291_456L : maxImageBytes;
		maxWidth = maxWidth <= 0 ? 4_096 : maxWidth;
		maxHeight = maxHeight <= 0 ? 4_096 : maxHeight;
		maxPixels = maxPixels <= 0 ? 12_000_000L : maxPixels;
		detectorInputSize = detectorInputSize <= 0 ? 320 : detectorInputSize;
		minFaceConfidence = minFaceConfidence <= 0 ? 0.9f : minFaceConfidence;
		modelDirectory = modelDirectory == null ? "" : modelDirectory;
	}
}
