package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.CaptureModel;
import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.capture.ScreenCapture;
import com.botmaker.shared.config.CaptureSourceKind;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstances;
import com.botmaker.shared.emulator.EmulatorProbe;
import javafx.application.Platform;

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
 */
public record EditorFrame(BufferedImage image, String label) {

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
        Thread worker = new Thread(() -> {
            CaptureTargetModel target = defaultTarget(services);
            EditorFrame frame = target == null ? null : grab(target);
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
     * The project's default capture target, or {@code null} when it has none.
     *
     * <p>Read on every call rather than held: a target changed in another window has to take effect here
     * without anything being rebuilt. Any failure to read reads as "no target", which is the same thing to
     * everyone downstream and keeps a mid-save project file from throwing at an editor.
     */
    public static CaptureTargetModel defaultTarget(StudioServices services) {
        try {
            Path resources = services == null ? null : services.resourcesDir();
            if (resources == null) return null;
            CaptureModel capture = Authoring.readCapture(SdkVersion.latest(), resources);
            return capture == null ? null : capture.defaultTarget();
        } catch (Exception unreadable) {
            return null;
        }
    }

    /** The pixels of {@code target}, or {@code null} when the grab failed or came back blank. */
    private static EditorFrame grab(CaptureTargetModel target) {
        try {
            BufferedImage image = pixels(target);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || looksBlank(image)) {
                return null;
            }
            return new EditorFrame(image, target.shortLabel());
        } catch (Throwable anything) {
            // A native capture path can throw as well as fail, and neither is worth more than "no frame".
            return null;
        }
    }

    private static BufferedImage pixels(CaptureTargetModel target) {
        if (target.emulatorName() != null) {
            EmulatorInstance instance = EmulatorInstances.byName(target.emulatorName()).orElse(null);
            return instance == null ? null : EmulatorProbe.screencap(instance);
        }
        if (target.windowTitle() != null) {
            GenericWindow window = findWindow(target.windowTitle());
            return window == null ? null : NativeControllerFactory.get().captureWindow(window);
        }
        if (target.is(CaptureSourceKind.MONITOR)) {
            return ScreenCapture.captureMonitor(target.monitorIndex());
        }
        return ScreenCapture.captureDesktop();
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
