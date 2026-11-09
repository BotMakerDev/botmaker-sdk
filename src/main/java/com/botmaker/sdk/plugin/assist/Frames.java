package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.overlay.Area;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.emulator.ReadableSource;
import com.botmaker.sdk.internal.vision.Images;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.ResourceMove;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.launch.SessionHandoff;
import com.botmaker.session.PrivateSession;
import com.botmaker.shared.capture.FrameGrab;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.session.launch.BackgroundLauncher;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the assistant's tools share: the last screenshot it was shown, the screen as a source in screenshot
 * pixels, boxes drawn on a copy of a frame and on the game, and the project's confidence.
 *
 * <p><b>Coordinates are the screenshot's pixels</b>: {@code (0, 0)} is the top-left of what {@code screenshot}
 * returned, which is the capture source's own top-left. A box drawn on the game adds where that sits.
 */
final class Frames {

    private static final ManagedHandle<BotSettings> SETTINGS = ManagedHandle.of(SdkValues.SETTINGS);
    private static final ManagedHandle<Shades> SHADES = ManagedHandle.of(SdkValues.SHADES);

    /** The shades the bot learned, none when it learned none. */
    static List<Shade> shades(StudioServices services) {
        return SHADES.read(services).map(Shades::list).orElse(List.of());
    }

    /** Writes {@code shades} as the bot's learned ones; empty when written, else why not. On the FX thread. */
    static Optional<String> writeShades(StudioServices services, List<Shade> shades) {
        return SHADES.write(services, new Shades(shades));
    }

    static final Color FOUND = new Color(0x3f, 0xb9, 0x50);
    static final Color NOTE = new Color(0xe3, 0xa0, 0x08);

    /**
     * The last screenshot the assistant was shown. {@code crop_pictures}' coordinates are that image's pixels,
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
        return feed(context).get();
    }

    /**
     * Where {@link #look} reads, found once: each call of the answer reads a frame there, without finding the
     * private display or reading the capture source again. A feed of many frames finds it again now and then, since
     * a launch may bring a private display up. Blocking.
     */
    static Supplier<Optional<BufferedImage>> feed(AgentContext context) {
        return away(context).orElse(context::frame);
    }

    /**
     * The game's window on the private display read as 🗺's live board reads it, through a grab kept open whose frames
     * cross no pipe (a whole frame down the display agent's pipe took ~200 ms, 2026-11-02); null when no private
     * display has a window attached. Read and closed on one thread. Blocking.
     */
    static Grabbed grabbed(AgentContext context) {
        PrivateSession session = session(context);
        GenericWindow game = session == null ? null : session.attached();
        if (game == null) return null;
        try {
            return new Grabbed(session.controller().grabWindow(game));
        } catch (RuntimeException closing) {
            return null;
        }
    }

    /**
     * Frames of a {@link FrameGrab}, each a new one the game drew: a read that gives the frame before again is read
     * once more, for up to {@link #FRESH_MS}, so a watch counts the game's frames rather than its own reads. Two images
     * are written in turn and handed out, each good until the read after next, so a reader copies what it keeps.
     */
    static final class Grabbed implements Supplier<Optional<BufferedImage>>, AutoCloseable {

        /** How long a read waits for a frame the game has not drawn yet before it hands out the same one again. */
        private static final long FRESH_MS = 100;

        private final FrameGrab grab;
        private final BufferedImage[] images = new BufferedImage[2];
        /** Which of {@link #images} was handed out last; -1 before the first. */
        private int last = -1;
        private int writing;

        private Grabbed(FrameGrab grab) {
            this.grab = grab;
        }

        @Override
        public Optional<BufferedImage> get() {
            writing = last == 0 ? 1 : 0;
            long until = System.nanoTime() + FRESH_MS * 1_000_000;
            try {
                while (true) {
                    if (!grab.grab(this::pixels)) return Optional.empty();
                    if (last < 0 || !sameFrame(images[writing], images[last]) || System.nanoTime() >= until) break;
                    Thread.sleep(2);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (RuntimeException closing) {
                return Optional.empty();
            }
            last = writing;
            return Optional.of(images[last]);
        }

        private int[] pixels(int width, int height) {
            BufferedImage image = images[writing];
            if (image == null || image.getWidth() != width || image.getHeight() != height) {
                images[writing] = image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            }
            return ints(image);
        }

        private static boolean sameFrame(BufferedImage a, BufferedImage b) {
            return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight() && Arrays.equals(ints(a), ints(b));
        }

        private static int[] ints(BufferedImage image) {
            return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        }

        @Override
        public void close() {
            grab.close();
        }
    }

    /**
     * {@link #feed} when it reads elsewhere than the context's capture source: the private display, the emulator;
     * empty for the screen the context watches. Blocking.
     */
    static Optional<Supplier<Optional<BufferedImage>>> away(AgentContext context) {
        PrivateSession session = session(context);
        if (session != null) {
            return Optional.of(() -> {
                try {
                    // The game's window, as a run's SessionSource grabs it: gamescope never paints its root, so the
                    // whole screen is black there; the screen only until a window is attached.
                    BufferedImage window = session.capture();
                    return Optional.ofNullable(window != null ? window : session.captureScreen());
                } catch (RuntimeException closing) {
                    return Optional.empty();
                }
            });
        }
        CaptureSource emulator = emulator(context);
        if (emulator != null) {
            return Optional.of(() -> emulator.isPresent() ? Optional.ofNullable(emulator.capture()) : Optional.empty());
        }
        return Optional.empty();
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
                : SessionHandoff.rejoin(context.services(),
                        BackgroundLauncher.forProject(ResourceMove.resourcesRoot(resources)));
    }

    /** The capture source when it names an emulator, or null. */
    static CaptureSource emulator(AgentContext context) {
        CaptureSource source = FxCall.call(() -> CaptureValue.current(context.services()));
        return source != null && source.base() instanceof EmulatorSource ? source : null;
    }

    /** The tree {@code emulator} (null: none) gives its app's screen by; empty for a target that shows none. */
    static Optional<ReadableSource> readable(CaptureSource emulator) {
        return emulator != null && emulator.base() instanceof ReadableSource tree ? Optional.of(tree)
                : Optional.empty();
    }

    /** The confidence a run holds a match to: the project's {@code Sdk.settings()}, else the SDK's default. */
    static double confidence(StudioServices services) {
        return SETTINGS.read(services).orElse(BotSettings.DEFAULTS).confidence();
    }

    /** How many frames the picture {@code name}'s file holds, as its constant says; 1 when it says not. */
    static int frames(AgentContext context, String name) {
        return FxCall.call(() -> TemplateUses.frames(context.services().pluginValues(), name));
    }

    /** {@link #frames(AgentContext, String)} of each of {@code names}, in one call on the FX thread. */
    static Map<String, Integer> frames(AgentContext context, Collection<String> names) {
        return FxCall.call(() -> {
            Map<String, Integer> frames = new HashMap<>();
            names.forEach(name -> frames.put(name, TemplateUses.frames(context.services().pluginValues(), name)));
            return frames;
        });
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

    /**
     * Where the bot's {@code picture} is on {@code frame}, its file read from {@code resources} and matched anywhere at
     * {@code confidence}: an area's anchor as a tool finds it. Empty when the bot has no such file or it is not there.
     */
    static Optional<Rect> found(Path resources, double confidence, ImageTemplate picture, BufferedImage frame) {
        if (resources == null || picture == null || frame == null) return Optional.empty();
        Optional<String> file = picture(resources, picture.id());
        if (file.isEmpty()) return Optional.empty();
        ImageTemplate placed = FrameCut.of(picture)
                .onto(new ImageTemplate(TemplateLibrary.fileForName(resources, file.get()).toString()));
        MatchResult best = ImageFinder.bestMatch(placed, new StillSource(frame));
        return best.isFound() && best.confidence() >= confidence ? Optional.of(best.rect()) : Optional.empty();
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
