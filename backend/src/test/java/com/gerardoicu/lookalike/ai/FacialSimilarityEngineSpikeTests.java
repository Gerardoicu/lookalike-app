package com.gerardoicu.lookalike.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.lang.management.ManagementFactory;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;
import org.junit.jupiter.api.Test;

class FacialSimilarityEngineSpikeTests {

	private static final String ASSET_DIR = "AI001_ASSET_DIR";
	private static final String DETECTOR_MODEL = "models/face_detection_yunet_2023mar.onnx";
	private static final String RECOGNIZER_MODEL = "models/face_recognition_sface_2021dec.onnx";
	private static final Size DETECTOR_INPUT = new Size(320, 320);
	private static final int SFACE_INPUT_SIZE = 112;
	private static final int WARMUP_ITERATIONS = 2;
	private static final int MEASURED_ITERATIONS = 5;

	@Test
	void validatesFacialSimilarityCandidatesWhenLocalAssetsAreAvailable() throws OrtException {
		Path assetDir = configuredAssetDirectory();
		assumeTrue(assetDir != null, () -> ASSET_DIR + " is not configured");
		assumeTrue(Files.isDirectory(assetDir), () -> assetDir + " does not exist");

		Path detectorModel = assetDir.resolve(DETECTOR_MODEL);
		Path recognizerModel = assetDir.resolve(RECOGNIZER_MODEL);
		Path samePerson1 = assetDir.resolve("fixtures/same-person-1.jpg");
		Path samePerson2 = assetDir.resolve("fixtures/same-person-2.jpg");
		Path differentPerson1 = assetDir.resolve("fixtures/different-person-1.jpg");
		Path differentPerson2 = assetDir.resolve("fixtures/different-person-2.jpg");
		List<Path> requiredFiles = List.of(detectorModel, recognizerModel, samePerson1, samePerson2, differentPerson1, differentPerson2);
		assumeTrue(requiredFiles.stream().allMatch(Files::isRegularFile), () -> "AI-001 local assets are incomplete: " + requiredFiles);
		List<Path> imagePaths = List.of(samePerson1, samePerson2, differentPerson1, differentPerson2);

		MemorySample beforeLoad = currentMemory();
		long loadStarted = System.nanoTime();
		OpenCV.loadLocally();

		FaceDetectorYN detector = FaceDetectorYN.create(
				detectorModel.toString(),
				"",
				DETECTOR_INPUT,
				0.9f,
				0.3f,
				5000
		);
		FaceRecognizerSF aligner = FaceRecognizerSF.create(recognizerModel.toString(), "");
		Net recognizer = Dnn.readNetFromONNX(recognizerModel.toString());
		OrtEnvironment environment = OrtEnvironment.getEnvironment();
		MemorySample afterOpenCvLoad = currentMemory();

		try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
			options.setSessionLogLevel(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
			try (OrtSession session = environment.createSession(recognizerModel.toString(), options)) {
				MemorySample afterModelLoad = currentMemory();
				Duration coldLoad = Duration.ofNanos(System.nanoTime() - loadStarted);

				ProcessedFace sameProcessed1 = processImage(environment, session, detector, aligner, samePerson1);
				ProcessedFace sameProcessed2 = processImage(environment, session, detector, aligner, samePerson2);
				ProcessedFace differentProcessed1 = processImage(environment, session, detector, aligner, differentPerson1);
				ProcessedFace differentProcessed2 = processImage(environment, session, detector, aligner, differentPerson2);

				Mat openCvSameFeature1 = extractOpenCvDnnFeature(recognizer, sameProcessed1.aligned());
				Mat openCvSameFeature2 = extractOpenCvDnnFeature(recognizer, sameProcessed2.aligned());
				Mat openCvDifferentFeature1 = extractOpenCvDnnFeature(recognizer, differentProcessed1.aligned());
				Mat openCvDifferentFeature2 = extractOpenCvDnnFeature(recognizer, differentProcessed2.aligned());

				System.out.printf("%nOpenCV DNN feature shapes: same1=%s, same2=%s, different1=%s, different2=%s%n",
						shapeOf(openCvSameFeature1),
						shapeOf(openCvSameFeature2),
						shapeOf(openCvDifferentFeature1),
						shapeOf(openCvDifferentFeature2)
				);
				printOpenCvComparison("OpenCV DNN", openCvSameFeature1, openCvSameFeature2, openCvDifferentFeature1, openCvDifferentFeature2);

				for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
					for (Path imagePath : imagePaths) {
						processImage(environment, session, detector, aligner, imagePath);
					}
				}

				TimingStats decodeStats = new TimingStats();
				TimingStats detectionAlignmentStats = new TimingStats();
				TimingStats embeddingStats = new TimingStats();
				TimingStats imageTotalStats = new TimingStats();
				TimingStats comparisonStats = new TimingStats();
				MemorySample peak = afterModelLoad;
				for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
					ProcessedFace measuredSame1 = processImage(environment, session, detector, aligner, samePerson1);
					ProcessedFace measuredSame2 = processImage(environment, session, detector, aligner, samePerson2);
					ProcessedFace measuredDifferent1 = processImage(environment, session, detector, aligner, differentPerson1);
					ProcessedFace measuredDifferent2 = processImage(environment, session, detector, aligner, differentPerson2);
					List<ProcessedFace> measuredFaces = List.of(measuredSame1, measuredSame2, measuredDifferent1, measuredDifferent2);
					for (ProcessedFace face : measuredFaces) {
						decodeStats.add(face.timing().decodeNanos());
						detectionAlignmentStats.add(face.timing().detectionAlignmentNanos());
						embeddingStats.add(face.timing().embeddingNanos());
						imageTotalStats.add(face.timing().totalNanos());
					}
					long comparisonStarted = System.nanoTime();
					cosine(measuredSame1.embedding(), measuredSame2.embedding());
					cosine(measuredSame1.embedding(), measuredDifferent1.embedding());
					cosine(measuredSame1.embedding(), measuredDifferent2.embedding());
					cosine(measuredSame2.embedding(), measuredDifferent1.embedding());
					cosine(measuredSame2.embedding(), measuredDifferent2.embedding());
					l2Distance(measuredSame1.embedding(), measuredSame2.embedding());
					l2Distance(measuredSame1.embedding(), measuredDifferent1.embedding());
					l2Distance(measuredSame1.embedding(), measuredDifferent2.embedding());
					comparisonStats.add(System.nanoTime() - comparisonStarted);
					peak = peak.max(currentMemory());
				}

				float[] sameFeature1 = sameProcessed1.embedding();
				float[] sameFeature2 = sameProcessed2.embedding();
				float[] differentFeature1 = differentProcessed1.embedding();
				float[] differentFeature2 = differentProcessed2.embedding();
				double sameCosine = cosine(sameFeature1, sameFeature2);
				double differentCosine1 = cosine(sameFeature1, differentFeature1);
				double differentCosine2 = cosine(sameFeature1, differentFeature2);
				double differentCosine3 = cosine(sameFeature2, differentFeature1);
				double differentCosine4 = cosine(sameFeature2, differentFeature2);
				double sameL2 = l2Distance(sameFeature1, sameFeature2);
				double differentL21 = l2Distance(sameFeature1, differentFeature1);
				double differentL22 = l2Distance(sameFeature1, differentFeature2);

				System.out.printf("%nONNX Runtime facial similarity spike:%n");
				System.out.printf("embedding sample same1=%s%n", sampleOf(sameFeature1));
				System.out.printf("embedding sample same2=%s%n", sampleOf(sameFeature2));
				System.out.printf("embedding sample different1=%s%n", sampleOf(differentFeature1));
				System.out.printf("embedding sample different2=%s%n", sampleOf(differentFeature2));
				System.out.printf("same-person cosine=%.6f, l2=%.6f%n", sameCosine, sameL2);
				System.out.printf("different-person cosine #1=%.6f, #2=%.6f, #3=%.6f, #4=%.6f%n",
						differentCosine1,
						differentCosine2,
						differentCosine3,
						differentCosine4
				);
				System.out.printf("different-person l2 #1=%.6f, #2=%.6f%n", differentL21, differentL22);
				System.out.printf("cold runtime/model load ms=%d%n", coldLoad.toMillis());
				System.out.printf("warm decode avg/range ms=%s%n", decodeStats.describeMillis());
				System.out.printf("warm detection+alignment avg/range ms=%s%n", detectionAlignmentStats.describeMillis());
				System.out.printf("warm ONNX embedding avg/range ms=%s%n", embeddingStats.describeMillis());
				System.out.printf("warm comparison batch avg/range ms=%s%n", comparisonStats.describeMillis());
				System.out.printf("warm per-image total avg/range ms=%s%n", imageTotalStats.describeMillis());
				System.out.printf("memory before load=%s%n", beforeLoad);
				System.out.printf("memory after OpenCV load=%s%n", afterOpenCvLoad);
				System.out.printf("memory after model load=%s%n", afterModelLoad);
				System.out.printf("memory observed peak=%s%n", peak);

				assertThat(sameFeature1).hasSize(128);
				assertThat(sameFeature2).hasSize(128);
				assertThat(differentFeature1).hasSize(128);
				assertThat(differentFeature2).hasSize(128);
				assertFinite(sameCosine, differentCosine1, differentCosine2, differentCosine3, differentCosine4, sameL2, differentL21, differentL22);
				assertThat(sameFeature1).isNotEqualTo(sameFeature2);
				assertThat(sameFeature1).isNotEqualTo(differentFeature1);
				assertThat(sameCosine).isGreaterThan(differentCosine1);
				assertThat(sameCosine).isGreaterThan(differentCosine2);
				assertThat(sameCosine).isGreaterThan(differentCosine3);
				assertThat(sameCosine).isGreaterThan(differentCosine4);
				assertThat(sameL2).isLessThan(differentL21);
				assertThat(sameL2).isLessThan(differentL22);
			}
		}
	}

	private static Path configuredAssetDirectory() {
		String configured = System.getenv(ASSET_DIR);
		if (configured == null || configured.isBlank()) {
			return null;
		}
		return Path.of(configured);
	}

	private static ProcessedFace processImage(OrtEnvironment environment, OrtSession session, FaceDetectorYN detector, FaceRecognizerSF aligner, Path imagePath) throws OrtException {
		long imageStarted = System.nanoTime();
		long decodeStarted = System.nanoTime();
		Mat image = Imgcodecs.imread(imagePath.toString());
		assertThat(image.empty()).as("decoded image %s", imagePath).isFalse();
		long decodeNanos = System.nanoTime() - decodeStarted;

		long alignmentStarted = System.nanoTime();
		Mat resized = new Mat();
		Imgproc.resize(image, resized, DETECTOR_INPUT);
		detector.setInputSize(DETECTOR_INPUT);

		Mat faces = new Mat();
		detector.detect(resized, faces);
		assertThat(faces.rows()).as("detected face count in %s", imagePath).isEqualTo(1);

		Mat face = faces.row(0);
		Mat aligned = new Mat();
		aligner.alignCrop(resized, face, aligned);
		assertThat(aligned.empty()).as("aligned face %s", imagePath).isFalse();
		writeDebugAlignedFace(imagePath, aligned);
		long detectionAlignmentNanos = System.nanoTime() - alignmentStarted;

		long embeddingStarted = System.nanoTime();
		float[] embedding = extractOnnxRuntimeFeature(environment, session, aligned);
		long embeddingNanos = System.nanoTime() - embeddingStarted;

		return new ProcessedFace(
				aligned,
				embedding,
				new ImageTiming(decodeNanos, detectionAlignmentNanos, embeddingNanos, System.nanoTime() - imageStarted)
		);
	}

	private static Mat extractOpenCvDnnFeature(Net recognizer, Mat aligned) {
		Mat blob = Dnn.blobFromImage(aligned, 1.0, new Size(SFACE_INPUT_SIZE, SFACE_INPUT_SIZE), new Scalar(0, 0, 0), true, false, CvType.CV_32F);
		recognizer.setInput(blob);
		Mat feature = recognizer.forward();
		assertThat(feature.empty()).as("OpenCV DNN feature vector").isFalse();
		return feature;
	}

	private static float[] extractOnnxRuntimeFeature(OrtEnvironment environment, OrtSession session, Mat aligned) throws OrtException {
		float[] input = toSFaceTensor(aligned);
		String inputName = session.getInputNames().iterator().next();
		try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), new long[] { 1, 3, SFACE_INPUT_SIZE, SFACE_INPUT_SIZE });
				OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
			OnnxValue output = result.get(0);
			return flattenFloats(output.getValue());
		}
	}

	private static float[] toSFaceTensor(Mat aligned) {
		Mat resized = new Mat();
		Imgproc.resize(aligned, resized, new Size(SFACE_INPUT_SIZE, SFACE_INPUT_SIZE));
		Mat rgb = new Mat();
		Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_BGR2RGB);
		Mat floatImage = new Mat();
		rgb.convertTo(floatImage, CvType.CV_32F);

		float[] tensor = new float[3 * SFACE_INPUT_SIZE * SFACE_INPUT_SIZE];
		double[] pixel;
		int channelSize = SFACE_INPUT_SIZE * SFACE_INPUT_SIZE;
		for (int row = 0; row < SFACE_INPUT_SIZE; row++) {
			for (int col = 0; col < SFACE_INPUT_SIZE; col++) {
				pixel = floatImage.get(row, col);
				int offset = row * SFACE_INPUT_SIZE + col;
				tensor[offset] = (float) pixel[0];
				tensor[channelSize + offset] = (float) pixel[1];
				tensor[channelSize * 2 + offset] = (float) pixel[2];
			}
		}
		return tensor;
	}

	private static float[] flattenFloats(Object value) {
		if (value instanceof float[][] output) {
			return Arrays.copyOf(output[0], output[0].length);
		}
		if (value instanceof float[] output) {
			return Arrays.copyOf(output, output.length);
		}
		throw new IllegalStateException("Unexpected ONNX Runtime output type: " + value.getClass());
	}

	private static double cosine(Mat first, Mat second) {
		return first.dot(second) / (Core.norm(first) * Core.norm(second));
	}

	private static double cosine(float[] first, float[] second) {
		double dot = 0;
		double firstNorm = 0;
		double secondNorm = 0;
		for (int index = 0; index < first.length; index++) {
			dot += first[index] * second[index];
			firstNorm += first[index] * first[index];
			secondNorm += second[index] * second[index];
		}
		return dot / (Math.sqrt(firstNorm) * Math.sqrt(secondNorm));
	}

	private static double l2Distance(Mat first, Mat second) {
		Mat difference = new Mat();
		Core.subtract(normalized(first), normalized(second), difference);
		return Core.norm(difference);
	}

	private static double l2Distance(float[] first, float[] second) {
		float[] normalizedFirst = normalized(first);
		float[] normalizedSecond = normalized(second);
		double sum = 0;
		for (int index = 0; index < normalizedFirst.length; index++) {
			double difference = normalizedFirst[index] - normalizedSecond[index];
			sum += difference * difference;
		}
		return Math.sqrt(sum);
	}

	private static Mat normalized(Mat feature) {
		Mat normalized = new Mat();
		Core.normalize(feature, normalized);
		return normalized;
	}

	private static float[] normalized(float[] feature) {
		double norm = 0;
		for (float value : feature) {
			norm += value * value;
		}
		double divisor = Math.sqrt(norm);
		float[] normalized = new float[feature.length];
		for (int index = 0; index < feature.length; index++) {
			normalized[index] = (float) (feature[index] / divisor);
		}
		return normalized;
	}

	private static void assertFinite(double... values) {
		for (double value : values) {
			assertThat(Double.isFinite(value)).as("finite comparison value %s", value).isTrue();
		}
	}

	private static String shapeOf(Mat feature) {
		return feature.rows() + "x" + feature.cols() + " type=" + feature.type();
	}

	private static void writeDebugAlignedFace(Path imagePath, Mat aligned) {
		Path outputDirectory = Path.of("target", "ai-001-debug");
		try {
			Files.createDirectories(outputDirectory);
			Imgcodecs.imwrite(outputDirectory.resolve(imagePath.getFileName()).toString(), aligned);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to write aligned face debug image for " + imagePath, ex);
		}
	}

	private static void printOpenCvComparison(String label, Mat sameFeature1, Mat sameFeature2, Mat differentFeature1, Mat differentFeature2) {
		double sameCosine = cosine(sameFeature1, sameFeature2);
		double differentCosine1 = cosine(sameFeature1, differentFeature1);
		double differentCosine2 = cosine(sameFeature1, differentFeature2);
		double sameL2 = l2Distance(sameFeature1, sameFeature2);
		double differentL2 = l2Distance(sameFeature1, differentFeature1);
		System.out.printf("%s feature norms: same1=%.6f, same2=%.6f, different1=%.6f, different2=%.6f%n",
				label,
				Core.norm(sameFeature1),
				Core.norm(sameFeature2),
				Core.norm(differentFeature1),
				Core.norm(differentFeature2)
		);
		System.out.printf("%s same-person cosine=%.6f, l2=%.6f%n", label, sameCosine, sameL2);
		System.out.printf("%s different-person cosine #1=%.6f, #2=%.6f, l2=%.6f%n", label, differentCosine1, differentCosine2, differentL2);
	}

	private static String sampleOf(float[] embedding) {
		Map<Integer, Float> sample = new LinkedHashMap<>();
		for (int index = 0; index < Math.min(8, embedding.length); index++) {
			sample.put(index, embedding[index]);
		}
		return sample.toString();
	}

	private static MemorySample currentMemory() {
		long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		long nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
		OptionalLong committedVirtual = OptionalLong.empty();
		if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
			committedVirtual = OptionalLong.of(operatingSystem.getCommittedVirtualMemorySize());
		}
		return new MemorySample(heap, nonHeap, committedVirtual);
	}

	private record ProcessedFace(Mat aligned, float[] embedding, ImageTiming timing) {
	}

	private record ImageTiming(long decodeNanos, long detectionAlignmentNanos, long embeddingNanos, long totalNanos) {
	}

	private record MemorySample(long heapUsedBytes, long nonHeapUsedBytes, OptionalLong committedVirtualBytes) {

		MemorySample max(MemorySample other) {
			long thisCommitted = committedVirtualBytes.orElse(0);
			long otherCommitted = other.committedVirtualBytes().orElse(0);
			return new MemorySample(
					Math.max(heapUsedBytes, other.heapUsedBytes()),
					Math.max(nonHeapUsedBytes, other.nonHeapUsedBytes()),
					thisCommitted > 0 || otherCommitted > 0 ? OptionalLong.of(Math.max(thisCommitted, otherCommitted)) : OptionalLong.empty()
			);
		}

		@Override
		public String toString() {
			String committed = committedVirtualBytes.isPresent() ? ", committed virtual MB=" + toMegabytes(committedVirtualBytes.getAsLong()) : "";
			return "heap MB=" + toMegabytes(heapUsedBytes) + ", non-heap MB=" + toMegabytes(nonHeapUsedBytes) + committed;
		}
	}

	private static final class TimingStats {

		private long count;
		private long totalNanos;
		private long minNanos = Long.MAX_VALUE;
		private long maxNanos;

		void add(long nanos) {
			count++;
			totalNanos += nanos;
			minNanos = Math.min(minNanos, nanos);
			maxNanos = Math.max(maxNanos, nanos);
		}

		String describeMillis() {
			return "%.2f [%d-%d]".formatted((totalNanos / (double) count) / 1_000_000.0, toMillis(minNanos), toMillis(maxNanos));
		}
	}

	private static long toMillis(long nanos) {
		return Duration.ofNanos(nanos).toMillis();
	}

	private static long toMegabytes(long bytes) {
		return bytes / 1024 / 1024;
	}
}
