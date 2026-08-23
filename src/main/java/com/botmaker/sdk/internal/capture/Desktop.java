package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.shared.capture.ScreenCapture;

import java.awt.image.BufferedImage;

/**
 * The whole virtual desktop (every monitor) as a {@link CaptureSource}. This is the ultimate
 * fallback source when no {@link Window} or {@link Monitor} is targeted and no project default is
 * configured. Prefer the factory {@link CaptureSource#desktop()} over constructing this directly.
 */
public final class Desktop implements CaptureSource {

    @Override
    public BufferedImage capture() {
        return ScreenCapture.captureDesktop();
    }

    @Override
    public Point origin() {
        java.awt.Point o = ScreenCapture.getVirtualScreenBounds().getLocation();
        return new Point(o.x, o.y);
    }

    @Override
    public String toString() {
        return "Desktop[]";
    }
}
