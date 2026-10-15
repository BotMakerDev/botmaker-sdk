package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.vision.Images;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.launch.SessionHandoff;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.launch.BackgroundLauncher;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the assistant's tools share: the last screenshot it was shown, the screen as a source in screenshot
 * pixels, boxes drawn on a copy of a frame and on the game, and the project's confidence.
 *
 * <p><b>Coordinates are the screenshot's pixels</b>: {@code (0, 0)} is the top-left of what {@code screenshot}
 * returned, which is the capture source's own top-left. A box drawn on the game adds where that sits.
 */
final class Frames {

    private static final ManagedHandle<BotSettings> SETTINGS = ManagedHandle.of(SdkValues.SETTINGS);

    static final Color FOUND = new Color(0x3f, 0xb9, 0x50);
    static final Color NOTE = new Color(0xe3, 0xa0, 0x08);

    /**
     * The last screenshot the assistant was shown. {@code crop_picture}'s coordinates are that image's pixels,
     * and the screen may have changed since, so a crop is cut from this, not from a fresh capture.
     */
    private static volatile BufferedImage shown;

    private Frames() {}

    static void show(BufferedImage frame) {
        shown = frame;
    }

    static Optional<BufferedImage> shown() {
        return Optional.ofNullable(shown);
    }

    /** Forgets the last screenshot, for a test. */
    static void forget() {
        shown = null;
    }

    static String noScreen() {
        return "The screen the bot watches cannot be captured — setup's set_capture_source names a window.";
    }

    static String noProject() {
        return "No project is open.";
    }

    /**
     * What the look tools read now, in the bot's pixels: the private display this project's launch brought up
     * (launch_game, ▶ Launch), else the emulator the capture source names while it runs, else the screen Studio
     * watches. Studio grabs only this desktop, and a game in a private display or an emulator is on neither: the
     * assistant was shown Studio's own window while Firestone ran in gamescope (2026-10-13). Blocking.
     */
    static Optional<BufferedImage> look(AgentContext context) {
        return ToolTiming.timed(ToolTiming.Phase.CAPTURE, () -> lookNow(context));
    }

    private static Optional<BufferedImage> lookNow(AgentContext context) {
        PrivateSession session = session(context);
        if (session != null) {
            try {
                // The game's window, as a run's SessionSource grabs it: gamescope never paints its root, so the
                // whole screen is black there; the screen only until a window is attached.
                BufferedImage window = session.capture();
                return Optional.ofNullable(window != null ? window : session.captureScreen());
            } catch (RuntimeException closing) {
                return Optional.empty();
            }
        }
        CaptureSource emulator = emulator(context);
        if (emulator != null) return emulator.isPresent() ? Optional.ofNullable(emulator.capture()) : Optional.empty();
        return context.frame();
    }

    /**
     * Where {@link #look}'s frame sits on this desktop; empty when it is a display of its own — the private
     * display, an emulator — which no box on the desktop lands on.
     */
    static Optional<Area> lookArea(AgentContext context) {
        return session(context) != null || emulator(context) != null ? Optional.empty() : context.watchedArea();
    }

    /** Where {@link #look} reads, said after a frame's size: " at 0,28 on the desktop", " in the private display :1". */
    static String place(AgentContext context) {
        PrivateSession session = session(context);
        if (session != null) return " in the private display " + session.displayName();
        if (emulator(context) != null) return " on the emulator";
        return context.watchedArea().map(a -> " at " + a.x() + ',' + a.y() + " on the desktop").orElse("");
    }

    /** The live private display this project's launch brought up, rejoined after a reload in place, or null. */
    static PrivateSession session(AgentContext context) {
        Path resources = context.services().resourcesDir();
        return resources == null ? null
                : SessionHandoff.rejoin(context.services(), BackgroundLauncher.forProject(resources));
    }

    /** The capture source when it names an emulator, or null. */
    static CaptureSource emulator(AgentContext context) {
        CaptureSource source = FxCall.call(() -> CaptureValue.current(context.services()));
        return source != null && source.base() instanceof EmulatorSource ? source : null;
    }

    /** The confidence a run holds a match to: the project's {@code Sdk.settings()}, else the SDK's default. */
    static double confidence(StudioServices services) {
        return SETTINGS.read(services).orElse(BotSettings.DEFAULTS).confidence();
    }

    /** The resources folder, when a project is open. */
    static Optional<Path> resources(AgentContext context) {
        return Optional.ofNullable(context.services().resourcesDir());
    }

    /** The picture {@code typed} names, by the sanitised name every picture has; empty when there is none. */
    static Optional<String> picture(Path resources, String typed) {
        String base = TemplateLibrary.sanitizeName(typed == null ? "" : typed);
        return !base.isEmpty() && TemplateLibrary.exists(resources, base) ? Optional.of(base) : Optional.empty();
    }

    static String noPicture(String typed) {
        return "No picture called \"" + typed + "\"; list_pictures names them.";
    }

    /**
     * {@code x, y, width, height} as an area inside {@code frame}, or why not. Absent numbers are the whole
     * frame; a box only partly inside is clipped.
     */
    static Optional<Rect> within(BufferedImage frame, Integer x, Integer y, Integer width, Integer height) {
        Rect whole = Images.bounds(frame);
        if (x == null && y == null && width == null && height == null) return Optional.of(whole);
        if (x == null || y == null || width == null || height == null) return Optional.empty();
        // Rect.overlaps reads an empty box inside the frame as overlapping it: refused here, as nothing to cut.
        if (width <= 0 || height <= 0) return Optional.empty();
        return Optional.ofNullable(whole.intersection(new Rect(x, y, width, height)));
    }

    static String outside(BufferedImage frame) {
        return "Give x, y, width and height together, inside the " + frame.getWidth() + "×" + frame.getHeight()
                + " screenshot.";
    }

    /**
     * The longest side a screenshot is shown at when its call names no scale. An image costs the assistant
     * tokens by its pixels, and a whole 1080p frame read at full size is seldom what a look needs; a part, or
     * {@code scale} 1, shows the detail.
     */
    static final int LOOK_EDGE = 768;
    /**
     * The longest side of the image a find answers with. Its text already gives the places in screenshot pixels,
     * so the image only shows them — it never needs to be read off.
     */
    static final int BOXED_EDGE = 768;

    /** The scale that fits {@code width}×{@code height} within {@code edge} pixels; 1 when it already fits. */
    static double fit(int width, int height, int edge) {
        return Math.min(1, edge / (double) Math.max(width, height));
    }

    /**
     * A find's answer: {@code text}, then, when {@code show} is true, {@code frame} with {@code boxes} drawn on it,
     * shrunk to fit {@link #BOXED_EDGE} — saying so, since the places in {@code text} are the frame's own pixels.
     * The text alone is the default: an image costs the assistant more than the places it only repeats.
     */
    static AgentReply found(String text, BufferedImage frame, List<Rect> boxes, Color color, Boolean show) {
        if (!Boolean.TRUE.equals(show)) return AgentReply.text(text);
        double scale = fit(frame.getWidth(), frame.getHeight(), BOXED_EDGE);
        String note = scale < 1 ? "\n(The image is shrunk; the places above are the screenshot's pixels.)" : "";
        return AgentReply.text(text + note).and(AgentReply.image(boxed(frame, boxes, color)));
    }

    /**
     * {@code frame} shrunk to fit {@link #BOXED_EDGE}, with each of {@code boxes} drawn on it in {@code color}
     * after the shrink, so a box stays 3 pixels wide and leaves a small target visible.
     */
    static BufferedImage boxed(BufferedImage frame, List<Rect> boxes, Color color) {
        double scale = fit(frame.getWidth(), frame.getHeight(), BOXED_EDGE);
        BufferedImage copy = Images.scaled(frame, scale);
        Graphics2D g = copy.createGraphics();
        try {
            g.setColor(color);
            g.setStroke(new BasicStroke(3));
            for (Rect r : boxes) {
                g.drawRect((int) Math.round(r.x() * scale), (int) Math.round(r.y() * scale),
                        Math.max(1, (int) Math.round(r.width() * scale)),
                        Math.max(1, (int) Math.round(r.height() * scale)));
            }
        } finally {
            g.dispose();
        }
        return copy;
    }

    /** How to turn a pixel of an image shown at {@code scale} back into the screenshot's: exact to 4 places. */
    static String undo(double scale) {
        return String.format(Locale.ROOT, "%.4f", 1 / scale);
    }

    /** Shows {@code box}, in screenshot pixels, on the game where the watched screen sits. */
    static void mark(AgentContext context, Rect box, Marks.Kind kind, String label) {
        lookArea(context).ifPresent(at -> context.marks().show(new Area(at.x() + box.x(), at.y() + box.y(),
                box.width(), box.height()), kind, label));
    }

    static String score(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String box(Rect r) {
        return r.x() + "," + r.y() + " (" + r.width() + "×" + r.height() + ")";
    }
}
