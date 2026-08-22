package com.botmaker.sdk.api.vision;
import com.botmaker.sdk.api.ApiId;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.opencv.ColorMatcher;
import com.botmaker.shared.opencv.RawColorMatch;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel-colour detection: what colour is this pixel, and where does this colour appear?
 *
 * <p>The colour counterpart to {@link ImageFinder}. Same conventions: every search takes a
 * {@link CaptureSource} (a window, a monitor, or the desktop), a search <em>region</em> is expressed as a
 * {@link CaptureSource#region(Rect) region of a source} rather than a separate parameter, results come back
 * in <b>absolute screen coordinates</b>, and the full result is parked in {@link VisionContext} so the
 * boolean-returning calls stay readable. No-source overloads use {@link Source#current()}.
 *
 * <h2>How exacting the search is — one knob-set, {@link Precision}</h2>
 *
 * <p>Every search reads its strictness from a single {@link Precision}: a colour tolerance (CIELAB ΔE), a
 * minimum blob area, and a minimum total pixel count. Start from an anchor — {@link Precision#EXACT},
 * {@link Precision#TIGHT}, {@link Precision#DEFAULT}, {@link Precision#LOOSE} — and adjust what you care
 * about. The short overloads use {@code DEFAULT}.
 *
 * <p>It is one type rather than three arguments because the numbers are unreadable apart and only meaningful
 * together; {@link Precision} carries the full argument. What matters at this level is that
 * <b>not every operation reads every field</b>: {@link #matchesAt} tests a single pixel and {@link #coverage}
 * never clusters, so both read only the tolerance, and {@link #findInRange} takes a colour band rather than a
 * target colour, so it reads only the two quantity gates. Each says so on itself.
 *
 * <pre>{@code
 * // Is the health bar still red, in the top-left corner of the game window?
 * CaptureSource hud = CaptureSource.window("MyGame").region(new Rect(10, 10, 200, 30));
 * if (Pixel.find(Color.RED, hud, Precision.DEFAULT.minArea(400).minCount(2000))) {
 *     Mouse.click(VisionContext.getLastColorMatch().getCenter());
 * }
 * }</pre>
 */
@ApiId("pixel")
public class Pixel {

    // ---------------------------------------------------------------------
    // colourAt — read a colour
    // ---------------------------------------------------------------------

    /** The colour at absolute screen point ({@code x},{@code y}), or {@code null} if unreadable. */
    public static Color colorAt(int x, int y) {
        return colorAt(x, y, Source.current());
    }

    /** The colour at {@code p} (absolute screen coordinates), or {@code null} if unreadable. */
    public static Color colorAt(Point p) {
        return colorAt((int) p.x, (int) p.y, Source.current());
    }

    /**
     * The colour at absolute screen point ({@code x},{@code y}) as seen through {@code source}, or
     * {@code null} if the point lies outside the source or the capture failed.
     */
    public static Color colorAt(int x, int y, CaptureSource source) {
        BufferedImage img = source.capture();
        if (img == null) return null;
        Point origin = source.origin();
        int lx = x - (int) origin.x;
        int ly = y - (int) origin.y;
        if (lx < 0 || ly < 0 || lx >= img.getWidth() || ly >= img.getHeight()) return null;
        return new Color(img.getRGB(lx, ly), false);
    }

    // ---------------------------------------------------------------------
    // matchesAt — colour precision at one known point
    // ---------------------------------------------------------------------

    /**
     * Whether the pixel at ({@code x},{@code y}) is within {@code precision}'s tolerance (ΔE) of
     * {@code target}. Reads only {@link Precision#deltaE()} — one pixel has no blob to measure.
     */
    public static boolean matchesAt(int x, int y, Color target, Precision precision) {
        return matchesAt(x, y, target, Source.current(), precision);
    }

    /**
     * Whether the pixel at ({@code x},{@code y}) of {@code source} is within {@code precision}'s tolerance of
     * {@code target}. Reads only {@link Precision#deltaE()}; {@code minArea} and {@code minCount} describe a
     * cluster search and there is no cluster here, so setting them changes nothing.
     */
    public static boolean matchesAt(int x, int y, Color target, CaptureSource source, Precision precision) {
        Color actual = colorAt(x, y, source);
        return actual != null && ColorMatcher.deltaE(actual, target) <= precision.deltaE();
    }

    /** The CIELAB ΔE distance between two colours — the metric {@link Precision#deltaE()} is measured in. */
    public static double distance(Color a, Color b) {
        return ColorMatcher.deltaE(a, b);
    }

    // ---------------------------------------------------------------------
    // find — colour + location precision
    // ---------------------------------------------------------------------

    /** Finds {@code target} anywhere on the current source, at {@link Precision#DEFAULT}. */
    public static boolean find(Color target) {
        return find(target, Source.current(), Precision.DEFAULT);
    }

    /** Finds {@code target} anywhere on the current source, at {@code precision}. */
    public static boolean find(Color target, Precision precision) {
        return find(target, Source.current(), precision);
    }

    /** Finds {@code target} within {@code source} (use {@code source.region(...)} to narrow the area). */
    public static boolean find(Color target, CaptureSource source) {
        return find(target, source, Precision.DEFAULT);
    }

    /**
     * Finds {@code target} within {@code source} at {@code precision} — all three of its knobs apply. The
     * best (largest) cluster is stored in {@link VisionContext#getLastColorMatch()}.
     */
    public static boolean find(Color target, CaptureSource source, Precision precision) {
        ColorMatch result = findInternal(target, source, precision);
        VisionContext.setLastColorMatch(result);
        return result.isFound();
    }

    /**
     * Finds every distinct cluster of {@code target} within {@code source}, largest first. The list is stored
     * in {@link VisionContext#getLastColorMatchList()}.
     *
     * @return how many clusters matched
     */
    public static int findAll(Color target, CaptureSource source, Precision precision) {
        List<ColorMatch> all = findAllInternal(target, source, precision);
        VisionContext.setLastColorMatchList(all);
        return all.size();
    }

    /** {@link #findAll(Color, CaptureSource, Precision)} against the current source. */
    public static int findAll(Color target, Precision precision) {
        return findAll(target, Source.current(), precision);
    }

    /**
     * Finds a colour in the inclusive RGB band [{@code low}, {@code high}] — an explicit per-channel range
     * rather than a distance from one colour. Use when you want "any fairly-red pixel" expressed as bounds.
     *
     * <p>Reads only {@code precision}'s {@link Precision#minArea()} and {@link Precision#minCount()}: the
     * band <em>is</em> the colour test here, so there is no target colour for a ΔE tolerance to measure from.
     */
    public static boolean findInRange(Color low, Color high, CaptureSource source, Precision precision) {
        Rect region = source.subRegion();
        BufferedImage img = source.capture();
        if (img == null) {
            VisionContext.setLastColorMatch(ColorMatch.notFound());
            return false;
        }
        List<RawColorMatch> raw = ColorMatcher.findClustersInRange(
                img, low, high, precision.minArea(), precision.minCount());
        List<ColorMatch> mapped = map(raw, source, img, midpoint(low, high));
        VisionContext.setLastColorMatchList(mapped);
        return !mapped.isEmpty();
    }

    /** {@link #findInRange(Color, Color, CaptureSource, Precision)} against the current source. */
    public static boolean findInRange(Color low, Color high) {
        return findInRange(low, high, Source.current(), Precision.DEFAULT);
    }

    // ---------------------------------------------------------------------
    // coverage — "how much of this region is this colour?"
    // ---------------------------------------------------------------------

    /**
     * The fraction (0..1) of {@code source} whose pixels are within {@code precision}'s tolerance of
     * {@code target}. Handy for progress/health bars:
     * {@code Pixel.coverage(Color.GREEN, healthBar, Precision.LOOSE)}.
     *
     * <p>Reads only {@link Precision#deltaE()}. Coverage counts every matching pixel and never clusters, so
     * {@code minArea} has nothing to filter — and {@code minCount} would be a second way to express a
     * question this already answers as a fraction.
     */
    public static double coverage(Color target, CaptureSource source, Precision precision) {
        BufferedImage img = source.capture();
        if (img == null) return 0.0;
        return ColorMatcher.coverage(img, target, precision.deltaE());
    }

    /** {@link #coverage(Color, CaptureSource, Precision)} against the current source. */
    public static double coverage(Color target, Precision precision) {
        return coverage(target, Source.current(), precision);
    }

    // ---------------------------------------------------------------------
    // waitFor — poll until the colour shows up
    // ---------------------------------------------------------------------

    /**
     * Polls until {@code target} appears in {@code source} or {@code timeoutMs} elapses.
     *
     * @return true if it appeared; the match is in {@link VisionContext#getLastColorMatch()}
     */
    public static boolean waitFor(Color target, CaptureSource source, Precision precision, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (find(target, source, precision)) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** {@link #waitFor(Color, CaptureSource, Precision, long)} against the current source. */
    public static boolean waitFor(Color target, Precision precision, long timeoutMs) {
        return waitFor(target, Source.current(), precision, timeoutMs);
    }

    /** Polls until {@code target} is <em>gone</em> from {@code source}, or {@code timeoutMs} elapses. */
    public static boolean waitForGone(Color target, CaptureSource source, Precision precision, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!find(target, source, precision)) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    static ColorMatch findInternal(Color target, CaptureSource source, Precision precision) {
        List<ColorMatch> all = findAllInternal(target, source, precision);
        return all.isEmpty() ? ColorMatch.notFound() : all.get(0);
    }

    static List<ColorMatch> findAllInternal(Color target, CaptureSource source, Precision precision) {
        // A genuine native-load failure surfaces as an Error (e.g. UnsatisfiedLinkError) and is intentionally
        // NOT caught, so it cannot masquerade as "no such colour".
        try {
            BufferedImage img = source.capture();
            if (img == null) return new ArrayList<>();
            List<RawColorMatch> raw = ColorMatcher.findClusters(
                    img, target, precision.deltaE(), precision.minArea(), precision.minCount());
            return map(raw, source, img, target);
        } catch (Exception e) {
            Debug.error("[Vision] error finding colour: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /** Maps internal clusters onto public results, shifting to absolute coords via the source origin. */
    private static List<ColorMatch> map(List<RawColorMatch> raw, CaptureSource source, BufferedImage img,
                                        Color color) {
        Point origin = source.origin();
        int total = img.getWidth() * img.getHeight();
        List<ColorMatch> out = new ArrayList<>(raw.size());
        for (RawColorMatch m : raw) {
            out.add(new ColorMatch(
                    new Point(m.x() + origin.x, m.y() + origin.y),
                    m.width(), m.height(), m.pixelCount(),
                    total == 0 ? 0.0 : m.pixelCount() / (double) total,
                    new Point(m.centroidX() + origin.x, m.centroidY() + origin.y),
                    color));
        }
        return out;
    }

    private static Color midpoint(Color low, Color high) {
        return new Color((low.getRed() + high.getRed()) / 2,
                         (low.getGreen() + high.getGreen()) / 2,
                         (low.getBlue() + high.getBlue()) / 2);
    }
}
