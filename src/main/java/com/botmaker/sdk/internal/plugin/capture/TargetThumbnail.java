package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.capture.ScreenCapture;
import com.botmaker.shared.config.CaptureSourceKind;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstances;
import com.botmaker.shared.emulator.EmulatorProbe;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Off-thread live preview and existence probe for one {@link CaptureTargetModel}, shared by the two capture
 * dialogs — the visual source chooser and the targets manager — so both show the same thumbnail and the same
 * "available / not found" badge from one code path.
 *
 * <p><b>Existence is a separate answer from the image, and that is the whole reason this is not just
 * {@link EditorFrame}.</b> An editor asking for pixels has nothing to do when a grab fails; a list of
 * configured targets has to say <em>why</em> a row is blank — a window whose application is not running reads
 * differently from one that is running and would not give up its pixels.
 *
 * <p>Grabs block (native enumeration, a desktop capture, an ADB round trip), so call
 * {@link #grab(CaptureTargetModel)} off the FX thread.
 */
public final class TargetThumbnail {

    private TargetThumbnail() {
    }

    /** A probe: the preview {@code image} ({@code null} when unavailable) and whether the target exists now. */
    public record Result(BufferedImage image, boolean exists) {
    }

    /**
     * Probes {@code target}: a window is resolved by title (existence = a matching window is open) and
     * captured; a monitor is cropped out of the virtual desktop (existence = the index is still valid); an
     * emulator answers over ADB; the whole desktop always exists. Never throws — a failure is a
     * {@code Result} with no image.
     */
    public static Result grab(CaptureTargetModel target) {
        try {
            if (target == null) return new Result(null, false);
            if (target.windowTitle() != null) return windowResult(target.windowTitle());
            if (target.is(CaptureSourceKind.MONITOR)) return monitorResult(target.monitorIndex());
            if (target.emulatorName() != null) return emulatorResult(target.emulatorName());
            if (target.isDesktop()) return new Result(ScreenCapture.captureDesktop(), true);
        } catch (Throwable anything) {
            // A native path can throw as well as fail, and for a badge the two mean the same thing.
        }
        return new Result(null, false);
    }

    /**
     * The window's pixels, falling back to a crop of the whole desktop when the per-window grab comes back
     * blank — the Wayland path, the same one {@link EditorFrame} takes for the same reason.
     */
    private static Result windowResult(String titleSubstring) {
        if (titleSubstring.isBlank()) return new Result(null, false);
        GenericWindow window = EditorFrame.findWindow(titleSubstring);
        if (window == null) return new Result(null, false);
        BufferedImage image = null;
        try {
            image = NativeControllerFactory.get().captureWindow(window);
        } catch (Throwable notCaptured) {
            // Falls through to the desktop crop below, which is the answer on a native Wayland session.
        }
        if (!EditorFrame.usable(image)) {
            image = EditorFrame.cropped(ScreenCapture.captureDesktop(), window.getRect());
        }
        return new Result(image, true);
    }

    /** One monitor, cropped out of a single whole-desktop grab so a multi-monitor list stays one capture. */
    private static Result monitorResult(int index) {
        List<Screen> screens = Screen.getScreens();
        if (index < 0 || index >= screens.size()) return new Result(null, false);
        BufferedImage desktop = ScreenCapture.captureDesktop();
        Rectangle2D bounds = screens.get(index).getBounds();
        return new Result(EditorFrame.cropped(desktop, toAwt(bounds)), true);
    }

    /**
     * The emulator instance by name: it exists when it is configured <em>and</em> its ADB port answers, and a
     * running instance is grabbed with one {@code screencap}.
     *
     * <p>A null image with {@code exists} true is a real state and a different one from "not configured" —
     * running but refusing its pixels — which is why the flag is not derived from the image.
     */
    private static Result emulatorResult(String instanceName) {
        EmulatorInstance instance = EmulatorInstances.byName(instanceName).orElse(null);
        if (instance == null || !EmulatorProbe.isRunning(instance)) return new Result(null, false);
        return new Result(EmulatorProbe.screencap(instance), true);
    }

    private static Rectangle toAwt(Rectangle2D bounds) {
        return new Rectangle((int) Math.round(bounds.getMinX()), (int) Math.round(bounds.getMinY()),
                (int) Math.round(bounds.getWidth()), (int) Math.round(bounds.getHeight()));
    }
}
