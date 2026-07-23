package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;
import com.botmaker.shared.capture.ScreenCapture;
import java.awt.image.BufferedImage;

public class Screen {
    /**
     * Captures the entire desktop.
     */
    public static BufferedImage capture() {
        return ScreenCapture.captureDesktop();
    }

    /**
     * Absolute screen coordinate of pixel (0,0) of the image returned by {@link #capture()}.
     * On a multi-monitor desktop the virtual-screen origin can be non-zero (e.g. a monitor placed
     * to the left of / above the primary gives negative coordinates), so this offset must be added
     * to in-image match coordinates to obtain absolute coordinates suitable for clicking.
     */
    public static Point captureOrigin() {
        java.awt.Point origin = ScreenCapture.getVirtualScreenBounds().getLocation();
        return new Point(origin.x, origin.y);
    }

}