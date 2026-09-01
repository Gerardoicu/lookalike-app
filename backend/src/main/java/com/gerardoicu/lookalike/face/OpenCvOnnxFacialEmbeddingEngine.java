package com.gerardoicu.lookalike.face;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import com.gerardoicu.lookalike.api.ErrorCode;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class OpenCvOnnxFacialEmbeddingEngine implements FacialEmbeddingEngine {

	private static final String DETECTOR_MODEL = "face_detection_yunet_2023mar.onnx";
	private static final String RECOGNIZER_MODEL = "face_recognition_sface_2021dec.onnx";
	private static final int SFACE_INPUT_SIZE = 112;

	private final FaceAnalysisProperties properties;
	private EngineState state;

	OpenCvOnnxFacialEmbeddingEngine(FaceAnalysisProperties properties) {
		this.properties = properties;
	}

	@Override
	public synchronized FacialEmbedding extractEmbedding(DecodedImage image) {
		EngineState current = initializedState();
		Mat resized = new Mat();
		Mat faces = new Mat();
		Mat aligned = new Mat();
		Mat face = null;
		try {
			Imgproc.resize(image.mat(), resized, current.detectorInput());
			current.detector().setInputSize(current.detectorInput());

			current.detector().detect(resized, faces);
			if (faces.rows() == 0) {
				throw new FaceAnalysisException(ErrorCode.FACE_NO_USABLE_FACE, HttpStatus.BAD_REQUEST, "No usable face was detected.");
			}
			if (faces.rows() > 1) {
				throw new FaceAnalysisException(ErrorCode.FACE_MULTIPLE_USABLE_FACES, HttpStatus.BAD_REQUEST, "Multiple usable faces were detected.");
			}

			face = faces.row(0);
			double confidence = face.get(0, 14)[0];
			if (confidence < properties.minFaceConfidence()) {
				throw new FaceAnalysisException(ErrorCode.FACE_NO_USABLE_FACE, HttpStatus.BAD_REQUEST, "No usable face was detected.");
			}

			current.aligner().alignCrop(resized, face, aligned);
			if (aligned.empty()) {
				throw new FaceAnalysisException(ErrorCode.FACE_NO_USABLE_FACE, HttpStatus.BAD_REQUEST, "No usable face was detected.");
			}
			return new FacialEmbedding(extractOnnxRuntimeFeature(current.environment(), current.session(), aligned));
		}
		finally {
			if (face != null) {
				face.release();
			}
			resized.release();
			faces.release();
			aligned.release();
		}
	}

	private EngineState initializedState() {
		if (state == null) {
			state = initialize();
		}
		return state;
	}

	private EngineState initialize() {
		if (properties.modelDirectory().isBlank()) {
			throw unavailable();
		}
		Path modelDirectory = Path.of(properties.modelDirectory());
		Path detectorModel = modelDirectory.resolve(DETECTOR_MODEL);
		Path recognizerModel = modelDirectory.resolve(RECOGNIZER_MODEL);
		if (!Files.isRegularFile(detectorModel) || !Files.isRegularFile(recognizerModel)) {
			throw unavailable();
		}
		try {
			OpenCvLibrary.load();
			Size detectorInput = new Size(properties.detectorInputSize(), properties.detectorInputSize());
			FaceDetectorYN detector = FaceDetectorYN.create(
					detectorModel.toString(),
					"",
					detectorInput,
					properties.minFaceConfidence(),
					0.3f,
					5000
			);
			FaceRecognizerSF aligner = FaceRecognizerSF.create(recognizerModel.toString(), "");
			OrtEnvironment environment = OrtEnvironment.getEnvironment();
			OrtSession.SessionOptions options = new OrtSession.SessionOptions();
			options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
			OrtSession session = environment.createSession(recognizerModel.toString(), options);
			return new EngineState(environment, session, detector, aligner, detectorInput);
		}
		catch (OrtException | RuntimeException ex) {
			throw unavailable();
		}
	}

	private static float[] extractOnnxRuntimeFeature(OrtEnvironment environment, OrtSession session, Mat aligned) {
		float[] input = toSFaceTensor(aligned);
		String inputName = session.getInputNames().iterator().next();
		try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), new long[] { 1, 3, SFACE_INPUT_SIZE, SFACE_INPUT_SIZE });
				OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
			OnnxValue output = result.get(0);
			return flattenFloats(output.getValue());
		}
		catch (OrtException ex) {
			throw unavailable();
		}
	}

	private static float[] toSFaceTensor(Mat aligned) {
		Mat resized = new Mat();
		Mat rgb = new Mat();
		Mat floatImage = new Mat();
		try {
			Imgproc.resize(aligned, resized, new Size(SFACE_INPUT_SIZE, SFACE_INPUT_SIZE));
			Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_BGR2RGB);
			rgb.convertTo(floatImage, CvType.CV_32F);

			float[] tensor = new float[3 * SFACE_INPUT_SIZE * SFACE_INPUT_SIZE];
			int channelSize = SFACE_INPUT_SIZE * SFACE_INPUT_SIZE;
			for (int row = 0; row < SFACE_INPUT_SIZE; row++) {
				for (int col = 0; col < SFACE_INPUT_SIZE; col++) {
					double[] pixel = floatImage.get(row, col);
					int offset = row * SFACE_INPUT_SIZE + col;
					tensor[offset] = (float) pixel[0];
					tensor[channelSize + offset] = (float) pixel[1];
					tensor[channelSize * 2 + offset] = (float) pixel[2];
				}
			}
			return tensor;
		}
		finally {
			resized.release();
			rgb.release();
			floatImage.release();
		}
	}

	private static float[] flattenFloats(Object value) {
		if (value instanceof float[][] output) {
			return Arrays.copyOf(output[0], output[0].length);
		}
		if (value instanceof float[] output) {
			return Arrays.copyOf(output, output.length);
		}
		throw unavailable();
	}

	private static FaceAnalysisException unavailable() {
		return new FaceAnalysisException(
				ErrorCode.FACE_ANALYSIS_UNAVAILABLE,
				HttpStatus.SERVICE_UNAVAILABLE,
				"Facial analysis is unavailable."
		);
	}

	private record EngineState(
			OrtEnvironment environment,
			OrtSession session,
			FaceDetectorYN detector,
			FaceRecognizerSF aligner,
			Size detectorInput
	) {
	}
}
