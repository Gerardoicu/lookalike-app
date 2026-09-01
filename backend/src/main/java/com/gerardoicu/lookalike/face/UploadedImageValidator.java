package com.gerardoicu.lookalike.face;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import com.gerardoicu.lookalike.api.ErrorCode;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class UploadedImageValidator {

	private static final int JPEG_MARKER_PREFIX = 0xFF;
	private static final int JPEG_START_OF_IMAGE = 0xD8;

	private final FaceAnalysisProperties properties;

	UploadedImageValidator(FaceAnalysisProperties properties) {
		this.properties = properties;
		ImageIO.setUseCache(false);
	}

	DecodedImage validate(byte[] bytes) {
		if (bytes.length == 0) {
			throw invalid(ErrorCode.FACE_IMAGE_EMPTY, HttpStatus.BAD_REQUEST, "Uploaded image is empty.");
		}
		if (bytes.length > properties.maxImageBytes()) {
			throw invalid(ErrorCode.FACE_IMAGE_OVERSIZED, HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded image is too large.");
		}
		if (!hasJpegSignature(bytes)) {
			throw invalid(ErrorCode.FACE_IMAGE_UNSUPPORTED_FORMAT, HttpStatus.BAD_REQUEST, "Uploaded image format is not supported.");
		}

		ImageDimensions dimensions = readDimensions(bytes);
		validateDimensions(dimensions);
		OpenCvLibrary.load();
		MatOfByte encoded = new MatOfByte(bytes);
		Mat decoded;
		try {
			decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR);
		}
		finally {
			encoded.release();
		}
		if (decoded.empty()) {
			decoded.release();
			throw invalid(ErrorCode.FACE_IMAGE_CORRUPT, HttpStatus.BAD_REQUEST, "Uploaded image is corrupt.");
		}
		try {
			if (decoded.width() != dimensions.width() || decoded.height() != dimensions.height()) {
				validateDimensions(new ImageDimensions(decoded.width(), decoded.height()));
			}
		}
		catch (RuntimeException ex) {
			decoded.release();
			throw ex;
		}
		return new DecodedImage(decoded, decoded.width(), decoded.height());
	}

	private ImageDimensions readDimensions(byte[] bytes) {
		try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw invalid(ErrorCode.FACE_IMAGE_CORRUPT, HttpStatus.BAD_REQUEST, "Uploaded image is corrupt.");
			}
			ImageReader reader = readers.next();
			try {
				if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) {
					throw invalid(ErrorCode.FACE_IMAGE_UNSUPPORTED_FORMAT, HttpStatus.BAD_REQUEST, "Uploaded image format is not supported.");
				}
				reader.setInput(input, true, true);
				return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof FaceAnalysisException faceAnalysisException) {
				throw faceAnalysisException;
			}
			throw invalid(ErrorCode.FACE_IMAGE_CORRUPT, HttpStatus.BAD_REQUEST, "Uploaded image is corrupt.");
		}
	}

	private void validateDimensions(ImageDimensions dimensions) {
		long pixels = (long) dimensions.width() * dimensions.height();
		if (dimensions.width() <= 0
				|| dimensions.height() <= 0
				|| dimensions.width() > properties.maxWidth()
				|| dimensions.height() > properties.maxHeight()
				|| pixels > properties.maxPixels()) {
			throw invalid(ErrorCode.FACE_IMAGE_DIMENSIONS_UNSUPPORTED, HttpStatus.BAD_REQUEST, "Uploaded image dimensions are not supported.");
		}
	}

	private static boolean hasJpegSignature(byte[] bytes) {
		return bytes.length >= 2
				&& Byte.toUnsignedInt(bytes[0]) == JPEG_MARKER_PREFIX
				&& Byte.toUnsignedInt(bytes[1]) == JPEG_START_OF_IMAGE;
	}

	private static FaceAnalysisException invalid(ErrorCode errorCode, HttpStatus status, String message) {
		return new FaceAnalysisException(errorCode, status, message);
	}

	private record ImageDimensions(int width, int height) {
	}
}
