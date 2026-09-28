package com.botmaker.sdk.plugin.screen;

import javafx.scene.image.Image;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * The screen overlay, over this machine's monitors — <b>one object for the picks that have no project behind
 * them.</b>
 *
 * <p>Resolving <em>which pixels</em> is a {@link ShotSource}; deciding <em>what the user does with them</em>
 * is a {@link ScreenOverlay}, with a {@link ScreenShot} between them. Putting a full-screen surface over a
 * running game and asking the user to point at something in it is entirely about what a bot sees, so it is
 * this plugin's: a plugin draws its own overlay, over pixels it grabbed itself through
 * {@code botmaker-shared}, which is published. The contract keeps what only a host can answer.
 *
 * <p><b>Add nothing here</b> — a new overlay behaviour belongs on {@link ScreenOverlay}, and anything that
 * has to know what a capture target is belongs on {@link EditorFrame}. This class exists so that the callers
 * that want the plain desktop pick do not each construct an overlay and a source.
 */
public final class ScreenCapture {

    private final ScreenOverlay overlay = new ScreenOverlay(new DesktopSource());

    /**
     * The interactive crop, reporting the cropped image and the physical resolution of the screen it was cut
     * from, so the caller can record it as the picture's authored resolution.
     */
    public void captureRegion(Window owner, ScreenOverlay.RegionCapture onCaptured) {
        overlay.captureRegion(owner, onCaptured);
    }

    /** Rubber-band selection returning {@code [x, y, width, height]} in the chosen screen's pixels. */
    public void selectRegion(Window owner, Consumer<int[]> onSelected) {
        overlay.selectRegion(owner, onSelected);
    }

    /** Point pick with a magnified close-up, reporting {@code [x, y]} in the chosen screen's pixels. */
    public void pickPoint(Window owner, Consumer<int[]> onPicked) {
        overlay.pickPoint(owner, onPicked);
    }

    /** The same magnified overlay, reporting the colour under the cursor rather than the coordinate. */
    public void pickColor(Window owner, Consumer<ScreenOverlay.ScreenPick> onPicked) {
        overlay.pickColor(owner, onPicked);
    }

    /** The single {@code BufferedImage} → FX {@code Image} conversion in this module; null-tolerant. */
    public static Image toFxImage(BufferedImage image) {
        return ScreenOverlay.toFxImage(image);
    }
}
