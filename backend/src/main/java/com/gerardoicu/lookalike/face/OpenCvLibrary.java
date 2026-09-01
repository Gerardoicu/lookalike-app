package com.gerardoicu.lookalike.face;

import com.gerardoicu.lookalike.api.ErrorCode;
import nu.pattern.OpenCV;
import org.springframework.http.HttpStatus;

final class OpenCvLibrary {

	private static boolean loaded;

	private OpenCvLibrary() {
	}

	static synchronized void load() {
		if (loaded) {
			return;
		}
		try {
			OpenCV.loadLocally();
			loaded = true;
		}
		catch (RuntimeException | UnsatisfiedLinkError ex) {
			throw new FaceAnalysisException(
					ErrorCode.FACE_ANALYSIS_UNAVAILABLE,
					HttpStatus.SERVICE_UNAVAILABLE,
					"Facial analysis is unavailable."
			);
		}
	}
}
