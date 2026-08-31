package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.CaptureModel;
import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.capture.ScreenCapture;
import com.botmaker.shared.config.CaptureSourceKind;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstances;
import com.botmaker.shared.emulator.EmulatorProbe;
import javafx.application.Platform;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * One frozen frame of what the bot will actually look at, plus the name of the target it came from.
 *
 * <p>This is what the {@code Pixel} editors were missing. A ΔE number and a blob on a grid are abstractions
 * because there is no real frame behind them; with one in hand every knob becomes answerable by looking.
 *
 * <p><b>The plugin grabs it, and that is the whole reason this class is here rather than on the contract.</b>
 * Which project is open is the one thing only the host knows, and {@link StudioServices#resourcesDir()}
 * answers it; everything after that — which target the project chose, and what that target's pixels are — is
 * this plugin's own vocabulary read out of its own {@code capture.json} through {@link Authoring}, and
 * {@code botmaker-shared} grabbing it. The contract's {@code Capture.grabFrame} does the same thing from the
 * host side and is scheduled for deletion; it cannot serve this, because it reports a failed or blank grab by
 * simply never calling back, and an editor that cannot tell "failed" from "still working" cannot say
 * anything useful to the person waiting.
 *
 * <p><b>Never a silent desktop fallback.</b> What is sampled has to be a pixel of the thing the bot will look
 * at. Quietly grabbing the whole desktop instead hands back a colour from the wrong image with no sign that
 * it happened — on Waydroid the two are a scale factor apart and nothing the bot matches ever fires.
 *
 * <p>The frame is re-grabbed on every open rather than cached: it should show the game as it is now, and the
 * grab is off-thread anyway.
 *
 * <h2>Two components the capture overlay added (2026-08-31)</h2>
 *
 * <p>{@code bounds} is where on the virtual desktop these pixels are — what a rubber-band surface has to be
 * placed over, and what a drawn selection is mapped back through. A pixel editor ignores it; an overlay
 * cannot work without it.
 *
 * <p>{@code onScreen} says whether the pixels are <em>actually there</em> at those bounds. It is false for
 * exactly one target kind: an emulator, whose frame comes over ADB and is nowhere on the display. That
 * distinction is load-bearing rather than informational — a transparent surface shows the user whatever is
 * behind it, so over an emulator it would show the host window while the crop is taken from the ADB frame.
 * A caller that draws must paint the frame itself when this is false; see {@code CaptureSurface}'s backdrop.
 *
 * @param image    the pixels
 * @param label    the target's short name, for a header or a warning
 * @param bounds   where those pixels are on the virtual desktop; for an off-screen frame this is a
 *                 <em>placement</em> rather than a location — the frame centred on the primary screen at its
 *                 own aspect ratio, which is all a crop's width/height ratio needs
 * @param onScreen whether the pixels are really on the desktop at {@code bounds}
 */
public record EditorFrame(BufferedImage image, String label, Rectangle bounds, boolean onScreen) {

    /**
     * Why there is no frame — the two states worth telling apart, because they send the user to different
     * places. {@link #NO_TARGET} means the project never chose what to look at; {@link #BLANK} means it did
     * and the grab came back empty, which on a Wayland session is an ordinary outcome for a target that is
     * configured perfectly well.
     */
    public enum Failure {
        NO_TARGET("This project has no capture target",
                "Sampling a colour needs a frame of the thing the bot will look at. Choose the game window, "
                        + "or a screen, as this project's capture target and try again."),
        BLANK("The capture target produced a blank frame",
                "The target is set, but grabbing it returned nothing. This usually means the window is "
                        + "minimised or on another workspace — or, on a Wayland session, that the screenshot "
                        + "tool could not reach it. Bring the game to the front and try again, or point the "
                        + "project at a different target.");

        private final String headline;
        private final String detail;

        Failure(String headline, String detail) {
            this.headline = headline;
            this.detail = detail;
        }

        /** The one-line reason, for a dialog header. */
        public String headline() {
            return headline;
        }

        /** What to do about it, in the user's own terms. */
        public String detail() {
            return detail;
        }
    }

    /**
     * Grabs the project's default capture target off the calling thread and delivers the result on the
     * JavaFX thread: a frame, or the {@link Failure} that says why there is none.
     *
     * <p>Exactly one of the two consumers is invoked, exactly once. An editor therefore never has to guard
     * against being told nothing, which is the failure mode this replaces.
     */
    public static void grabAsync(StudioServices services, Consumer<EditorFrame> onFrame,
                                 Consumer<Failure> onFailure) {
        grabAsync(services, null, false, onFrame, onFailure);
    }

    /**
     * The grab a <b>capture session</b> takes: the target window brought to the front first, and snapped to
     * {@code snapTo} before the pixels are read when the project names a size.
     *
     * <p>Separate from {@link #grabAsync(StudioServices, Consumer, Consumer)} rather than a flag on it,
     * because the difference is a real one and belongs at the call site. A pixel editor samples the target
     * <em>as it is</em> — raising the game to read one colour would rearrange the user's screen for no
     * reason. A capture must do the opposite: what it writes to disk becomes a picture the bot matches
     * against, so the window has to be visible and at the project's canonical size, or every picture is
     * authored at whatever size the window happened to be and the matcher rescales all of them.
     *
     * <p>Both are no-ops for a target that is not a window. A monitor is not resized and an emulator's frame
     * arrives over ADB at its own resolution.
     */
    public static void grabAsync(StudioServices services, CaptureModel.Resolution snapTo,
                                 Consumer<EditorFrame> onFrame, Consumer<Failure> onFailure) {
        grabAsync(services, snapTo, true, onFrame, onFailure);
    }

    private static void grabAsync(StudioServices services, CaptureModel.Resolution snapTo, boolean raise,
                                  Consumer<EditorFrame> onFrame, Consumer<Failure> onFailure) {
        Thread worker = new Thread(() -> {
            CaptureTargetModel target = defaultTarget(services);
            EditorFrame frame = target == null ? null : grab(target, snapTo, raise);
            Failure failure = frame != null ? null : (target == null ? Failure.NO_TARGET : Failure.BLANK);
            Platform.runLater(() -> {
                if (frame != null) onFrame.accept(frame);
                else onFailure.accept(failure);
            });
        }, "sdk-editor-frame");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * The size the project captures at, or {@code null} when it names none.
     *
     * <p>Read out of {@code capture.json} for the same reason {@link #defaultTarget} is, and beside it since
     * 2026-08-31: a resolution set in another window has to take effect here without anything being rebuilt.
     */
    public static CaptureModel.Resolution referenceSize(StudioServices services) {
        CaptureModel capture = capture(services);
        return capture == null ? null : capture.reference();
    }

    /**
     * The project's default capture target, or {@code null} when it has none.
     *
     * <p>Read on every call rather than held: a target changed in another window has to take effect here
     * without anything being rebuilt. Any failure to read reads as "no target", which is the same thing to
     * everyone downstream and keeps a mid-save project file from throwing at an editor.
     */
    public static CaptureTargetModel defaultTarget(StudioServices services) {
        CaptureModel capture = capture(services);
        return capture == null ? null : capture.defaultTarget();
    }

    /** The project's {@code capture.json}, or {@code null} when there is no project or it will not read. */
    private static CaptureModel capture(StudioServices services) {
        try {
            Path resources = services == null ? null : services.resourcesDir();
            if (resources == null) return null;
            return Authoring.readCapture(SdkVersion.latest(), resources);
        } catch (Exception unreadable) {
            return null;
        }
    }

    /** The pixels of {@code target}, or {@code null} when the grab failed or came back blank. */
    private static EditorFrame grab(CaptureTargetModel target, CaptureModel.Resolution snapTo, boolean raise) {
        try {
            if (target.emulatorName() != null) return emulatorFrame(target);
            if (target.windowTitle() != null) return windowFrame(target, snapTo, raise);
            Rectangle bounds = target.is(CaptureSourceKind.MONITOR)
                    ? ScreenCapture.monitorBounds(target.monitorIndex())
                    : ScreenCapture.getVirtualScreenBounds();
            BufferedImage image = target.is(CaptureSourceKind.MONITOR)
                    ? ScreenCapture.captureMonitor(target.monitorIndex())
                    : ScreenCapture.captureDesktop();
            return usable(image) ? new EditorFrame(image, target.shortLabel(), bounds, true) : null;
        } catch (Throwable anything) {
            // A native capture path can throw as well as fail, and neither is worth more than "no frame".
            return null;
        }
    }

    /**
     * The emulator's own frame, pulled over ADB rather than grabbed off the host window it is drawn in.
     *
     * <p>Without this the pictures would be cropped out of the host window while the bot matches them
     * against the ADB frame. On Waydroid those two are a scale factor apart, so nothing the bot matched ever
     * fired — and the crop looked perfectly accurate, because it <em>was</em> accurate in the space it was
     * taken in. Capturing the way the bot captures makes the two spaces one by construction.
     */
    private static EditorFrame emulatorFrame(CaptureTargetModel target) {
        EmulatorInstance instance = EmulatorInstances.byName(target.emulatorName()).orElse(null);
        BufferedImage image = instance == null ? null : EmulatorProbe.screencap(instance);
        if (!usable(image)) return null;
        return new EditorFrame(image, target.shortLabel(),
                fitToPrimaryScreen(image.getWidth(), image.getHeight()), false);
    }

    /**
     * The window's pixels, optionally after raising it and snapping it to {@code snapTo}.
     *
     * <p>The window is re-resolved after the raise, because restoring a minimised window changes its bounds
     * and the surface has to be placed over where it ended up rather than where it was.
     *
     * <p><b>A blank native grab falls through to a desktop crop.</b> Per-window capture returns black on a
     * native Wayland session; the whole-desktop path has a working backend there, so cropping it to the
     * window's bounds recovers the frame. Without the fallback a Wayland user's every capture reads as "the
     * target produced a blank frame" while the window is plainly on screen.
     */
    private static EditorFrame windowFrame(CaptureTargetModel target, CaptureModel.Resolution snapTo,
                                           boolean raise) {
        GenericWindow window = findWindow(target.windowTitle());
        if (window == null) return null;
        NativeController controller = NativeControllerFactory.get();
        if (snapTo != null) resize(controller, window, snapTo);
        if (raise) {
            try {
                controller.restoreWindow(window);
                settle();
            } catch (Throwable notRaised) {
                System.err.println("Could not focus window: " + notRaised.getMessage());
            }
        }
        if (snapTo != null || raise) {
            GenericWindow refreshed = findWindow(target.windowTitle());
            if (refreshed != null) window = refreshed;
        }
        Rectangle bounds = window.getRect();

        BufferedImage image = null;
        try {
            image = controller.captureWindow(window);
        } catch (Throwable notCaptured) {
            System.err.println("Native window capture failed: " + notCaptured.getMessage());
        }
        if (!usable(image)) image = cropped(ScreenCapture.captureDesktop(), bounds);
        return usable(image) ? new EditorFrame(image, target.shortLabel(), bounds, true) : null;
    }

    /** Snaps {@code window} to {@code size}; a window already that size is left alone. Best-effort. */
    private static void resize(NativeController controller, GenericWindow window,
                               CaptureModel.Resolution size) {
        Rectangle current = window.getRect();
        if (current != null && current.width == size.width() && current.height == size.height()) return;
        try {
            controller.resizeWindow(window, size.width(), size.height());
            settle();
        } catch (Throwable notResized) {
            System.err.println("Could not resize window: " + notResized.getMessage());
        }
    }

    /** Lets the compositor finish moving or raising a window before its pixels are read. */
    private static void settle() {
        try {
            Thread.sleep(180);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** {@code desktop} cropped to {@code bounds}, clamped to the image, or {@code null}. */
    private static BufferedImage cropped(BufferedImage desktop, Rectangle bounds) {
        if (desktop == null || bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
        Rectangle virtual = ScreenCapture.getVirtualScreenBounds();
        int x = Math.max(0, Math.min(bounds.x - virtual.x, desktop.getWidth() - 1));
        int y = Math.max(0, Math.min(bounds.y - virtual.y, desktop.getHeight() - 1));
        int w = Math.max(1, Math.min(bounds.width, desktop.getWidth() - x));
        int h = Math.max(1, Math.min(bounds.height, desktop.getHeight() - y));
        return desktop.getSubimage(x, y, w, h);
    }

    /**
     * A {@code w}×{@code h}-shaped rectangle centred on the primary screen, at most 80% of its visual area —
     * where a frame that is nowhere on the desktop is <em>placed</em> so the user can draw on it.
     */
    private static Rectangle fitToPrimaryScreen(int w, int h) {
        javafx.geometry.Rectangle2D visual = javafx.stage.Screen.getPrimary().getVisualBounds();
        double scale = Math.min(1.0, Math.min(visual.getWidth() * 0.8 / w, visual.getHeight() * 0.8 / h));
        int width = Math.max(1, (int) Math.round(w * scale));
        int height = Math.max(1, (int) Math.round(h * scale));
        return new Rectangle(
                (int) Math.round(visual.getMinX() + (visual.getWidth() - width) / 2),
                (int) Math.round(visual.getMinY() + (visual.getHeight() - height) / 2),
                width, height);
    }

    /** A grab worth returning: real pixels, and not the uniform frame a failed capture produces. */
    private static boolean usable(BufferedImage image) {
        return image != null && image.getWidth() > 0 && image.getHeight() > 0 && !looksBlank(image);
    }

    /** The first window whose title contains {@code titleSubstring}, case-insensitively, or {@code null}. */
    private static GenericWindow findWindow(String titleSubstring) {
        String needle = titleSubstring.toLowerCase();
        for (GenericWindow window : NativeControllerFactory.get().getAllWindows(true)) {
            String title = window.getTitle();
            if (title != null && title.toLowerCase().contains(needle)) return window;
        }
        return null;
    }

    /**
     * Whether a grab came back with nothing on it — every sampled pixel the same colour.
     *
     * <p>Sampled on a grid rather than read whole: this runs on every open, and a frame that is genuinely
     * blank is uniform everywhere, so a hundred points answer as well as two million. The test is deliberately
     * "all identical" rather than "all black": a Wayland grab that fails returns black, and a window captured
     * before it has painted returns its background colour, and both are equally useless to sample from.
     */
    private static boolean looksBlank(BufferedImage image) {
        int first = image.getRGB(0, 0);
        int stepX = Math.max(1, image.getWidth() / 10);
        int stepY = Math.max(1, image.getHeight() / 10);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                if (image.getRGB(x, y) != first) return false;
            }
        }
        return true;
    }
}
