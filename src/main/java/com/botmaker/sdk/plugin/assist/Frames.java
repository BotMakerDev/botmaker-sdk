package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;

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
        return "The screen the bot watches cannot be captured — set_capture_source names a window.";
    }

    static String noProject() {
        return "No project is open.";
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
        if (x == null && y == null && width == null && height == null) {
            return Optional.of(new Rect(0, 0, frame.getWidth(), frame.getHeight()));
        }
        if (x == null || y == null || width == null || height == null) return Optional.empty();
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = Math.min(frame.getWidth(), x + width);
        int bottom = Math.min(frame.getHeight(), y + height);
        return right <= left || bottom <= top ? Optional.empty() : Optional.of(new Rect(left, top, right - left,
                bottom - top));
    }

    static String outside(BufferedImage frame) {
        return "Give x, y, width and height together, inside the " + frame.getWidth() + "×" + frame.getHeight()
                + " screenshot.";
    }

    /** The part of {@code frame} under {@code area}, as its own image. */
    static BufferedImage cut(BufferedImage frame, Rect area) {
        BufferedImage copy = new BufferedImage(area.width(), area.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(frame.getSubimage(area.x(), area.y(), area.width(), area.height()), 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    /** A copy of {@code frame} with each of {@code boxes} drawn on it in {@code color}. */
    static BufferedImage boxed(BufferedImage frame, List<Rect> boxes, Color color) {
        BufferedImage copy = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(frame, 0, 0, null);
            g.setColor(color);
            g.setStroke(new BasicStroke(3));
            for (Rect r : boxes) g.drawRect(r.x(), r.y(), r.width(), r.height());
        } finally {
            g.dispose();
        }
        return copy;
    }

    /** Shows {@code box}, in screenshot pixels, on the game where the watched screen sits. */
    static void mark(AgentContext context, Rect box, Marks.Kind kind, String label) {
        context.watchedArea().ifPresent(at -> context.marks().show(new Area(at.x() + box.x(), at.y() + box.y(),
                box.width(), box.height()), kind, label));
    }

    static String score(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String box(Rect r) {
        return r.x() + "," + r.y() + " (" + r.width() + "×" + r.height() + ")";
    }

    /** {@code image} as a source with its own top-left at {@code (0, 0)}, so matches are in screenshot pixels. */
    record Still(BufferedImage image) implements CaptureSource {

        @Override
        public BufferedImage capture() {
            return image;
        }

        @Override
        public Point origin() {
            return new Point(0, 0);
        }

        @Override
        public void click(Point p) {
        }
    }
}
