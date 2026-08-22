package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.ApiId;
import com.botmaker.sdk.api.Point;
import com.botmaker.shared.capture.ScreenCapture;

import java.awt.image.BufferedImage;

/**
 * The whole virtual desktop (every monitor) as a {@link CaptureSource}. This is the ultimate
 * fallback source when no {@link Window} or {@link Monitor} is targeted and no project default is
 * configured. Prefer the factory {@link CaptureSource#desktop()} over constructing this directly.
 */
@ApiId("desktop")
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
