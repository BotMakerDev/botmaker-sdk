package com.botmaker.sdk.internal.plugin.capture;

import javafx.scene.image.Image;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The screen overlay, over this machine's monitors — <b>one object for the picks that have no project behind
 * them.</b>
 *
 * <p>This is Studio's {@code ScreenCaptureService}, which arrived here on 2026-08-31 with the overlay itself.
 * The history is worth one paragraph, because three separate things were once this class. It began as 1,324
 * lines in which resolving <em>which pixels</em> and deciding <em>what the user does with them</em> were one
 * flow; the split made the first half a {@code TargetCapture} and the second a {@link ScreenOverlay}, with a
 * {@link ScreenShot} between them. The target half moved here first, because a window to look at is what a
 * bot's own {@code CaptureSource} names. Then the overlay followed, on the maintainer's ruling that
 * <i>the overlay and the desktop grab do not belong to Studio either</i> — which is right for the same reason
 * the pilot was: putting a full-screen surface over a running game and asking the user to point at something
 * in it is entirely about what a bot sees, and the editor's part in it was only ever that the editor happened
 * to be written first.
 *
 * <p>So {@code Capture} and {@code StudioServices.capture()} left the plugin contract in the same step. A
 * plugin draws its own overlay, over pixels it grabbed itself through {@code botmaker-shared}, which is
 * published. The contract keeps what only a host can answer.
 *
 * <p><b>Add nothing here</b> — a new overlay behaviour belongs on {@link ScreenOverlay}, and anything that
 * has to know what a capture target is belongs on {@link EditorFrame}. This class exists so that the callers
 * that want the plain desktop pick do not each construct an overlay and a source.
 */
public final class ScreenCapture {

    private final ScreenOverlay overlay = new ScreenOverlay(new DesktopSource());

    /**
     * Runs the interactive crop on the FX thread. With multiple monitors the user first picks which screen;
     * the frame is then shown 1:1 and the user rubber-bands a region. Calls {@code onCaptured} with the
     * cropped image, or does nothing if the user cancels (Esc / empty selection / chooser) or capture is
     * unavailable.
     */
    public void captureRegion(Window owner, Consumer<BufferedImage> onCaptured) {
        overlay.captureRegion(owner, onCaptured);
    }

    /**
     * As {@link #captureRegion(Window, Consumer)} but also reports the capture source's physical resolution
     * (the full screen pixel size the region was cropped from) so the caller can record it as the picture's
     * authored resolution.
     */
    public void captureRegion(Window owner, ScreenOverlay.RegionCapture onCaptured) {
        overlay.captureRegion(owner, onCaptured);
    }

    /**
     * Interactive rubber-band selection returning the chosen region as {@code [x, y, width, height]} in the
     * <b>capture source's</b> own pixel space. Does nothing if the user cancels or capture is unavailable.
     */
    public void selectRegion(Window owner, Consumer<int[]> onSelected) {
        overlay.selectRegion(owner, onSelected);
    }

    /** Interactive point pick with a magnified close-up, reporting {@code [x, y]} in the source's own space. */
    public void pickPoint(Window owner, Consumer<int[]> onPicked) {
        overlay.pickPoint(owner, onPicked);
    }

    /** The same magnified overlay, reporting the colour under the cursor rather than the coordinate. */
    public void pickColor(Window owner, Consumer<ScreenOverlay.ScreenPick> onPicked) {
        overlay.pickColor(owner, onPicked);
    }

    /**
     * Captures once and drives a single reusable overlay through {@code steps} in order — for a whole method
     * call's on-screen arguments.
     */
    public void runSession(Window owner, List<ScreenOverlay.PickStep> steps, Runnable onDone) {
        overlay.runSession(owner, steps, onDone);
    }

    /** Registers {@code listener}; returns a handle that unregisters it when closed. */
    public static AutoCloseable addCaptureOverlayListener(ScreenOverlay.CaptureOverlayListener listener) {
        return ScreenOverlay.addCaptureOverlayListener(listener);
    }

    /** The single {@code BufferedImage} → FX {@code Image} conversion in this module; null-tolerant. */
    public static Image toFxImage(BufferedImage image) {
        return ScreenOverlay.toFxImage(image);
    }

    /**
     * Writes {@code image} to {@code target} as PNG, creating parent directories.
     *
     * <p>About files rather than about pixels, and here because the overlay's callers are the ones that save
     * what they picked.
     */
    public void savePng(BufferedImage image, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }
}
