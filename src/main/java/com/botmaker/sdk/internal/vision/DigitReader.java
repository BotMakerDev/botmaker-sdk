package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.shared.Diag;
import com.botmaker.shared.opencv.OpenCvNative;
import com.botmaker.shared.opencv.OpencvManager;
import com.botmaker.shared.opencv.RawMatch;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.IntFunction;

/**
 * {@code Text.readDigits}: a number in a game's own font, read with the font's digit pictures (2026-10-11).
 *
 * <p>Every digit is looked for in the one frame; where two overlap, the surer one stays, or the wider when they are
 * nearly as sure (a {@code 1} is a stroke of a {@code 4}); the line of the surest is read left to right. A gap
 * wider than a digit is a character the set does not hold, so the number is not read rather than read wrong; a
 * narrower one ({@code ,}, {@code .}, {@code /}) is passed over. The digits scale as pictures do, by the whole
 * surface against the bot's screen, not by the area searched.
 */
public final class DigitReader {

    private static final String SOURCE = "Digits";
    /** A gap wider than this share of the widest digit holds a character the set does not have. */
    private static final double MISSING = 0.8;
    /** Scores this close are as sure: the wider digit stays. */
    private static final double AS_SURE = 0.03;
    /** The most digits a {@code long} holds. */
    private static final int LONGEST = 18;
    /** Digits shown shorter than this many pixels are enlarged before matching. */
    private static final double SMALL = 12;

    private DigitReader() {}

    /** A digit's picture. */
    public record Glyph(int digit, BufferedImage image) {
    }

    /** Where a digit was found, in the pixels searched. */
    record Hit(int digit, int x, int y, int width, int height, double score) {

        /** Whether {@code other} stands on most of this one's place: one of the two is a misreading. */
        boolean covers(Hit other) {
            int across = Math.min(x + width, other.x + other.width) - Math.max(x, other.x);
            int down = Math.min(y + height, other.y + other.height) - Math.max(y, other.y);
            return down > 0 && across * 2 > Math.min(width, other.width);
        }

        boolean sameLine(Hit other) {
            return Math.abs((y * 2 + height) - (other.y * 2 + other.height)) < Math.max(height, other.height);
        }

        /** Whether this one beats {@code other} where they overlap. */
        boolean beats(Hit other) {
            return score - other.score > AS_SURE || score - other.score > -AS_SURE && width > other.width;
        }
    }

    /** The number {@code set} reads in {@code source}, or empty when no digit is there or one is missing. */
    public static OptionalLong read(CaptureSource source, DigitSet set, double confidence) {
        List<Glyph> glyphs = load(digit -> TemplateSource.read(set.folder() + "/" + digit + ".png", new ArrayList<>())
                .map(TemplateSource.Found::bytes));
        if (glyphs.isEmpty()) {
            Diag.warn(SOURCE, "No digit pictures in " + set.folder() + ": save_digits cuts them from a number the"
                    + " game shows.");
            return OptionalLong.empty();
        }
        BufferedImage frame = source.base().capture();
        if (frame == null) return OptionalLong.empty();
        Size live = new Size(frame.getWidth(), frame.getHeight());
        double scale = scale(set.frame() != null ? set.frame() : BotScreen.size().orElse(null), live);
        // A digit is cut with a pixel of background around the line; an area boxed tight on the ink is widened
        // by that much, so no digit is taller than the area it is looked for in.
        int pad = (int) Math.ceil(2 * scale) + 1;
        Rect area = area(source, live).expand(pad).intersection(new Rect(0, 0, live.width(), live.height()));
        if (area.empty()) return OptionalLong.empty();
        BufferedImage part = frame.getSubimage(area.x(), area.y(), area.width(), area.height());
        return read(part, scale, glyphs, confidence, set.name());
    }

    /**
     * The number {@code glyphs} read in {@code image}, each digit resized by {@code scale} first. {@code name} names
     * the set in a warning.
     */
    public static OptionalLong read(BufferedImage image, double scale, List<Glyph> glyphs, double confidence,
                                    String name) {
        OpenCvNative.ensureLoaded();
        // A digit a few pixels tall is a handful of pixels to compare: both sides are enlarged first, so a
        // pixel of antialiasing weighs less than a stroke.
        int enlarge = enlargement(glyphs.stream().mapToInt(g -> g.image().getHeight()).max().orElse(0) * scale);
        BufferedImage searched = enlarge == 1 ? image : resized(image, enlarge);
        List<Hit> hits = new ArrayList<>();
        Mat background = OpencvManager.bufferedImageToMat(searched);
        try {
            for (Glyph glyph : glyphs) {
                BufferedImage sized = resized(glyph.image(), scale * enlarge);
                if (sized.getWidth() > searched.getWidth() || sized.getHeight() > searched.getHeight()) continue;
                Mat template = OpencvManager.bufferedImageToMat(sized);
                try {
                    for (RawMatch found : OpencvManager.findMultipleMatches(template, background, false,
                            confidence)) {
                        hits.add(new Hit(glyph.digit(), found.x(), found.y(), found.width(), found.height(),
                                found.score()));
                    }
                } finally {
                    template.release();
                }
            }
        } finally {
            background.release();
        }
        return assemble(hits, name);
    }

    /** The number the hits spell: the better of each overlapping pair, on the surest one's line, left to right. */
    static OptionalLong assemble(List<Hit> hits, String name) {
        List<Hit> ranked = new ArrayList<>(hits);
        ranked.sort(Comparator.comparingDouble(Hit::score).reversed());
        List<Hit> kept = new ArrayList<>();
        for (Hit hit : ranked) {
            List<Hit> under = kept.stream().filter(hit::covers).toList();
            if (under.isEmpty()) {
                kept.add(hit);
            } else if (under.stream().allMatch(hit::beats)) {
                kept.removeAll(under);
                kept.add(hit);
            }
        }
        if (kept.isEmpty()) return OptionalLong.empty();
        Hit surest = kept.stream().max(Comparator.comparingDouble(Hit::score)).orElseThrow();
        List<Hit> line = kept.stream().filter(surest::sameLine).sorted(Comparator.comparingInt(Hit::x)).toList();
        int widest = line.stream().mapToInt(Hit::width).max().orElse(0);
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < line.size(); i++) {
            Hit hit = line.get(i);
            if (i > 0) {
                Hit before = line.get(i - 1);
                if (hit.x() - (before.x() + before.width()) > MISSING * widest) {
                    Diag.warn(SOURCE, "A character after \"" + digits + "\" is not in the " + name + " digits:"
                            + " save_digits on a number that shows it.");
                    return OptionalLong.empty();
                }
            }
            digits.append(hit.digit());
        }
        return digits.length() > LONGEST ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(digits.toString()));
    }

    /**
     * The digit pictures {@code bytes} gives, {@code 0.png} to {@code 9.png} by digit; a missing or unreadable one
     * is left out.
     */
    public static List<Glyph> load(IntFunction<Optional<byte[]>> bytes) {
        List<Glyph> glyphs = new ArrayList<>();
        for (int digit = 0; digit <= 9; digit++) {
            Optional<byte[]> png = bytes.apply(digit);
            if (png.isEmpty()) continue;
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png.get()));
                if (image != null) glyphs.add(new Glyph(digit, image));
            } catch (IOException unreadable) {
                Diag.warn(SOURCE, digit + ".png is not an image: " + unreadable.getMessage());
            }
        }
        return glyphs;
    }

    /**
     * How much bigger the game shows than {@code authored}, as the matcher scales a picture: by each side's
     * ratio's mean, or the smaller one on a frame of another shape. 1 when the bot names no size.
     */
    static double scale(Size authored, Size live) {
        if (authored == null || authored.empty()) return 1;
        double sx = live.width() / (double) authored.width();
        double sy = live.height() / (double) authored.height();
        return Places.otherShape(authored, live) ? Math.min(sx, sy) : (sx + sy) / 2;
    }

    /** How many times a digit {@code shownHeight} pixels tall is enlarged before matching: up to 3, toward 12 px. */
    static int enlargement(double shownHeight) {
        if (shownHeight <= 0 || shownHeight >= SMALL) return 1;
        return (int) Math.min(3, Math.ceil(SMALL / shownHeight));
    }

    /** Where {@code source} narrows its whole surface of {@code live} size to; all of it when it does not. */
    private static Rect area(CaptureSource source, Size live) {
        if (source instanceof ZoneSource zoned) {
            return Places.inSource(zoned.zone(), BotScreen.size().orElse(null), live);
        }
        Rect sub = source.subRegion();
        return sub == null ? new Rect(0, 0, live.width(), live.height()) : sub;
    }

    private static BufferedImage resized(BufferedImage image, double scale) {
        if (Math.abs(scale - 1) < 0.005) return image;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return copy;
    }
}
