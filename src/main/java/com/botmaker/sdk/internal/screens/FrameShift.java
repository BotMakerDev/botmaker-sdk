package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.shared.opencv.OpencvManager;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * How far a view moved between two frames, read off their pixels by phase correlation: what a node out of view on
 * both sides of a scroll is placed by, where no node shows on both.
 */
final class FrameShift {

    /** How sure the correlation must be: a frame of noise or of one flat colour answers below it. */
    static final double LEAST_RESPONSE = 0.2;

    private FrameShift() {
    }

    /**
     * How far the content of {@code before} moved to show as {@code after}, in pixels: right and down are positive.
     * Empty when the frames differ in size or the correlation is not sure of it.
     */
    static Optional<Point> of(BufferedImage before, BufferedImage after) {
        if (before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight()) return Optional.empty();
        Mat a = null;
        Mat b = null;
        Mat window = new Mat();
        try {
            a = gray(before);
            b = gray(after);
            Imgproc.createHanningWindow(window, a.size(), CvType.CV_32F);
            double[] response = new double[1];
            org.opencv.core.Point shift = Imgproc.phaseCorrelate(a, b, window, response);
            if (!(response[0] >= LEAST_RESPONSE)) return Optional.empty();
            return Optional.of(new Point((int) Math.round(shift.x), (int) Math.round(shift.y)));
        } finally {
            if (a != null) a.release();
            if (b != null) b.release();
            window.release();
        }
    }

    /** {@code image} as one channel of 32-bit floats, the form phase correlation reads. */
    private static Mat gray(BufferedImage image) {
        Mat bgr = OpencvManager.bufferedImageToMat(image);
        Mat gray = new Mat();
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
        bgr.release();
        Mat floats = new Mat();
        gray.convertTo(floats, CvType.CV_32F);
        gray.release();
        return floats;
    }
}
