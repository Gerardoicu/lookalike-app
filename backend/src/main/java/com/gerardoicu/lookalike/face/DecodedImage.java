package com.gerardoicu.lookalike.face;

import org.opencv.core.Mat;

public record DecodedImage(Mat mat, int width, int height) {
}
