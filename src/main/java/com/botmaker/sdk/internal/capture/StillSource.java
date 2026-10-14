package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;

import java.awt.image.BufferedImage;

/**
 * One image already taken, as a source with its own top-left at {@code (0, 0)}: a find on it answers in the image's
 * pixels. Input on it does nothing; there is no game behind it.
 */
public record StillSource(BufferedImage image) implements CaptureSource {

    @Override
    public BufferedImage capture() {
        return image;
    }

    @Override
    public Point origin() {
        return new Point(0, 0);
    }

    @Override
    public void click(Point p) {
    }

    @Override
    public void drag(Point from, Point to, long durationMs) {
    }

    @Override
    public void scroll(Point at, int notches) {
    }
}
