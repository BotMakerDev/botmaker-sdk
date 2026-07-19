package com.botmaker.sdk.api.vision;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.opencv.ColorMatcher;
import com.botmaker.sdk.internal.opencv.RawColorMatch;

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
 * <h2>Two kinds of precision — they are separate knobs</h2>
 * <ul>
 *   <li><b>Colour precision</b> — {@code tolerance}: how far a pixel's colour may sit from the target, as a
 *       <b>CIELAB ΔE</b> distance. Perceptually uniform, so one number behaves the same across hues.
 *       Guide: {@link #EXACT} (0) only identical pixels, {@link #TIGHT} (≈5) the same shade,
 *       {@link #DEFAULT_TOLERANCE} (≈12) the same colour through mild shading/anti-aliasing,
 *       {@link #LOOSE} (≈25) the whole colour family.</li>
 *   <li><b>Location precision</b> — the {@code region} you search, plus {@code minPixels}: the smallest
 *       connected blob that counts. {@code minPixels} is what stops one stray anti-aliased pixel from
 *       reporting a hit; raise it to demand a real patch of colour.</li>
 * </ul>
 *
 * <pre>{@code
 * // Is the health bar still red, in the top-left corner of the game window?
 * CaptureSource hud = CaptureSource.window("MyGame").region(new Rect(10, 10, 200, 30));
 * if (Pixel.find(Color.RED, Pixel.DEFAULT_TOLERANCE, hud, 40)) {
 *     Mouse.click(VisionContext.getLastColorMatch().getCenter());
 * }
 * }</pre>
 */
public class Pixel {

    /** Only pixels of exactly the target colour. */
    public static final double EXACT = 0.0;
    /** The same shade — tolerates little more than compression noise (CIELAB ΔE ≈ 5). */
    public static final double TIGHT = 5.0;
    /** The same colour through mild shading / anti-aliasing (CIELAB ΔE ≈ 12). The default. */
    public static final double DEFAULT_TOLERANCE = 12.0;
    /** The whole colour family — "some kind of red" (CIELAB ΔE ≈ 25). */
    public static final double LOOSE = 25.0;

    /** Default smallest connected blob that counts as a hit — filters out stray anti-aliased pixels. */
    public static final int DEFAULT_MIN_PIXELS = 4;

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

    /** Whether the pixel at ({@code x},{@code y}) is within {@code tolerance} (ΔE) of {@code target}. */
    public static boolean matchesAt(int x, int y, Color target, double tolerance) {
        return matchesAt(x, y, target, tolerance, Source.current());
    }

    /** Whether the pixel at ({@code x},{@code y}) of {@code source} is within {@code tolerance} of {@code target}. */
    public static boolean matchesAt(int x, int y, Color target, double tolerance, CaptureSource source) {
        Color actual = colorAt(x, y, source);
        return actual != null && ColorMatcher.deltaE(actual, target) <= tolerance;
    }

    /** The CIELAB ΔE distance between two colours — the metric {@code tolerance} is measured in. */
    public static double distance(Color a, Color b) {
        return ColorMatcher.deltaE(a, b);
    }

    // ---------------------------------------------------------------------
    // find — colour + location precision
    // ---------------------------------------------------------------------

    /** Finds {@code target} anywhere on the current source, at the default tolerance and min blob size. */
    public static boolean find(Color target) {
        return find(target, DEFAULT_TOLERANCE, Source.current(), DEFAULT_MIN_PIXELS);
    }

    /** Finds {@code target} anywhere on the current source at {@code tolerance} (ΔE). */
    public static boolean find(Color target, double tolerance) {
        return find(target, tolerance, Source.current(), DEFAULT_MIN_PIXELS);
    }

    /** Finds {@code target} within {@code source} (use {@code source.region(...)} to narrow the area). */
    public static boolean find(Color target, double tolerance, CaptureSource source) {
        return find(target, tolerance, source, DEFAULT_MIN_PIXELS);
    }

    /**
     * Finds {@code target} within {@code source}, requiring a connected blob of at least {@code minPixels}.
     * The best (largest) cluster is stored in {@link VisionContext#getLastColorMatch()}.
     */
    public static boolean find(Color target, double tolerance, CaptureSource source, int minPixels) {
        ColorMatch result = findInternal(target, tolerance, source, minPixels);
        VisionContext.setLastColorMatch(result);
        return result.isFound();
    }

    /**
     * Finds every distinct cluster of {@code target} within {@code source}, largest first. The list is stored
     * in {@link VisionContext#getLastColorMatchList()}.
     *
     * @return how many clusters matched
     */
    public static int findAll(Color target, double tolerance, CaptureSource source, int minPixels) {
        List<ColorMatch> all = findAllInternal(target, tolerance, source, minPixels);
        VisionContext.setLastColorMatchList(all);
        return all.size();
    }

    /** {@link #findAll(Color, double, CaptureSource, int)} against the current source. */
    public static int findAll(Color target, double tolerance) {
        return findAll(target, tolerance, Source.current(), DEFAULT_MIN_PIXELS);
    }

    /**
     * Finds a colour in the inclusive RGB band [{@code low}, {@code high}] — an explicit per-channel range
     * rather than a distance from one colour. Use when you want "any fairly-red pixel" expressed as bounds.
     */
    public static boolean findInRange(Color low, Color high, CaptureSource source, int minPixels) {
        Rect region = source.subRegion();
        BufferedImage img = source.capture();
        if (img == null) {
            VisionContext.setLastColorMatch(ColorMatch.notFound());
            return false;
        }
        List<RawColorMatch> raw = ColorMatcher.findClustersInRange(img, low, high, minPixels);
        List<ColorMatch> mapped = map(raw, source, img, midpoint(low, high));
        VisionContext.setLastColorMatchList(mapped);
        return !mapped.isEmpty();
    }

    /** {@link #findInRange(Color, Color, CaptureSource, int)} against the current source. */
    public static boolean findInRange(Color low, Color high) {
        return findInRange(low, high, Source.current(), DEFAULT_MIN_PIXELS);
    }

    // ---------------------------------------------------------------------
    // coverage — "how much of this region is this colour?"
    // ---------------------------------------------------------------------

    /**
     * The fraction (0..1) of {@code source} whose pixels are within {@code tolerance} of {@code target}.
     * Handy for progress/health bars: {@code Pixel.coverage(Color.GREEN, Pixel.LOOSE, healthBar)}.
     */
    public static double coverage(Color target, double tolerance, CaptureSource source) {
        BufferedImage img = source.capture();
        if (img == null) return 0.0;
        return ColorMatcher.coverage(img, target, tolerance);
    }

    /** {@link #coverage(Color, double, CaptureSource)} against the current source. */
    public static double coverage(Color target, double tolerance) {
        return coverage(target, tolerance, Source.current());
    }

    // ---------------------------------------------------------------------
    // waitFor — poll until the colour shows up
    // ---------------------------------------------------------------------

    /**
     * Polls until {@code target} appears in {@code source} or {@code timeoutMs} elapses.
     *
     * @return true if it appeared; the match is in {@link VisionContext#getLastColorMatch()}
     */
    public static boolean waitFor(Color target, double tolerance, CaptureSource source, int minPixels,
                                  long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (find(target, tolerance, source, minPixels)) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** {@link #waitFor(Color, double, CaptureSource, int, long)} against the current source. */
    public static boolean waitFor(Color target, double tolerance, long timeoutMs) {
        return waitFor(target, tolerance, Source.current(), DEFAULT_MIN_PIXELS, timeoutMs);
    }

    /** Polls until {@code target} is <em>gone</em> from {@code source}, or {@code timeoutMs} elapses. */
    public static boolean waitForGone(Color target, double tolerance, CaptureSource source, int minPixels,
                                      long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!find(target, tolerance, source, minPixels)) return true;
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

    static ColorMatch findInternal(Color target, double tolerance, CaptureSource source, int minPixels) {
        List<ColorMatch> all = findAllInternal(target, tolerance, source, minPixels);
        return all.isEmpty() ? ColorMatch.notFound() : all.get(0);
    }

    static List<ColorMatch> findAllInternal(Color target, double tolerance, CaptureSource source,
                                            int minPixels) {
        // A genuine native-load failure surfaces as an Error (e.g. UnsatisfiedLinkError) and is intentionally
        // NOT caught, so it cannot masquerade as "no such colour".
        try {
            BufferedImage img = source.capture();
            if (img == null) return new ArrayList<>();
            List<RawColorMatch> raw = ColorMatcher.findClusters(img, target, tolerance, minPixels);
            return map(raw, source, img, target);
        } catch (Exception e) {
            if (Debug.isEnabled()) {
                System.err.println("Error finding colour: " + e.getMessage());
                e.printStackTrace();
            }
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
