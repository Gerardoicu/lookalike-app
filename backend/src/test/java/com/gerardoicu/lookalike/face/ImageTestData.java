package com.gerardoicu.lookalike.face;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

final class ImageTestData {

	private ImageTestData() {
	}

	static byte[] jpeg(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.setColor(Color.BLACK);
			graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
		}
		finally {
			graphics.dispose();
		}
		return write(image, "jpg");
	}

	static byte[] png() {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		return write(image, "png");
	}

	private static byte[] write(BufferedImage image, String format) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, format, output);
			return output.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to create test image.", ex);
		}
	}
}
