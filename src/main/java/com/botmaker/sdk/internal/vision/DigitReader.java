package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.internal.trace.SdkRun;
import com.botmaker.shared.opencv.OpenCvNative;
import com.botmaker.shared.opencv.OpencvManager;
import com.botmaker.shared.opencv.RawMatch;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code Text.readDigits}: a number in a game's own font, read with the font's digit pictures (2026-10-11).
 *
 * <p>Every character is looked for in the one frame; where two overlap, the surer one stays, or the wider when they
 * are nearly as sure (a {@code 1} is a stroke of a {@code 4}); the line of the surest is read left to right. A gap
 * wider than a digit is a character the set does not hold, so the number is not read rather than read wrong; a
 * narrower one is passed over. The characters scale as pictures do, by the whole surface against the bot's screen,
 * not by the area searched.
 *
 * <p>Beside the digits a set may hold the marks a game writes a number with (2026-11-09): {@code ,} is passed over,
 * {@code .} is a decimal point, {@code K}, {@code M} and {@code B} multiply, and {@code /} ends the number, so
 * {@code 37.1K} reads 37100 and {@code 300/300} reads 300 ({@link #number}).
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
    /** Each mark a set may hold, by the file its picture is saved in: {@code dot.png}. */
    private static final Map<Character, String> MARKS = Map.of('.', "dot", ',', "comma", '/', "slash", 'K', "k",
            'M', "m", 'B', "b");
    /** Every character a set may hold, digits first. */
    public static final String SYMBOLS = "0123456789.,/KMB";
    /** A number as {@link #number} reads it: digits, a decimal part, a multiplier. */
    private static final Pattern NUMBER = Pattern.compile("(\\d+)(\\.\\d+)?([KMB])?");

    private DigitReader() {}

    /** A character's picture: a digit or a mark of {@link #SYMBOLS}. */
    public record Glyph(char symbol, BufferedImage image) {

        /** Whether it is a digit, not a mark. */
        public boolean digit() {
            return Character.isDigit(symbol);
        }
    }

    /** Where a character was found, in the pixels searched. */
    record Hit(char symbol, int x, int y, int width, int height, double score) {

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

    /**
     * The name of the file, with no extension, that {@code symbol}'s picture is saved in: the digit itself, or a mark's
     * word; empty for a character a set does not hold. A lowercase multiplier is its capital.
     */
    public static Optional<String> fileOf(char symbol) {
        if (Character.isDigit(symbol)) return Optional.of(String.valueOf(symbol));
        return Optional.ofNullable(MARKS.get(Character.toUpperCase(symbol)));
    }

    /**
     * The number {@code text} writes, as the game shows it: commas and spaces passed over, read up to a {@code /},
     * a decimal part kept until a multiplier ({@code K} a thousand, {@code M} a million, {@code B} a billion) and
     * dropped after it. {@code 37.1K} is 37100, {@code 300/300} is 300, {@code 12.5} is 12. Empty for anything else.
     */
    public static OptionalLong number(String text) {
        if (text == null) return OptionalLong.empty();
        String plain = text.replace(",", "").replace(" ", "").toUpperCase();
        int slash = plain.indexOf('/');
        if (slash >= 0) plain = plain.substring(0, slash);
        Matcher m = NUMBER.matcher(plain);
        if (!m.matches() || m.group(1).length() > LONGEST) return OptionalLong.empty();
        BigDecimal value = new BigDecimal(m.group(1) + (m.group(2) == null ? "" : m.group(2)));
        if (m.group(3) != null) {
            value = value.movePointRight(switch (m.group(3)) {
                case "K" -> 3;
                case "M" -> 6;
                default -> 9;
            });
        }
        BigDecimal whole = value.setScale(0, RoundingMode.DOWN);
        return whole.precision() > LONGEST ? OptionalLong.empty() : OptionalLong.of(whole.longValueExact());
    }

    /**
     * The number a readout's text shows, read leniently: {@link #number} over its digits and marks with any other
     * character dropped ({@code - 130} is 130, {@code 300/300} 300, {@code 37.1K} 37100), else every digit in it
     * ({@code Lv.12} is 12); empty when it holds no digit or more than a {@code long} holds (2026-11-09).
     */
    public static OptionalLong shown(String text) {
        if (text == null) return OptionalLong.empty();
        OptionalLong marked = number(text.replaceAll("[^0-9.,/KMBkmb]", ""));
        if (marked.isPresent()) return marked;
        String digits = text.replaceAll("\\D", "");
        return digits.isEmpty() || digits.length() > LONGEST ? OptionalLong.empty()
                : OptionalLong.of(Long.parseLong(digits));
    }

    /** The number {@code set} reads in {@code source}, or empty when no digit is there or one is missing. */
    public static OptionalLong read(CaptureSource source, DigitSet set, double confidence) {
        BufferedImage frame = source.base().capture();
        if (frame == null) return OptionalLong.empty();
        return read(frame, area(source, new Size(frame.getWidth(), frame.getHeight())), set, confidence);
    }

    /** The number {@code set} reads in {@code area} of {@code frame}, a whole capture; empty as for the source's. */
    public static OptionalLong read(BufferedImage frame, Rect area, DigitSet set, double confidence) {
        List<Glyph> glyphs = load(file -> TemplateSource.read(set.folder() + "/" + file + ".png", new ArrayList<>())
                .map(TemplateSource.Found::bytes));
        if (glyphs.isEmpty()) {
            SdkRun.warn(SOURCE, "No digit pictures in " + set.folder() + ": save_digits cuts them from a number the"
                    + " game shows.");
            return OptionalLong.empty();
        }
        Size live = new Size(frame.getWidth(), frame.getHeight());
        double scale = scale(set.frame() != null ? set.frame() : BotScreen.size().orElse(null), live);
        // A digit is cut with a pixel of background around the line; an area boxed tight on the ink is widened
        // by that much, so no digit is taller than the area it is looked for in.
        int pad = (int) Math.ceil(2 * scale) + 1;
        Rect padded = area.expand(pad).intersection(new Rect(0, 0, live.width(), live.height()));
        if (padded.empty()) return OptionalLong.empty();
        BufferedImage part = frame.getSubimage(padded.x(), padded.y(), padded.width(), padded.height());
        return read(part, scale, glyphs, confidence, set.name());
    }

    /**
     * The number {@code glyphs} read in {@code image}, each character resized by {@code scale} first. {@code name}
     * names the set in a warning.
     */
    public static OptionalLong read(BufferedImage image, double scale, List<Glyph> glyphs, double confidence,
                                    String name) {
        return text(image, scale, glyphs, confidence, name).map(DigitReader::number).orElse(OptionalLong.empty());
    }

    /** The characters {@code glyphs} read in {@code image}, as {@link #read} finds them, before they are a number. */
    public static Optional<String> text(BufferedImage image, double scale, List<Glyph> glyphs, double confidence,
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
                        hits.add(new Hit(glyph.symbol(), found.x(), found.y(), found.width(), found.height(),
                                found.score()));
                    }
                } finally {
                    template.release();
                }
            }
        } finally {
            background.release();
        }
        return spelled(hits, name);
    }

    /** The number the hits spell, as {@link #number} reads {@link #spelled their characters}. */
    static OptionalLong assemble(List<Hit> hits, String name) {
        return spelled(hits, name).map(DigitReader::number).orElse(OptionalLong.empty());
    }

    /** The characters the hits spell: the better of each overlapping pair, on the surest one's line, left to right. */
    static Optional<String> spelled(List<Hit> hits, String name) {
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
        if (kept.isEmpty()) return Optional.empty();
        Hit surest = kept.stream().max(Comparator.comparingDouble(Hit::score)).orElseThrow();
        List<Hit> line = kept.stream().filter(surest::sameLine).sorted(Comparator.comparingInt(Hit::x)).toList();
        int widest = line.stream().filter(h -> Character.isDigit(h.symbol())).mapToInt(Hit::width).max()
                .orElse(line.stream().mapToInt(Hit::width).max().orElse(0));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < line.size(); i++) {
            Hit hit = line.get(i);
            if (i > 0) {
                Hit before = line.get(i - 1);
                if (hit.x() - (before.x() + before.width()) > MISSING * widest) {
                    SdkRun.warn(SOURCE, "A character after \"" + text + "\" is not in the " + name + " digits:"
                            + " save_digits on a number that shows it.");
                    return Optional.empty();
                }
            }
            text.append(hit.symbol());
        }
        return Optional.of(text.toString());
    }

    /**
     * The pictures {@code bytes} gives, by file name with no extension ({@code 0} to {@code 9}, then each mark's
     * word, as {@link #fileOf} names them); a missing or unreadable one is left out.
     */
    public static List<Glyph> load(Function<String, Optional<byte[]>> bytes) {
        List<Glyph> glyphs = new ArrayList<>();
        for (char symbol : SYMBOLS.toCharArray()) {
            String file = fileOf(symbol).orElseThrow();
            Optional<byte[]> png = bytes.apply(file);
            if (png.isEmpty()) continue;
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png.get()));
                if (image != null) glyphs.add(new Glyph(symbol, image));
            } catch (IOException unreadable) {
                SdkRun.warn(SOURCE, file + ".png is not an image: " + unreadable.getMessage());
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
        return Math.abs(scale - 1) < 0.005 ? image : Images.scaled(image, scale);
    }
}
