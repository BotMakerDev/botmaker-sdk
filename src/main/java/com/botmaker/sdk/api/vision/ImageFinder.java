package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.observe.MatchEvent;
import com.botmaker.sdk.internal.observe.Surface;
import com.botmaker.sdk.internal.trace.Trace;
import com.botmaker.shared.opencv.OpencvManager;
import com.botmaker.shared.opencv.RawMatch;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Single-frame image lookup: does this template appear right now, and where?
 *
 * <p>Every matcher takes a {@link CaptureSource} — one of a {@link CaptureSource#window(String) window},
 * a {@link CaptureSource#monitor(int) monitor}, or the whole {@link CaptureSource#desktop() desktop} — so a
 * search can be pinned to a window or a single screen and still return absolute, clickable coordinates. A
 * search <em>region</em> is expressed as a {@link CaptureSource#region(com.botmaker.sdk.api.geometry.Rect) region of a
 * source}, not a separate parameter. The no-source overloads default to the whole desktop.
 *
 * <p>Besides {@code find}/{@code findAll}/{@code findAny} this class owns the lambda control-flow helpers
 * ({@link #whileFind}, {@link #untilFind}, {@link #ifFind}) — each is one capture that hands the matched
 * {@link MatchResult} to your action.
 *
 * <p><b>Two shapes per operation</b>: the plain form, which searches {@link Source#current()}, and the
 * {@code CaptureSource} form. How sure a match must be is {@link BotSettings}' {@code confidence}, and how far a
 * good template must out-score a bad one is its {@code compareMargin}; a bot that wants other values for a while
 * calls {@code BotSettings.use(…)}. The per-call {@code double} overloads that answered those questions a second
 * time, and the {@code ImageTemplate...} spellings of the group operations, were deleted on 2026-10-01.
 */
@Palette(category = "vision", categoryLabel = "Vision", icon = "🔍")
public class ImageFinder {

    // --- find (single template) ---

    /**
     * Finds the specified template on the current capture source.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template the image template to search for
     * @return true if the template was found, false otherwise
     */
    public static boolean find(ImageTemplate template) {
        return find(template, Source.current());
    }

    /**
     * Finds the specified template on a specific capture source. The returned match result contains absolute
     * screen coordinates, so a click can land directly on {@link Vision#lastMatch()}.
     *
     * @param template the image template to search for
     * @param source   the capture source (window, monitor, or desktop region) to search within
     * @return true if the template was found, false otherwise
     */
    public static boolean find(ImageTemplate template, CaptureSource source) {
        PopupGuard.check();
        MatchResult result = findInternal(template, source, confidence());
        Vision.setLastMatch(result);
        return result.isFound();
    }

    /**
     * The best place {@code template} sits on {@code source} at <em>any</em> score, as a result whose
     * {@link MatchResult#confidence()} is that score; a result that is not found only when nothing could be
     * matched at all (no capture, a picture larger than the frame).
     *
     * <p>For the overlay editor's live probe, which shows how close a picture is rather than only whether it
     * clears the bot's confidence. It stores nothing in {@link Vision}, sends no telemetry and writes no trace,
     * so asking it twice a second leaves no mark. A bot asks {@link #find}.
     */
    @Hidden("the overlay editor's probe; a bot asks find(), which applies its confidence")
    public static MatchResult bestMatch(ImageTemplate template, CaptureSource source) {
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) return MatchResult.notFound();
            background = OpencvManager.bufferedImageToMat(screenshot);
            RawMatch best = OpencvManager.findBest(template.getMat(), background, false, template.authoredSize());
            if (best == null) return MatchResult.notFound();
            Point origin = source.origin();
            return new MatchResult(new Point(best.x() + origin.x(), best.y() + origin.y()), best.width(),
                    best.height(), best.score(), template.id());
        } catch (RuntimeException e) {
            return MatchResult.notFound();
        } finally {
            if (background != null) background.release();
        }
    }

    /** The confidence every match uses: {@link BotSettings}'. */
    static double confidence() {
        return BotSettings.current().confidence();
    }

    /**
     * Internal method for findAny that returns MatchResult.
     * Does not update Vision - caller is responsible for that.
     */
    static MatchResult findAnyInternal(CaptureSource source, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            MatchResult result = findInternal(template, source, confidence);
            if (result.isFound()) {
                return result;
            }
        }
        return MatchResult.notFound();
    }

    /**
     * Internal: one frame — the best match of <em>every</em> template in {@code group} that clears
     * {@code confidence}, all read from a <b>single</b> capture, together with the screenshot they were read
     * from. Does not update Vision — the caller is responsible for that.
     *
     * <p>This cannot delegate to {@link #findAnyInternal}: that one short-circuits on the first hit, which is
     * precisely the information loss {@link Matches} exists to undo. It captures once and re-matches the same
     * background {@link Mat} per template instead of calling {@link #findInternal} in a loop, so N templates
     * still cost one screenshot — the "one capture per check" property the lambda helpers promise — and so
     * every answer in the returned {@code Matches} describes the same instant.
     */
    static Vision.Frame findFrame(ImageTemplateGroup group, CaptureSource source, double confidence) {
        if (group.isEmpty()) {
            // nothing to look for — don't pay for a capture to prove it
            return new Vision.Frame(Matches.none(), source, null, group);
        }
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return new Vision.Frame(Matches.none(), source, null, group);
            }
            background = OpencvManager.bufferedImageToMat(screenshot);
            Point origin = source.origin();

            List<MatchResult> results = new ArrayList<>();
            for (ImageTemplate template : group.templates()) {
                RawMatch best = OpencvManager.findBest(template.getMat(), background, false, template.authoredSize());
                if (best != null && best.score() >= confidence) {
                    Point location = new Point(best.x() + origin.x(), best.y() + origin.y());
                    MatchResult result = new MatchResult(
                            location, best.width(), best.height(), best.score(), template.id());
                    emitMatch(source, result);
                    traceHit(template, result);
                    results.add(result);
                } else {
                    emitMatch(source, MatchResult.miss(best != null ? best.score() : 0.0));
                    traceMiss(template);
                }
            }
            return new Vision.Frame(Matches.of(results), source, screenshot, group);

        } catch (Exception e) {
            if (Debug.isEnabled()) {
                Debug.error("Error finding template group: " + e.getMessage(), e);
            }
            return new Vision.Frame(Matches.none(), source, null, group);
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    /**
     * Internal method that performs the actual find operation and returns the MatchResult.
     * Does not update Vision - caller is responsible for that.
     */
    static MatchResult findInternal(ImageTemplate template, CaptureSource source, double confidence) {
        // Note: a genuine native-load failure surfaces as an Error (e.g. UnsatisfiedLinkError),
        // which is intentionally NOT caught here so it cannot masquerade as "not found".
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return MatchResult.notFound();
            }
            background = OpencvManager.bufferedImageToMat(screenshot);

            // Get the raw best match (below-threshold included) so a miss can still report its real score.
            RawMatch best = OpencvManager.findBest(template.getMat(), background, false, template.authoredSize());

            if (best != null && best.score() >= confidence) {
                Point origin = source.origin();
                Point location = new Point(best.x() + origin.x(), best.y() + origin.y());
                MatchResult result = new MatchResult(
                        location, best.width(), best.height(), best.score(), template.id());
                emitMatch(source, result);
                traceHit(template, result);
                return result;
            }

            // Miss: emit telemetry carrying the best near-miss score (so the dashboard shows why it's
            // borderline), but return not-found result.
            MatchResult missResult = MatchResult.miss(best != null ? best.score() : 0.0);
            emitMatch(source, missResult);
            traceMiss(template);
            return MatchResult.notFound();

        } catch (Exception e) {
            if (Debug.isEnabled()) {
                Debug.error("Error finding template: " + e.getMessage(), e);
            }
            return MatchResult.notFound();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    // --- findAny: the first template in the group, in order, that clears the threshold ---

    /**
     * Finds the first template in the group (in order) that appears on the current capture source.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param group the template group to search for, in priority order
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findAny(ImageTemplateGroup group) {
        return findAny(group, Source.current());
    }

    /**
     * Finds the first template in the group (in order) that appears on a specific capture source.
     *
     * @param group  the template group to search for, in priority order
     * @param source the capture source to search within
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findAny(ImageTemplateGroup group, CaptureSource source) {
        PopupGuard.check();
        MatchResult result = findAnyInternal(source, confidence(), group.toArray());
        Vision.setLastMatch(result);
        return result.isFound();
    }

    // --- Best match: evaluate fully and return the single highest-scoring match ---

    /**
     * Finds the highest-scoring match for any template in the group on the current capture source.
     * Unlike {@link #findAny(ImageTemplateGroup)}, this evaluates every template and returns the best match
     * regardless of order.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param group the template group to search for
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findBest(ImageTemplateGroup group) {
        return findBest(group, Source.current());
    }

    /**
     * Finds the highest-scoring match for any template in the group on a specific capture source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findBest(ImageTemplateGroup group, CaptureSource source) {
        PopupGuard.check();
        double confidence = confidence();
        MatchResult best = MatchResult.notFound();
        for (ImageTemplate template : group.templates()) {
            MatchResult result = findInternal(template, source, confidence);
            if (result.isFound() && (!best.isFound() || result.confidence() > best.confidence())) {
                best = result;
            }
        }
        Vision.setLastMatch(best);
        return best.isFound();
    }

    // --- Compare: a "good" template must out-score similar "bad" ones at the same location ---

    /** Padding (px) around a candidate location when re-scoring a competing template there. */
    private static final int COMPARE_PAD = 4;

    /**
     * Among the {@code good} templates, return the best-scoring match that still beats every {@code bad}
     * template at its location by {@link BotSettings}' compare margin.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param good the group of good templates to search for
     * @param bad  the group of bad templates that must NOT out-score the good templates
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return findCompare(good, bad, Source.current());
    }

    /**
     * {@link #findCompare(ImageTemplateGroup, ImageTemplateGroup)} searched within {@code source}.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        PopupGuard.check();
        MatchResult result = compare(good.templates(), bad.templates(), source, confidence(), margin());
        Vision.setLastMatch(result);
        return result.isFound();
    }

    /** The margin every compare uses: {@link BotSettings}'. */
    static double margin() {
        return BotSettings.current().compareMargin();
    }

    // --- findAnyCompare: the FIRST good template (in order) that beats every bad template ---

    /**
     * Return the first {@code good} template (in priority order) whose best match beats every
     * {@code bad} template at its location by the compare margin. Unlike {@link #findCompare} (which
     * returns the single highest-scoring good), this stops at the first good that wins — the compare
     * analogue of {@link #findAny}.
     * <p>
     * The match result is stored in {@link Vision}.
     *
     * @param good the group of good templates to search for, in priority order
     * @param bad  the group of bad templates that must NOT out-score the good template
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findAnyCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return findAnyCompare(good, bad, Source.current());
    }

    /**
     * {@link #findAnyCompare(ImageTemplateGroup, ImageTemplateGroup)} searched within {@code source}.
     *
     * @param good   the group of good templates to search for, in priority order
     * @param bad    the group of bad templates that must NOT out-score the good template
     * @param source the capture source to search within
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findAnyCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        PopupGuard.check();
        MatchResult result = compareAny(good.templates(), bad.templates(), source, confidence(), margin());
        Vision.setLastMatch(result);
        return result.isFound();
    }

    // --- findAllCompare: EVERY good match (across every good template) that beats all bad templates ---

    /**
     * Find every location of every {@code good} template that beats all {@code bad} templates there by
     * the compare margin — the compare analogue of {@link #findAll}.
     * <p>
     * The list of matches is stored in {@link Vision} ({@link Vision#lastMatchList()}).
     *
     * @param good the group of good templates to search for
     * @param bad  the group of bad templates that must NOT out-score a good match
     * @return the number of winning good matches found
     */
    public static int findAllCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return findAllCompare(good, bad, Source.current());
    }

    /**
     * {@link #findAllCompare(ImageTemplateGroup, ImageTemplateGroup)} searched within {@code source}.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score a good match
     * @param source the capture source to search within
     * @return the number of winning good matches found
     */
    public static int findAllCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        PopupGuard.check();
        List<MatchResult> results = compareAll(good.templates(), bad.templates(), source, confidence(), margin());
        Vision.setLastMatchList(results);
        return results.size();
    }

    /**
     * Single-capture compare: find each good template's best match, keep the highest-scoring good
     * whose location out-scores every bad template (re-scored on the same frame) by {@code margin}.
     * This is an internal method - callers are responsible for updating Vision.
     */
    private static MatchResult compare(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                       CaptureSource source, double confidence, double margin) {
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return MatchResult.notFound();
            }
            background = OpencvManager.bufferedImageToMat(screenshot);

            Point origin = source.origin();
            int offsetX = origin.x();
            int offsetY = origin.y();

            MatchResult best = MatchResult.notFound();
            for (ImageTemplate good : goods) {
                RawMatch gm = OpencvManager.findBestMatch(good.getMat(), background, false, confidence,
                        good.authoredSize());
                if (gm == null) {
                    continue;
                }
                if (beatsAllBads(gm, bads, background, margin) && (!best.isFound() || gm.score() > best.confidence())) {
                    best = new MatchResult(
                            new Point(gm.x() + offsetX, gm.y() + offsetY),
                            gm.width(), gm.height(), gm.score(), good.id());
                }
            }
            emitMatch(source, best);
            return best;
        } catch (Exception e) {
            if (Debug.isEnabled()) {
                Debug.error("Error in compare: " + e.getMessage(), e);
            }
            return MatchResult.notFound();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    /**
     * Single-capture compare, first-wins: return the first good template (in order) whose best match
     * out-scores every bad template (re-scored on the same frame) by {@code margin}. Internal — callers
     * update Vision.
     */
    private static MatchResult compareAny(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                          CaptureSource source, double confidence, double margin) {
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return MatchResult.notFound();
            }
            background = OpencvManager.bufferedImageToMat(screenshot);

            Point origin = source.origin();
            int offsetX = origin.x();
            int offsetY = origin.y();

            for (ImageTemplate good : goods) {
                RawMatch gm = OpencvManager.findBestMatch(good.getMat(), background, false, confidence,
                        good.authoredSize());
                if (gm == null) {
                    continue;
                }
                if (beatsAllBads(gm, bads, background, margin)) {
                    MatchResult result = new MatchResult(
                            new Point(gm.x() + offsetX, gm.y() + offsetY),
                            gm.width(), gm.height(), gm.score(), good.id());
                    emitMatch(source, result);
                    return result;
                }
            }
            emitMatch(source, MatchResult.notFound());
            return MatchResult.notFound();
        } catch (Exception e) {
            if (Debug.isEnabled()) {
                Debug.error("Error in compareAny: " + e.getMessage(), e);
            }
            return MatchResult.notFound();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    /**
     * Single-capture compare, every-location: for every good template, keep each match location that
     * out-scores every bad template (re-scored on the same frame) by {@code margin}. Internal — callers
     * update Vision.
     */
    private static List<MatchResult> compareAll(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                                CaptureSource source, double confidence, double margin) {
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return new ArrayList<>();
            }
            background = OpencvManager.bufferedImageToMat(screenshot);

            Point origin = source.origin();
            int offsetX = origin.x();
            int offsetY = origin.y();

            List<MatchResult> results = new ArrayList<>();
            for (ImageTemplate good : goods) {
                List<RawMatch> matches = OpencvManager.findMultipleMatches(good.getMat(), background, false,
                        confidence, good.authoredSize());
                for (RawMatch gm : matches) {
                    if (beatsAllBads(gm, bads, background, margin)) {
                        results.add(new MatchResult(
                                new Point(gm.x() + offsetX, gm.y() + offsetY),
                                gm.width(), gm.height(), gm.score(), good.id()));
                    }
                }
            }
            emitMatches(source, results);
            return results;
        } catch (Exception e) {
            if (Debug.isEnabled()) {
                Debug.error("Error in compareAll: " + e.getMessage(), e);
            }
            return new ArrayList<>();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    /** Whether {@code good}'s match location out-scores every bad template (re-scored there) by {@code margin}. */
    private static boolean beatsAllBads(RawMatch good, List<ImageTemplate> bads, Mat background, double margin) {
        for (ImageTemplate bad : bads) {
            double badScore = OpencvManager.scoreAround(
                    bad.getMat(), background, false, good.x(), good.y(), COMPARE_PAD);
            if (badScore >= good.score() - margin) {
                return false;
            }
        }
        return true;
    }

    // --- findAll (every location above the threshold) ---

    /**
     * Finds all occurrences of the template on the current capture source.
     * <p>
     * The list of match results is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatchList()}.
     *
     * @param template the image template to search for
     * @return the number of matches found
     */
    public static int findAll(ImageTemplate template) {
        return findAll(template, Source.current());
    }

    /**
     * Finds all occurrences of the template on a specific capture source.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @return the number of matches found
     */
    public static int findAll(ImageTemplate template, CaptureSource source) {
        PopupGuard.check();
        List<MatchResult> results = findAllInternal(template, source, confidence());
        Vision.setLastMatchList(results);
        return results.size();
    }

    /**
     * Internal method that performs findAll and returns the list of results.
     * Does not update Vision - caller is responsible for that.
     */
    static List<MatchResult> findAllInternal(ImageTemplate template, CaptureSource source, double confidence) {
        BufferedImage screenshot = source.capture();
        if (screenshot == null) {
            return new ArrayList<>();
        }
        return findAllIn(screenshot, template, source, confidence);
    }

    /**
     * Internal: every location of {@code template} in <em>already-captured</em> pixels, at or above
     * {@code confidence}, in absolute coordinates via {@code source}'s origin.
     *
     * <p>Split out of {@link #findAllInternal} so the frame-scoped
     * {@link ImageClicker#clickAllLast() clickAllLast} can ask "and where else is this?" of the screenshot the
     * enclosing group check already took, instead of taking another one. The two answers then describe the same
     * instant, which is the whole reason the frame retains its pixels.
     */
    static List<MatchResult> findAllIn(BufferedImage screenshot, ImageTemplate template, CaptureSource source,
                                       double confidence) {
        Mat background = null;
        try {
            background = OpencvManager.bufferedImageToMat(screenshot);

            List<RawMatch> matches =
                    OpencvManager.findMultipleMatches(template.getMat(), background, false, confidence,
                            template.authoredSize());

            Point origin = source.origin();
            int offsetX = origin.x();
            int offsetY = origin.y();

            List<MatchResult> results = matches.stream()
                    .map(r -> {
                        Point location = new Point(r.x() + offsetX, r.y() + offsetY);
                        return new MatchResult(location, r.width(), r.height(), r.score(), template.id());
                    })
                    .collect(Collectors.toList());

            emitMatches(source, results);
            return results;

        } catch (Exception e) {
            if (Debug.isEnabled()) {
                Debug.error("Error in findAll: " + e.getMessage(), e);
            }
            return new ArrayList<>();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    // --- findAll over an ImageTemplateGroup: every location of every template above the threshold ---

    /**
     * Finds all occurrences of every template in the group on the current capture source.
     * <p>
     * The list of match results is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatchList()}.
     *
     * @param group the template group to search for
     * @return the total number of matches found across all templates in the group
     */
    public static int findAll(ImageTemplateGroup group) {
        return findAll(group, Source.current());
    }

    /**
     * Finds all occurrences of every template in the group on a specific capture source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return the total number of matches found across all templates in the group
     */
    public static int findAll(ImageTemplateGroup group, CaptureSource source) {
        PopupGuard.check();
        double confidence = confidence();
        List<MatchResult> all = new ArrayList<>();
        for (ImageTemplate template : group.templates()) {
            all.addAll(findAllInternal(template, source, confidence));
        }
        Vision.setLastMatchList(all);
        return all.size();
    }

    // --- Debug trace: what the bot is looking at, without the wall of misses ---
    //
    // A hit is rare and interesting, so it prints. A miss is neither: untilFind/waitFor poll many times a
    // second and every poll misses until the last one, so printing each would bury every other line in the
    // log. Misses are therefore counted per template and reported once when the run ends — the template is
    // finally found, or the run gets old enough that staying silent would read as a hang. The counter lives
    // here rather than at each call site because "how long has this template been missing" is a property of
    // the template, not of the particular find overload that happened to ask.

    private static final Trace.Runs MISSES = new Trace.Runs();

    /** Records a hit and prints it, closing (and reporting) any run of misses it just ended. */
    private static void traceHit(ImageTemplate template, MatchResult result) {
        if (!Debug.isEnabled()) return;
        reportMisses(template.id(), MISSES.flush(template.id()));
        // The centre rather than the top-left: it is the point a click would land on, so the number in the
        // log is the number to compare against where the pointer actually went.
        Point centre = result.center();
        Trace.log("find " + template.id() + " → (" + centre.x() + "," + centre.y() + ") "
                + String.format(Locale.ROOT, "%.2f", result.confidence()), result.rect());
    }

    /** Records a miss, printing only when its run has gone on long enough to be worth saying so. */
    private static void traceMiss(ImageTemplate template) {
        if (!Debug.isEnabled()) return;
        reportMisses(template.id(), MISSES.tick(template.id()));
    }

    private static void reportMisses(String templateId, Trace.Runs.Run run) {
        if (run != null) {
            Trace.log("" + templateId + " not found", run);
        }
    }

    // --- Observability: report each match attempt to registered BotObservers (see internal.observe.Bots) ---
    // Guarded by hasObservers() so a normal bot run (no observer) builds nothing and pays nothing.
    // The Surface is the source's whole-surface identity (window/screen); the region is its sub-rectangle
    // when the source was narrowed with CaptureSource.region(...), else null.

    private static void emitMatch(CaptureSource source, MatchResult result) {
        if (Bots.hasObservers()) {
            Bots.fireMatch(new MatchEvent(Surface.of(source.base()), source.subRegion(), result));
        }
    }

    private static void emitMatches(CaptureSource source, List<MatchResult> results) {
        if (!Bots.hasObservers()) return;
        Surface surface = Surface.of(source.base());
        if (results.isEmpty()) {
            Bots.fireMatch(new MatchEvent(surface, source.subRegion(), MatchResult.notFound()));
            return;
        }
        for (MatchResult result : results) {
            Bots.fireMatch(new MatchEvent(surface, source.subRegion(), result));
        }
    }

    // --- Lambda control-flow: act on the live match, one capture per check ---

    /**
     * Run {@code action} once with the match if {@code template} is currently visible.
     * The match result is stored in {@link Vision}.
     *
     * @param template the image template to search for
     * @param action   the action to run with the match result
     * @return true if the template was found and the action was run, false otherwise
     */
    public static boolean ifFind(ImageTemplate template, Consumer<MatchResult> action) {
        return ifFind(template, Source.current(), action);
    }

    /**
     * Run {@code action} once with the match if {@code template} is currently visible on a specific source.
     * The match result is stored in {@link Vision}.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @param action   the action to run with the match result
     * @return true if the template was found and the action was run, false otherwise
     */
    public static boolean ifFind(ImageTemplate template, CaptureSource source, Consumer<MatchResult> action) {
        PopupGuard.check();
        MatchResult result = findInternal(template, source, BotSettings.current().confidence());
        Vision.setLastMatch(result);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /**
     * Keep running {@code action} (with the fresh match each time) as long as {@code template} stays visible.
     *
     * @param template the image template to search for
     * @param action   the action to run with each match result
     */
    public static void whileFind(ImageTemplate template, Consumer<MatchResult> action) {
        whileFind(template, Source.current(), action);
    }

    /**
     * Keep running {@code action} (with the fresh match each time) as long as {@code template} stays visible on a specific source.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @param action   the action to run with each match result
     */
    public static void whileFind(ImageTemplate template, CaptureSource source, Consumer<MatchResult> action) {
        PopupGuard.check();
        MatchResult result;
        while ((result = findInternal(template, source, BotSettings.current().confidence())).isFound()) {
            Vision.setLastMatch(result);
            action.accept(result);
        }
        Vision.setLastMatch(MatchResult.notFound());
    }

    /**
     * Keep running {@code action} until {@code template} appears (no match found while it's absent).
     *
     * @param template the image template to search for
     * @param action   the action to run
     */
    public static void untilFind(ImageTemplate template, Runnable action) {
        untilFind(template, Source.current(), action);
    }

    /**
     * Keep running {@code action} until {@code template} appears on a specific source.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @param action   the action to run
     */
    public static void untilFind(ImageTemplate template, CaptureSource source, Runnable action) {
        while (!find(template, source)) {
            action.run();
        }
    }

    // --- Lambda control-flow over a group: "Any" (at least one visible) / "All" (every one visible) ---
    //
    // Both hand your action a Matches — every template of the group that cleared the threshold in that one
    // frame, so the body can branch on the *combination* that is present. That is the question a bot actually
    // asks ("a mail popup AND its claim-all button?"), and neither of the shapes these used to have could
    // answer it: the "Any" variants passed a single MatchResult (the first hit — a second template visible in
    // the same frame was simply invisible to you), and the "All" variants passed a bare Runnable on the
    // grounds that "every template is present" has no single meaningful MatchResult. Matches is the type that
    // answers that, so both old shapes are gone.

    /**
     * Run {@code action} once with the frame's matches if any template in the group is currently visible.
     * The matches are stored in {@link Vision}.
     *
     * @param group  the template group to search for
     * @param action the action to run with the frame's matches
     * @return true if any template was found and the action was run, false otherwise
     */
    public static boolean ifFindAny(ImageTemplateGroup group, Consumer<Matches> action) {
        return ifFindAny(group, Source.current(), action);
    }

    /**
     * Run {@code action} once with the frame's matches if any template in the group is currently visible on a
     * specific source. The matches are stored in {@link Vision}.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run with the frame's matches
     * @return true if any template was found and the action was run, false otherwise
     */
    public static boolean ifFindAny(ImageTemplateGroup group, CaptureSource source, Consumer<Matches> action) {
        PopupGuard.check();
        Vision.Frame frame = findFrame(group, source, BotSettings.current().confidence());
        Vision.setLastMatches(frame.matches());
        if (!frame.matches().isEmpty()) {
            Vision.runInFrame(frame, action);
            return true;
        }
        return false;
    }

    /**
     * Run {@code action} once with the frame's matches if all templates in the group are currently visible.
     *
     * @param group  the template group to search for
     * @param action the action to run with the frame's matches
     * @return true if all templates were found and the action was run, false otherwise
     */
    public static boolean ifFindAll(ImageTemplateGroup group, Consumer<Matches> action) {
        return ifFindAll(group, Source.current(), action);
    }

    /**
     * Run {@code action} once with the frame's matches if all templates in the group are currently visible on a
     * specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run with the frame's matches
     * @return true if all templates were found and the action was run, false otherwise
     */
    public static boolean ifFindAll(ImageTemplateGroup group, CaptureSource source, Consumer<Matches> action) {
        if (group.isEmpty()) return false;   // "all of nothing" is vacuously true; an empty group matches nothing
        PopupGuard.check();
        Vision.Frame frame = findFrame(group, source, BotSettings.current().confidence());
        Vision.setLastMatches(frame.matches());
        if (frame.matches().hasAll(group.toArray())) {
            Vision.runInFrame(frame, action);
            return true;
        }
        return false;
    }

    /**
     * Keep running {@code action} (with a fresh frame's matches each time) as long as any template in the group
     * stays visible.
     *
     * @param group  the template group to search for
     * @param action the action to run with each frame's matches
     */
    public static void whileFindAny(ImageTemplateGroup group, Consumer<Matches> action) {
        whileFindAny(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} (with a fresh frame's matches each time) as long as any template in the group
     * stays visible on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run with each frame's matches
     */
    public static void whileFindAny(ImageTemplateGroup group, CaptureSource source, Consumer<Matches> action) {
        PopupGuard.check();
        Vision.Frame frame;
        while (!(frame = findFrame(group, source, BotSettings.current().confidence())).matches().isEmpty()) {
            Vision.runInFrame(frame, action);
        }
        Vision.setLastMatches(Matches.none());
    }

    /**
     * Keep running {@code action} (with a fresh frame's matches each time) as long as all templates in the
     * group stay visible.
     *
     * @param group  the template group to search for
     * @param action the action to run with each frame's matches
     */
    public static void whileFindAll(ImageTemplateGroup group, Consumer<Matches> action) {
        whileFindAll(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} (with a fresh frame's matches each time) as long as all templates in the
     * group stay visible on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run with each frame's matches
     */
    public static void whileFindAll(ImageTemplateGroup group, CaptureSource source, Consumer<Matches> action) {
        if (group.isEmpty()) return;   // vacuous hasAll would spin this loop forever on an empty group
        PopupGuard.check();
        Vision.Frame frame;
        while ((frame = findFrame(group, source, BotSettings.current().confidence())).matches().hasAll(group.toArray())) {
            Vision.runInFrame(frame, action);
        }
        Vision.setLastMatches(Matches.none());
    }

    /**
     * Keep running {@code action} until any template in the group appears.
     *
     * @param group  the template group to search for
     * @param action the action to run
     */
    public static void untilFindAny(ImageTemplateGroup group, Runnable action) {
        untilFindAny(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} until any template in the group appears on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run
     */
    public static void untilFindAny(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        if (group.isEmpty()) return;   // nothing can ever appear, so this would run action forever
        while (!findAny(group, source)) {
            action.run();
        }
    }

    /**
     * Keep running {@code action} until all templates in the group appear.
     *
     * @param group  the template group to search for
     * @param action the action to run
     */
    public static void untilFindAll(ImageTemplateGroup group, Runnable action) {
        untilFindAll(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} until all templates in the group appear on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run
     */
    public static void untilFindAll(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        if (group.isEmpty()) return;   // allMatch over nothing is vacuously true; keep it "matches nothing"
        while (!group.templates().stream().allMatch(t -> find(t, source))) {
            action.run();
        }
    }
}
