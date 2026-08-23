package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.shared.capture.ScreenCapture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * A single monitor (0-based {@code index} into the OS screen-device list) as a {@link CaptureSource},
 * so a bot can match against just one screen on a multi-monitor desktop. An out-of-range index falls
 * back to the whole desktop's pixels. Prefer the factory {@link CaptureSource#monitor(int)} over
 * constructing this directly.
 */
public final class Monitor implements CaptureSource {

    private final int index;

    public Monitor(int index) {
        this.index = index;
    }

    @Override
    public BufferedImage capture() {
        return ScreenCapture.captureMonitor(index);
    }

    @Override
    public Point origin() {
        Rectangle b = ScreenCapture.monitorBounds(index);
        return new Point(b.x, b.y);
    }

    @Override
    public String toString() {
        return "Monitor[" + index + "]";
    }
}
