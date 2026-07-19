package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.ocr.OcrEngine;
import com.botmaker.shared.ocr.OcrOptions;
import com.botmaker.shared.ocr.TextResult;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * On-screen text recognition (OCR): read text from a capture source and locate where given text appears.
 *
 * <p>The text counterpart to {@link ImageFinder} (templates) and {@link Pixel} (colour), and it follows the
 * same conventions: every call takes a {@link CaptureSource} (a window, a monitor, or the desktop); a search
 * <em>region</em> is expressed as a {@link CaptureSource#region(Rect) region of a source}; results come back
 * in <b>absolute screen coordinates</b>; the full result is parked in {@link VisionContext} so the
 * boolean-returning calls stay readable; and no-source overloads use {@link Source#current()}.
 *
 * <p>The heavy lifting lives in {@code botmaker-shared}'s {@link OcrEngine} (OpenCV preprocessing +
 * Tesseract), so Studio can reuse it later without depending on the SDK. Tune recognition — languages,
 * page-segmentation mode, upscale, binarize, char whitelist — with an {@link OcrOptions} overload; the
 * default reads whole {@link TextResult.Level#LINE lines} of English so multi-word phrases match.
 *
 * <pre>{@code
 * // Wait for a "Play" button's label, then click it.
 * CaptureSource game = CaptureSource.window("MyGame");
 * if (Text.waitFor("Play", game, 5000)) {
 *     Mouse.click(VisionContext.getLastTextMatch().getCenter());
 * }
 *
 * // Read a numeric HUD counter tuned for digits only.
 * CaptureSource gold = game.region(new Rect(1700, 30, 120, 40));
 * OcrOptions digits = OcrOptions.defaults().withCharWhitelist("0123456789").withUpscale(3.0);
 * String amount = Text.read(gold, digits);
 * }</pre>
 */
public final class Text {

    /**
     * Default OCR options for the facade: {@link OcrOptions#defaults()} at {@link TextResult.Level#LINE},
     * so a whole line is one match and multi-word phrases like {@code "Game Over"} match via substring.
     */
    public static final OcrOptions DEFAULT_OPTIONS = OcrOptions.defaults().withLevel(TextResult.Level.LINE);

    private Text() {}

    // ---------------------------------------------------------------------
    // read — pull all text out of a source
    // ---------------------------------------------------------------------

    /** All recognized text within {@code source}, as one string. Empty string if nothing is read. */
    public static String read(CaptureSource source) {
        return read(source, DEFAULT_OPTIONS);
    }

    /** {@link #read(CaptureSource)} against the current source. */
    public static String read() {
        return read(Source.current(), DEFAULT_OPTIONS);
    }

    /** All recognized text within {@code source} using {@code opts}, as one string. */
    public static String read(CaptureSource source, OcrOptions opts) {
        BufferedImage img = source.capture();
        if (img == null) return "";
        return OcrEngine.text(img, opts);
    }

    // ---------------------------------------------------------------------
    // find — does this text appear? (substring, case-insensitive)
    // ---------------------------------------------------------------------

    /**
     * Whether {@code needle} appears within {@code source} (case-insensitive substring of a recognized
     * line). The matching line is stored in {@link VisionContext#getLastTextMatch()}.
     */
    public static boolean find(String needle, CaptureSource source) {
        return find(needle, source, DEFAULT_OPTIONS);
    }

    /** {@link #find(String, CaptureSource)} against the current source. */
    public static boolean find(String needle) {
        return find(needle, Source.current(), DEFAULT_OPTIONS);
    }

    /** {@link #find(String, CaptureSource)} using {@code opts}. */
    public static boolean find(String needle, CaptureSource source, OcrOptions opts) {
        TextMatch hit = firstMatching(recognize(source, opts), m -> containsIgnoreCase(m.getText(), needle));
        VisionContext.setLastTextMatch(hit);
        return hit.isFound();
    }

    /**
     * Whether some recognized text within {@code source} <em>exactly</em> equals {@code target}
     * (case-insensitive, trimmed) — stricter than {@link #find}. The hit is stored in
     * {@link VisionContext#getLastTextMatch()}.
     */
    public static boolean findExact(String target, CaptureSource source) {
        return findExact(target, source, DEFAULT_OPTIONS);
    }

    /** {@link #findExact(String, CaptureSource)} using {@code opts}. */
    public static boolean findExact(String target, CaptureSource source, OcrOptions opts) {
        TextMatch hit = firstMatching(recognize(source, opts),
                m -> m.getText() != null && m.getText().trim().equalsIgnoreCase(target.trim()));
        VisionContext.setLastTextMatch(hit);
        return hit.isFound();
    }

    /**
     * Whether some recognized text within {@code source} matches the regular expression {@code regex}
     * (via {@link java.util.regex.Matcher#find}). The hit is stored in
     * {@link VisionContext#getLastTextMatch()}.
     */
    public static boolean findMatching(String regex, CaptureSource source) {
        return findMatching(regex, source, DEFAULT_OPTIONS);
    }

    /** {@link #findMatching(String, CaptureSource)} using {@code opts}. */
    public static boolean findMatching(String regex, CaptureSource source, OcrOptions opts) {
        Pattern pattern = Pattern.compile(regex);
        TextMatch hit = firstMatching(recognize(source, opts),
                m -> m.getText() != null && pattern.matcher(m.getText()).find());
        VisionContext.setLastTextMatch(hit);
        return hit.isFound();
    }

    // ---------------------------------------------------------------------
    // findFuzzy — approximate match, tolerant of OCR noise (edit distance)
    // ---------------------------------------------------------------------

    /** Default edit-distance tolerance for {@link #findFuzzy}: 2 characters (e.g. {@code "P1ay"} ≈ {@code "Play"}). */
    public static final int DEFAULT_FUZZY_DISTANCE = 2;

    /**
     * Whether {@code needle} appears within {@code source} <em>approximately</em> — case-insensitive, tolerating
     * up to {@link #DEFAULT_FUZZY_DISTANCE} character edits (insertions/deletions/substitutions). Forgives the
     * usual OCR misreads ({@code l↔1}, {@code O↔0}, a dropped letter) that make {@link #find}'s exact substring
     * miss. The matching line is stored in {@link VisionContext#getLastTextMatch()}.
     */
    public static boolean findFuzzy(String needle, CaptureSource source) {
        return findFuzzy(needle, DEFAULT_FUZZY_DISTANCE, source, DEFAULT_OPTIONS);
    }

    /** {@link #findFuzzy(String, CaptureSource)} against the current source. */
    public static boolean findFuzzy(String needle) {
        return findFuzzy(needle, DEFAULT_FUZZY_DISTANCE, Source.current(), DEFAULT_OPTIONS);
    }

    /** {@link #findFuzzy(String, CaptureSource)} with an explicit {@code maxDistance} edit tolerance. */
    public static boolean findFuzzy(String needle, int maxDistance, CaptureSource source) {
        return findFuzzy(needle, maxDistance, source, DEFAULT_OPTIONS);
    }

    /** {@link #findFuzzy(String, int, CaptureSource)} using {@code opts}. */
    public static boolean findFuzzy(String needle, int maxDistance, CaptureSource source, OcrOptions opts) {
        TextMatch hit = firstMatching(recognize(source, opts), m -> fuzzyContains(m.getText(), needle, maxDistance));
        VisionContext.setLastTextMatch(hit);
        return hit.isFound();
    }

    // ---------------------------------------------------------------------
    // findAll — every place this text appears
    // ---------------------------------------------------------------------

    /**
     * Every recognized text within {@code source} containing {@code needle} (case-insensitive). The list
     * is stored in {@link VisionContext#getLastTextMatchList()}.
     *
     * @return how many matched
     */
    public static int findAll(String needle, CaptureSource source) {
        return findAll(needle, source, DEFAULT_OPTIONS);
    }

    /** {@link #findAll(String, CaptureSource)} using {@code opts}. */
    public static int findAll(String needle, CaptureSource source, OcrOptions opts) {
        List<TextMatch> hits = new ArrayList<>();
        for (TextMatch m : recognize(source, opts)) {
            if (containsIgnoreCase(m.getText(), needle)) hits.add(m);
        }
        VisionContext.setLastTextMatchList(hits);
        return hits.size();
    }

    /**
     * Every piece of recognized text within {@code source}, whatever it says — a general "read everything,
     * with positions" call. The list is stored in {@link VisionContext#getLastTextMatchList()}.
     *
     * @return how many pieces of text were recognized
     */
    public static int readAll(CaptureSource source, OcrOptions opts) {
        List<TextMatch> all = recognize(source, opts);
        VisionContext.setLastTextMatchList(all);
        return all.size();
    }

    /** {@link #readAll(CaptureSource, OcrOptions)} at the default options. */
    public static int readAll(CaptureSource source) {
        return readAll(source, DEFAULT_OPTIONS);
    }

    // ---------------------------------------------------------------------
    // waitFor — poll until the text shows up / goes away
    // ---------------------------------------------------------------------

    /**
     * Polls until {@code needle} appears in {@code source} or {@code timeoutMs} elapses.
     *
     * @return true if it appeared; the match is in {@link VisionContext#getLastTextMatch()}
     */
    public static boolean waitFor(String needle, CaptureSource source, long timeoutMs) {
        return waitFor(needle, source, DEFAULT_OPTIONS, timeoutMs);
    }

    /** {@link #waitFor(String, CaptureSource, long)} using {@code opts}. */
    public static boolean waitFor(String needle, CaptureSource source, OcrOptions opts, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (find(needle, source, opts)) return true;
            if (sleep()) return false;
        }
        return false;
    }

    /** Polls until {@code needle} is <em>gone</em> from {@code source}, or {@code timeoutMs} elapses. */
    public static boolean waitForGone(String needle, CaptureSource source, long timeoutMs) {
        return waitForGone(needle, source, DEFAULT_OPTIONS, timeoutMs);
    }

    /** {@link #waitForGone(String, CaptureSource, long)} using {@code opts}. */
    public static boolean waitForGone(String needle, CaptureSource source, OcrOptions opts, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!find(needle, source, opts)) return true;
            if (sleep()) return false;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    /**
     * Recognizes {@code source} and maps every {@link TextResult} (source-local coords) onto a
     * {@link TextMatch} (absolute screen coords) via the source origin — the OCR analogue of
     * {@code Pixel.map(...)}. A genuine native-load failure surfaces as an {@link Error} and is
     * intentionally not caught, so it cannot masquerade as "no text".
     */
    private static List<TextMatch> recognize(CaptureSource source, OcrOptions opts) {
        try {
            BufferedImage img = source.capture();
            if (img == null) return new ArrayList<>();
            List<TextResult> raw = OcrEngine.recognize(img, opts);
            Point origin = source.origin();
            List<TextMatch> out = new ArrayList<>(raw.size());
            for (TextResult r : raw) {
                Rectangle b = r.bounds();
                Rect abs = new Rect(
                        (int) (b.x + origin.x),
                        (int) (b.y + origin.y),
                        b.width, b.height);
                out.add(new TextMatch(r.text(), abs, r.confidence()));
            }
            return out;
        } catch (Exception e) {
            if (ClickConfig.DEBUG_MODE) {
                System.err.println("Error reading text: " + e.getMessage());
                e.printStackTrace();
            }
            return new ArrayList<>();
        }
    }

    private static TextMatch firstMatching(List<TextMatch> matches, java.util.function.Predicate<TextMatch> pred) {
        for (TextMatch m : matches) {
            if (pred.test(m)) return m;
        }
        return TextMatch.notFound();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    /**
     * Whether {@code line} approximately contains {@code needle} within {@code maxDistance} edits
     * (case-insensitive). Exact substring is an early yes (distance 0); otherwise the needle is slid across the
     * line one window at a time (window = the needle's length ± {@code maxDistance}) and the best
     * {@link #levenshtein} distance is compared to the budget — so a short needle can match inside a longer
     * noisy line without the line's extra length counting against it.
     */
    private static boolean fuzzyContains(String line, String needle, int maxDistance) {
        if (line == null || needle == null) return false;
        if (maxDistance < 0) maxDistance = 0;
        String hay = line.toLowerCase();
        String pat = needle.toLowerCase();
        if (pat.isEmpty()) return true;
        if (hay.contains(pat)) return true;                       // distance 0 fast path
        if (hay.length() < pat.length() - maxDistance) return false;
        int min = Math.max(1, pat.length() - maxDistance);
        int max = pat.length() + maxDistance;
        for (int start = 0; start < hay.length(); start++) {
            int limit = Math.min(hay.length(), start + max);
            for (int end = start + min; end <= limit; end++) {
                if (levenshtein(hay.substring(start, end), pat) <= maxDistance) return true;
            }
        }
        return false;
    }

    /** Classic iterative two-row Levenshtein edit distance between {@code a} and {@code b}. */
    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }

    /** Sleeps the poll interval; returns true if interrupted (caller should abort the wait). */
    private static boolean sleep() {
        try {
            Thread.sleep(100);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
