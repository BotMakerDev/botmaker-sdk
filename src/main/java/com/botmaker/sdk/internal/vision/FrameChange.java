package com.botmaker.sdk.internal.vision;

import java.awt.image.BufferedImage;

/**
 * Whether two frames of one source differ enough to call it a change: what {@code Screen.waitStable} and
 * {@code Screen.waitChange} ask between polls.
 *
 * <p>The tolerance is fixed so a bot writes no number: a pixel counts as changed when its brightness moved by
 * more than {@link #PIXEL_STEP} of 255, and the frame as changed when more than {@link #CHANGED_SHARE} of its
 * sampled pixels did. Capture noise and a blinking caret stay under both; a loading bar, a fade or a new screen
 * do not. Frames are sampled on a grid at most {@link #SAMPLES} wide, so a 4K frame costs what a small one does.
 */
public final class FrameChange {

    static final int PIXEL_STEP = 24;
    static final double CHANGED_SHARE = 0.005;
    static final int SAMPLES = 320;

    private FrameChange() {}

    /** Whether {@code b} differs from {@code a}; frames of different sizes always do, a missing one never. */
    public static boolean changed(BufferedImage a, BufferedImage b) {
        if (a == null || b == null) return false;
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return true;
        int step = Math.max(1, Math.max(a.getWidth(), a.getHeight()) / SAMPLES);
        int sampled = 0;
        int moved = 0;
        for (int y = 0; y < a.getHeight(); y += step) {
            for (int x = 0; x < a.getWidth(); x += step) {
                sampled++;
                if (Math.abs(luma(a.getRGB(x, y)) - luma(b.getRGB(x, y))) > PIXEL_STEP) moved++;
            }
        }
        return moved > sampled * CHANGED_SHARE;
    }

    private static int luma(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }
}
