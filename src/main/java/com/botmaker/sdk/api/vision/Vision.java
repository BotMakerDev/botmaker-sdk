package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.capture.CaptureSource;

import java.util.ArrayList;
import java.util.List;

/**
 * What the last vision search saw. Global, thread-local access to the most recent {@link MatchResult} from any
 * {@link ImageFinder}, {@link ImageClicker}, or {@link ImageWaiter} call, enabling fluent coding patterns while
 * preserving rich match information.
 *
 * <p>Every find/click/wait call in the vision API automatically updates the last match result
 * for the current thread. This lets you write fluent code like:
 *
 * <pre>{@code
 * if (ImageClicker.click(button)) {
 *     MatchResult last = Vision.lastMatch();
 *     System.out.println("Clicked at " + last.center() + " with confidence " + last.confidence());
 * }
 * }</pre>
 *
 * <p>For methods that return multiple results (like {@link ImageFinder#findAll}), the list
 * of results is also available:
 * <pre>{@code
 * int count = ImageFinder.findAll(template);
 * List<MatchResult> results = Vision.lastMatchList();
 * }</pre>
 *
 * <p>The context is {@link ThreadLocal}, so it is automatically isolated per thread — safe for
 * concurrent bot execution. Use {@link #clearLastMatch()} to reset the context for the
 * current thread (e.g., at the start of a bot action).
 *
 * <p><b>Named {@code Vision} since 1.1.0</b>, previously {@code VisionContext}. "Context" was a placeholder
 * noun for a class that is really <em>what the last search saw</em>, and the call site is what a bot author
 * reads: {@code VisionContext.getLastMatch().getCenter()} against {@code Vision.lastMatch().center()}.
 */
public final class Vision {

    private static final ThreadLocal<MatchResult> lastMatch = new ThreadLocal<>();
    private static final ThreadLocal<List<MatchResult>> lastMatchList = new ThreadLocal<>();
    private static final ThreadLocal<Matches> lastMatches = new ThreadLocal<>();
    private static final ThreadLocal<ColorMatch> lastColorMatch = new ThreadLocal<>();
    private static final ThreadLocal<List<ColorMatch>> lastColorMatchList = new ThreadLocal<>();
    private static final ThreadLocal<TextMatch> lastTextMatch = new ThreadLocal<>();
    private static final ThreadLocal<List<TextMatch>> lastTextMatchList = new ThreadLocal<>();

    private Vision() {}

    /**
     * Returns the most recent match result for the current thread, or {@link MatchResult#notFound()}
     * if no vision operation has been performed yet on this thread.
     *
     * @return the last match result, never null
     */
    public static MatchResult lastMatch() {
        MatchResult result = lastMatch.get();
        return result != null ? result : MatchResult.notFound();
    }

    /**
     * Returns the most recent list of match results for the current thread, or an empty list
     * if no vision operation that returns multiple results has been performed yet.
     *
     * @return the last match result list, never null
     */
    public static List<MatchResult> lastMatchList() {
        List<MatchResult> result = lastMatchList.get();
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Returns whether the last match for the current thread was successful.
     * Equivalent to {@code lastMatch().isFound()}.
     *
     * @return true if the last vision operation found a match
     */
    public static boolean lastMatchFound() {
        return lastMatch().isFound();
    }

    /**
     * Returns the most recent group result for the current thread — every template of the last
     * {@link ImageFinder#ifFindAny}/{@link ImageFinder#whileFindAny}/{@code *All} check that was visible — or
     * {@link Matches#none()} if no such check has run on this thread.
     *
     * <p>Prefer the lambda's own parameter: {@code whileFindAny(POPUPS, found -> …)} hands you exactly this
     * value, scoped to the frame it describes. This accessor is the out-of-band escape hatch, for the same
     * reason {@link #lastMatch()} is.
     *
     * @return the last group result, never null
     */
    public static Matches lastMatches() {
        Matches result = lastMatches.get();
        return result != null ? result : Matches.none();
    }

    // --- the current frame -------------------------------------------------
    //
    // lastMatches() above answers "what did the last group check see", which outlives the callback that saw
    // it. That is fine for reading and useless for *acting*: a coordinate is only valid for the frame it was
    // measured in, so a click on a stale one lands wherever the screen has since moved. The frame below is the
    // narrower fact — "a group callback is running right now, over these matches, on this source, and these
    // are the very pixels they were measured in" — and it is what the ImageClicker.*Last verbs act on. It is
    // scoped to the callback by runInFrame's finally, so it cannot leak past the instant it describes.

    private static final ThreadLocal<Frame> frame = new ThreadLocal<>();

    /**
     * What a group callback is running over: the matches, the source they were measured on, the screenshot they
     * were measured in, and the group that was looked for.
     *
     * <p>The screenshot is retained — not just the matches — because {@link ImageClicker#clickAllLast()} wants
     * <em>every occurrence</em> of a template, and {@link Matches} only holds the best one per template. Keeping
     * the pixels lets that second question be answered by re-matching the same instant rather than by capturing
     * a new one, which is both cheaper and the only way the two answers can agree. It is never converted to an
     * OpenCV {@code Mat} unless someone asks.
     */
    record Frame(Matches matches, CaptureSource source, java.awt.image.BufferedImage pixels,
                 ImageTemplateGroup group) {}

    /**
     * Internal: runs {@code action} as the callback of a group find, with {@code current} as the current frame.
     *
     * <p>Restores whatever frame was current before rather than clearing, so a group check nested inside
     * another's callback puts the outer frame back on the way out instead of leaving the thread frameless.
     */
    static void runInFrame(Frame current, java.util.function.Consumer<Matches> action) {
        Frame previous = frame.get();
        frame.set(current);
        setLastMatches(current.matches());
        try {
            action.accept(current.matches());
        } finally {
            if (previous == null) frame.remove();
            else frame.set(previous);
        }
    }

    /**
     * Whether the calling thread is inside a group find's callback — i.e. whether the frame-scoped verbs
     * ({@link ImageClicker#clickLast()}, {@link ImageClicker#clickEachLast()},
     * {@link ImageClicker#clickAllLast()}) have something to act on.
     *
     * @return true while a {@code ifFindAny}/{@code whileFindAny}/{@code …All} callback is running
     */
    public static boolean inFrame() {
        return frame.get() != null;
    }

    /**
     * Internal: the current frame, or {@code null} outside a group callback.
     *
     * <p>Answering with null rather than throwing is deliberate, and is a change of mind: this used to be a
     * loud {@code IllegalStateException}, on the grounds that the alternative — falling back on
     * {@link #lastMatch()} — would click a coordinate measured against a frame that is no longer on screen.
     * That reasoning was right about the click and wrong about the remedy. The frame verbs still never click a
     * stale coordinate; they simply report that there was nothing to click, exactly as they already did for an
     * <em>empty</em> frame. A bot that drifts out of a callback should carry on, not die.
     */
    static Frame currentFrame() {
        return frame.get();
    }

    /**
     * Clears the last match result, match list and group result for the current thread.
     * Useful at the start of a bot action to ensure a clean state.
     */
    public static void clearLastMatch() {
        lastMatch.remove();
        lastMatchList.remove();
        lastMatches.remove();
    }

    /**
     * Invokes the {@code action} with the last match result if it exists (i.e., the last vision
     * operation found a match). Does nothing if the last match was not found.
     *
     * @param action the consumer to invoke with the match result
     * @return true if a match existed and the action was invoked, false otherwise
     */
    public static boolean ifLastMatch(java.util.function.Consumer<MatchResult> action) {
        MatchResult result = lastMatch();
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /**
     * Internal method: updates the last match result for the current thread.
     * Called by vision API methods after each operation.
     *
     * @param result the match result to store
     */
    static void setLastMatch(MatchResult result) {
        lastMatch.set(result);
        lastMatchList.remove();
    }

    /**
     * Internal method: updates the last match result list for the current thread.
     * Called by vision API methods that return multiple results.
     *
     * @param results the list of match results to store
     */
    /**
     * Internal method: updates the last group result for the current thread.
     *
     * <p>Also seeds {@link #lastMatch()} with {@code matches.best()} so the single-match accessor keeps
     * meaning something after a group check — the palette's seeded
     * {@code MatchResult match = Vision.lastMatch()} entry relies on it.
     */
    static void setLastMatches(Matches matches) {
        lastMatches.set(matches);
        lastMatch.set(matches.best());
        lastMatchList.remove();
    }

    static void setLastMatchList(List<MatchResult> results) {
        lastMatchList.set(results);
        if (!results.isEmpty()) {
            lastMatch.set(results.get(0));
        } else {
            lastMatch.set(MatchResult.notFound());
        }
    }

    // --- colour (Pixel) ---------------------------------------------------
    //
    // Colour results are tracked separately from template results: a bot commonly interleaves the two
    // (find a button by template, check its colour), and sharing one slot would let each clobber the other.

    /**
     * Returns the most recent colour match for the current thread, or {@link ColorMatch#notFound()} if no
     * {@link Pixel} operation has been performed yet on this thread.
     *
     * @return the last colour match, never null
     */
    public static ColorMatch lastColorMatch() {
        ColorMatch result = lastColorMatch.get();
        return result != null ? result : ColorMatch.notFound();
    }

    /**
     * Returns the most recent list of colour matches for the current thread (from
     * {@link Pixel#findAll}), or an empty list if none has been performed yet.
     *
     * @return the last colour match list, never null
     */
    public static List<ColorMatch> lastColorMatchList() {
        List<ColorMatch> result = lastColorMatchList.get();
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Returns whether the last colour search for the current thread found something.
     * Equivalent to {@code lastColorMatch().isFound()}.
     */
    public static boolean lastColorMatchFound() {
        return lastColorMatch().isFound();
    }

    /** Clears the last colour match and colour match list for the current thread. */
    public static void clearLastColorMatch() {
        lastColorMatch.remove();
        lastColorMatchList.remove();
    }

    /**
     * Invokes {@code action} with the last colour match if one was found. Does nothing otherwise.
     *
     * @return true if a match existed and the action was invoked
     */
    public static boolean ifLastColorMatch(java.util.function.Consumer<ColorMatch> action) {
        ColorMatch result = lastColorMatch();
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /** Internal: updates the last colour match for the current thread. */
    static void setLastColorMatch(ColorMatch result) {
        lastColorMatch.set(result);
        lastColorMatchList.remove();
    }

    /** Internal: updates the last colour match list for the current thread. */
    static void setLastColorMatchList(List<ColorMatch> results) {
        lastColorMatchList.set(results);
        lastColorMatch.set(results.isEmpty() ? ColorMatch.notFound() : results.get(0));
    }

    // --- text (Text / OCR) ------------------------------------------------
    //
    // Text results are tracked in their own slot, separate from template and colour, for the same reason:
    // a bot commonly interleaves all three (find a button by template, check its colour, read its label),
    // and sharing a slot would let each clobber the others.

    /**
     * Returns the most recent text match for the current thread, or {@link TextMatch#notFound()} if no
     * {@link Text} operation has been performed yet on this thread.
     *
     * @return the last text match, never null
     */
    public static TextMatch lastTextMatch() {
        TextMatch result = lastTextMatch.get();
        return result != null ? result : TextMatch.notFound();
    }

    /**
     * Returns the most recent list of text matches for the current thread (from {@link Text#findAll}),
     * or an empty list if none has been performed yet.
     *
     * @return the last text match list, never null
     */
    public static List<TextMatch> lastTextMatchList() {
        List<TextMatch> result = lastTextMatchList.get();
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Returns whether the last text search for the current thread found something.
     * Equivalent to {@code lastTextMatch().isFound()}.
     */
    public static boolean lastTextMatchFound() {
        return lastTextMatch().isFound();
    }

    /** Clears the last text match and text match list for the current thread. */
    public static void clearLastTextMatch() {
        lastTextMatch.remove();
        lastTextMatchList.remove();
    }

    /**
     * Invokes {@code action} with the last text match if one was found. Does nothing otherwise.
     *
     * @return true if a match existed and the action was invoked
     */
    public static boolean ifLastTextMatch(java.util.function.Consumer<TextMatch> action) {
        TextMatch result = lastTextMatch();
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /** Internal: updates the last text match for the current thread. */
    static void setLastTextMatch(TextMatch result) {
        lastTextMatch.set(result);
        lastTextMatchList.remove();
    }

    /** Internal: updates the last text match list for the current thread. */
    static void setLastTextMatchList(List<TextMatch> results) {
        lastTextMatchList.set(results);
        lastTextMatch.set(results.isEmpty() ? TextMatch.notFound() : results.get(0));
    }
}
