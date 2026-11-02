package com.botmaker.sdk.internal.vision;

import com.botmaker.shared.opencv.OpenCvNative;
import com.botmaker.shared.opencv.OpencvManager;
import com.botmaker.shared.opencv.RawMatch;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * How a moving picture is matched: the frame and each of its frames shrunk to a {@link #SCALE quarter} and blurred,
 * so the few pixels a thing sways by between two poses no longer tell them apart (2026-11-02, the user's call:
 * moving pictures only; a still one keeps every pixel). Measured on Sword x Staff's swaying plant: 25 poses at full
 * size were 7 at a quarter, the best wrong place scoring 0.61 against 0.42, and each search about 16 times cheaper;
 * an eighth fell to 3 poses but let wrong places reach 0.72. A place found is about {@code 1 / SCALE} pixels sure.
 */
public final class Coarse {

    private Coarse() {}

    /** The share of each side kept. */
    public static final double SCALE = 0.25;

    /**
     * The shortest side, in pixels, a picture is shrunk from: under it the shrunk frame is a few pixels a blur
     * flattens, which correlate with almost anything, so such a picture is matched as it is.
     */
    public static final int SMALLEST = 32;

    /**
     * Shrunk pixels a watch lets a pose shift by, each way: a kept pose without this border is slid over the new one,
     * as a find slides a picture over the margin around its place (12 px at full size). Without it the plant's sway,
     * whose box drifts a few pixels, was 13 poses on the recording; with it, 7.
     */
    static final int SLACK = 3;

    static {
        OpenCvNative.ensureLoaded();
    }

    /** Whether a picture {@code width}×{@code height} is big enough to be matched shrunk. */
    public static boolean fits(int width, int height) {
        return Math.min(width, height) >= SMALLEST;
    }

    /** {@code image} shrunk to {@link #SCALE} and blurred; a new {@code Mat} the caller releases. */
    public static Mat shrink(Mat image) {
        Mat small = new Mat();
        Imgproc.resize(image, small, new Size(side(image.cols()), side(image.rows())), 0, 0, Imgproc.INTER_AREA);
        Imgproc.GaussianBlur(small, small, new Size(3, 3), 0);
        return small;
    }

    /** The frame size a picture was cut in as a shrunk frame has it; null stays null. */
    public static Dimension shrink(Dimension size) {
        return size == null ? null : new Dimension(side(size.width), side(size.height));
    }

    private static int side(int pixels) {
        return Math.max(1, (int) Math.round(pixels * SCALE));
    }

    /**
     * Poses a watch keeps, each held shrunk with its {@link #SLACK} border off, so a new grab is shrunk once and
     * compared with all of them; a pose too small to shrink ({@link #fits}) is compared as it is, without slack.
     */
    public static final class Poses implements AutoCloseable {

        private final List<Mat> kept = new ArrayList<>();

        /** Keeps {@code pose}. */
        public void add(BufferedImage pose) {
            Mat small = prepared(pose);
            boolean trimmed = fits(pose.getWidth(), pose.getHeight()) && small.cols() > 4 * SLACK
                    && small.rows() > 4 * SLACK;
            if (!trimmed) {
                kept.add(small);
                return;
            }
            kept.add(small.submat(SLACK, small.rows() - SLACK, SLACK, small.cols() - SLACK).clone());
            small.release();
        }

        /** The kept pose {@code pose} is most alike, at {@code confidence} or more; -1 when none is. */
        public int best(BufferedImage pose, double confidence) {
            Mat small = prepared(pose);
            try {
                int best = -1;
                double score = confidence;
                for (int i = 0; i < kept.size(); i++) {
                    RawMatch match = OpencvManager.findBest(kept.get(i), small, false);
                    if (match != null && match.score() >= score) {
                        best = i;
                        score = match.score();
                    }
                }
                return best;
            } finally {
                small.release();
            }
        }

        private static Mat prepared(BufferedImage pose) {
            Mat full = OpencvManager.bufferedImageToMat(pose);
            if (!fits(pose.getWidth(), pose.getHeight())) return full;
            try {
                return shrink(full);
            } finally {
                full.release();
            }
        }

        @Override
        public void close() {
            kept.forEach(Mat::release);
            kept.clear();
        }
    }
}
