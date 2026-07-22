package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.Debug;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

/**
 * Captures the full virtual desktop via AWT {@link Robot}. Works on Windows, X11, and XWayland.
 * (Under native Wayland this typically returns black — {@link SpectacleCapture} is preferred there.)
 */
public final class RobotCapture implements CaptureBackend {

    @Override
    public BufferedImage captureDesktop() {
        try {
            Rectangle virtualBounds = ScreenCapture.getVirtualScreenBounds();
            return new Robot().createScreenCapture(virtualBounds);
        } catch (AWTException e) {
            Debug.error("[capture] Robot desktop capture failed: " + e.getMessage());
            return null;
        }
    }
}
