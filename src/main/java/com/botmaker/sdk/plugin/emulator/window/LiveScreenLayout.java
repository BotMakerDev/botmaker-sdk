package com.botmaker.sdk.plugin.emulator.window;

/**
 * How big the live screen draws an instance's frame, and how big its window opens: the drawing keeps the frame's ratio
 * and fills what the window gives it; the window takes at most {@link #SHARE} of the screen's free area, so it fits a
 * laptop's scaled screen as well as a large one.
 */
final class LiveScreenLayout {

    /** The share of the screen's visual bounds the window takes at most, on each side. */
    static final double SHARE = 0.9;

    /** A size in the window's units. */
    record Size(double width, double height) {
    }

    private LiveScreenLayout() {
    }

    /** The largest drawing of a {@code frameW}×{@code frameH} frame inside {@code boxW}×{@code boxH}; zero when either is empty. */
    static Size fitted(double frameW, double frameH, double boxW, double boxH) {
        if (!(frameW > 0) || !(frameH > 0) || !(boxW > 0) || !(boxH > 0)) return new Size(0, 0);
        double scale = Math.min(boxW / frameW, boxH / frameH);
        return new Size(frameW * scale, frameH * scale);
    }

    /**
     * The window's size for a {@code frameW}×{@code frameH} frame: the drawing as large as {@link #SHARE} of the visual
     * bounds allows once the window's own parts ({@code chromeW}×{@code chromeH}: the panel, the bars) are taken off,
     * plus those parts; never under the window's minimum, never over the visual bounds.
     */
    static Size window(double frameW, double frameH, double chromeW, double chromeH,
                       double visualW, double visualH, double minW, double minH) {
        Size drawing = fitted(frameW, frameH, visualW * SHARE - chromeW, visualH * SHARE - chromeH);
        return new Size(clamp(drawing.width() + chromeW, minW, visualW), clamp(drawing.height() + chromeH, minH, visualH));
    }

    /** Where a window of {@code size} starting at {@code at} sits once moved inside {@code min}..{@code max}. */
    static double inside(double at, double size, double min, double max) {
        return Math.max(min, Math.min(at, max - size));
    }

    /**
     * Where a window at {@code at}, {@code oldSize} long, starts once it is {@code newSize} long about the same centre,
     * kept inside {@code min}..{@code max}.
     */
    static double centred(double at, double oldSize, double newSize, double min, double max) {
        return inside(at + (oldSize - newSize) / 2, newSize, min, max);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(Math.min(min, max), Math.min(value, max));
    }
}
