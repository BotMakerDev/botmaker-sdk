package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.BotSettings;
import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.ClickEvent;
import com.botmaker.sdk.api.observe.Surface;

import java.util.ArrayList;
import java.util.List;

/**
 * Locate a template and click it. Every method mirrors {@link ImageFinder}: a whole-desktop default plus a
 * {@link CaptureSource} form (window / monitor / desktop, optionally narrowed with
 * {@link CaptureSource#region(com.botmaker.sdk.api.Rect) region}), so a click can be pinned to a specific surface.
 * The template is located within the source; the click lands at the resulting absolute screen coordinate.
 * <p>
 * Every method in this class also updates {@link VisionContext} for the current thread,
 * enabling access to the most recent match via {@link VisionContext#getLastMatch()}.
 */
public class ImageClicker {

    // --- click (single template) ---

    /**
     * Clicks the specified template on the current capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template the image template to search for and click
     * @return true if the template was found and clicked, false otherwise
     * @see #click(ImageTemplate, double)
     * @see #click(ImageTemplate, CaptureSource)
     * @see #click(ImageTemplate, CaptureSource, double)
     */
    public static boolean click(ImageTemplate template) {
        return click(template, Source.current(), BotSettings.confidence(), BotSettings.foundDelay());
    }

    /**
     * Clicks the specified template on the current capture source with a custom confidence threshold.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for and click
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean click(ImageTemplate template, double confidence) {
        return click(template, Source.current(), confidence, BotSettings.foundDelay());
    }

    /**
     * Clicks the specified template on a specific capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template the image template to search for and click
     * @param source   the capture source (window, monitor, or desktop region) to search within
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean click(ImageTemplate template, CaptureSource source) {
        return click(template, source, BotSettings.confidence(), BotSettings.foundDelay());
    }

    /**
     * Clicks the specified template on a specific capture source with a custom confidence threshold.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for and click
     * @param source     the capture source (window, monitor, or desktop region) to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean click(ImageTemplate template, CaptureSource source, double confidence) {
        return click(template, source, confidence, BotSettings.foundDelay());
    }

    /**
     * Clicks the specified template on a specific capture source with a custom confidence threshold and delay.
     * This is the core implementation that locates the template, clicks it, and waits for the specified delay.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for and click
     * @param source     the capture source (window, monitor, or desktop region) to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @param delayMs    the delay in milliseconds after a successful click
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean click(ImageTemplate template, CaptureSource source, double confidence, int delayMs) {
        PopupGuard.check();
        MatchResult result = ImageFinder.findInternal(template, source, confidence);
        VisionContext.setLastMatch(result);
        return clickResult(source, result, delayMs > 0 ? delayMs : BotSettings.foundDelay());
    }

    // --- click an already-located match (no second capture) ---

    /**
     * Clicks a match you already have — typically one picked out of a {@link Matches} by the group lambda
     * helpers ({@code found.get(claimAll)}). No capture and no matching happen: the click lands at the
     * coordinate the match already carries, which is the point of branching on a frame's matches and then
     * acting on the one you chose.
     *
     * <p>Companion to {@link Matches#get(ImageTemplate)}, which returns {@link MatchResult#notFound()} rather
     * than null for an absent template — so {@code click(found.get(x))} is safe to write unguarded and simply
     * returns false when {@code x} wasn't there.
     *
     * @param result the match to click
     * @return true if the match was found and clicked, false if it was a miss
     */
    public static boolean click(MatchResult result) {
        return click(result, Source.current());
    }

    /**
     * Clicks an already-located match on a specific capture source. The source only routes the click (a window
     * session may inject rather than move the real pointer); the coordinate comes from the match.
     *
     * @param result the match to click
     * @param source the capture source to click through
     * @return true if the match was found and clicked, false if it was a miss
     */
    public static boolean click(MatchResult result, CaptureSource source) {
        if (result == null) return false;
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    // --- click what the surrounding group check already found (no second capture) ---
    //
    // The fast path for a busy loop. `whileFindAny(POPUPS, found -> ImageClicker.click(POPUP))` captures and
    // matches twice per iteration to act on a template the enclosing call has *already* located in this frame;
    // these verbs act on that frame directly, so an iteration costs one capture instead of two.
    //
    // Three questions, three verbs, and the names say which is which:
    //   clickLast()      — the best one match of the frame.
    //   clickEachLast()  — one click per template that was visible.
    //   clickAllLast()   — every occurrence of every template that was visible.
    // "Each" against "All" is the distinction a frame's Matches cannot express on its own (it holds the best
    // match per template), which is why the last one re-matches the frame's retained pixels. Every one of them
    // also takes a filtering `template…` overload, so a branch that matched three templates can act on the two
    // it cares about without looking at the screen again.
    //
    // They are frame-scoped rather than "last match"-scoped on purpose: see VisionContext.currentFrame.
    // Outside a frame they do nothing and say so in the return value — never an exception, and never a click
    // on a coordinate whose frame has scrolled away.

    /**
     * Clicks the best match of the frame the surrounding group check is running over — no capture, no matching,
     * no second look at the screen.
     *
     * <p>Meaningful inside an {@link ImageFinder#ifFindAny}/{@link ImageFinder#whileFindAny}/{@code …All}
     * callback, where the {@link Matches} snapshot describes the screen as it is right now. <b>Outside one it
     * clicks nothing and returns false</b> — it will not click the coordinate of a frame that has since
     * scrolled away. Ask {@link VisionContext#inFrame()} if you need to tell that apart from an empty frame.
     *
     * <pre>{@code
     * ImageFinder.whileFindAny(POPUPS, found -> ImageClicker.clickLast());   // dismiss popups, one capture each
     * }</pre>
     *
     * <p>"Best" is the highest-confidence match in the frame, ties going to the group's declaration order —
     * the same rule as {@link Matches#best()}. To choose a different one, click it by name:
     * {@code ImageClicker.click(found.get(mail))}, which is equally capture-free.
     *
     * @return true if the frame had a match and it was clicked, false otherwise
     */
    public static boolean clickLast() {
        VisionContext.Frame frame = VisionContext.currentFrame();
        return frame != null && clickResult(frame.source(), frame.matches().best());
    }

    /**
     * Clicks one match per template that was visible in the frame, in the group's declaration order, with the
     * usual found-delay between them. No capture and no matching happen.
     *
     * <p>This is one click per <em>template</em>, not per occurrence — a frame's {@link Matches} holds the best
     * match of each template. When a template appears several times and you want them all, that is
     * {@link #clickAllLast()}.
     *
     * @return how many matches were clicked (0 outside a frame, or if the frame was empty)
     */
    public static int clickEachLast() {
        VisionContext.Frame frame = VisionContext.currentFrame();
        return frame == null ? 0 : clickEach(frame, frame.matches().all());
    }

    /**
     * {@link #clickEachLast()} restricted to the named templates — one click per template of {@code templates}
     * that was visible in the frame, skipping the rest of the frame and anything that wasn't there.
     *
     * <pre>{@code
     * ImageFinder.whileFindAny(POPUPS, found -> ImageClicker.clickEachLast(mail, gift));   // ignore the ad
     * }</pre>
     *
     * @param templates the templates to act on; the frame's own order is kept, not this argument's
     * @return how many matches were clicked
     */
    public static int clickEachLast(ImageTemplate... templates) {
        VisionContext.Frame frame = VisionContext.currentFrame();
        if (frame == null || templates == null) return 0;
        List<MatchResult> chosen = new ArrayList<>();
        for (MatchResult match : frame.matches().all()) {
            if (namedAmong(match, templates)) chosen.add(match);
        }
        return clickEach(frame, chosen);
    }

    /**
     * Clicks <em>every occurrence</em> of every template that was visible in the frame — the whole row of
     * chests, not just the best one of each.
     *
     * <p>The extra occurrences are found by re-matching the frame's own screenshot, so this still costs no
     * capture: it is the same instant the enclosing check measured, asked a second, finer question. (That is
     * the difference from {@link #clickAll(ImageTemplate)}, which looks at the screen again.) Only templates
     * the frame already saw are re-matched — one that wasn't there is not searched for a second time.
     *
     * @return how many matches were clicked (0 outside a frame, or if the frame was empty)
     */
    public static int clickAllLast() {
        VisionContext.Frame frame = VisionContext.currentFrame();
        if (frame == null) return 0;
        return clickAllOccurrences(frame, visibleTemplates(frame, null));
    }

    /**
     * {@link #clickAllLast()} restricted to the named templates — every occurrence of each of
     * {@code templates} that was visible in the frame.
     *
     * @param templates the templates to act on
     * @return how many matches were clicked
     */
    public static int clickAllLast(ImageTemplate... templates) {
        VisionContext.Frame frame = VisionContext.currentFrame();
        if (frame == null || templates == null) return 0;
        return clickAllOccurrences(frame, visibleTemplates(frame, List.of(templates)));
    }

    /** Whether {@code match} is the match of one of {@code templates}, compared the way {@link Matches} keys. */
    private static boolean namedAmong(MatchResult match, ImageTemplate[] templates) {
        for (ImageTemplate template : templates) {
            if (template != null && template.getId().equals(match.getTemplateId())) return true;
        }
        return false;
    }

    /**
     * The templates of {@code frame}'s group that were visible, optionally narrowed to {@code wanted}. Narrowing
     * by the frame's own group rather than by the argument keeps the click order the group's, and means a
     * template that was never looked for cannot be smuggled in by the filter.
     */
    private static List<ImageTemplate> visibleTemplates(VisionContext.Frame frame, List<ImageTemplate> wanted) {
        List<ImageTemplate> visible = new ArrayList<>();
        if (frame.group() == null) return visible;
        for (ImageTemplate template : frame.group().templates()) {
            if (!frame.matches().has(template)) continue;
            if (wanted != null && wanted.stream().noneMatch(w -> w != null && w.getId().equals(template.getId()))) {
                continue;
            }
            visible.add(template);
        }
        return visible;
    }

    /** Clicks {@code matches} in order, recording them as the last match list. */
    private static int clickEach(VisionContext.Frame frame, List<MatchResult> matches) {
        VisionContext.setLastMatchList(matches);
        for (MatchResult match : matches) {
            clickResult(frame.source(), match);
        }
        if (Debug.isEnabled() && !matches.isEmpty()) {
            Debug.log("Clicked " + matches.size() + " matches of the current frame");
        }
        return matches.size();
    }

    /** Re-matches {@code templates} against the frame's own pixels and clicks every occurrence found. */
    private static int clickAllOccurrences(VisionContext.Frame frame, List<ImageTemplate> templates) {
        if (frame.pixels() == null || templates.isEmpty()) return 0;
        List<MatchResult> occurrences = new ArrayList<>();
        for (ImageTemplate template : templates) {
            occurrences.addAll(ImageFinder.findAllIn(
                    frame.pixels(), template, frame.source(), BotSettings.confidence()));
        }
        return clickEach(frame, occurrences);
    }

    // --- clickAny (first template, in order, that clears the threshold) ---

    /**
     * Clicks the first template (in order) that appears on the current capture source using the default confidence.
     * Templates are checked in the order provided, and the first one found above the threshold is clicked.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param templates the image templates to search for, in priority order
     * @return true if any template was found and clicked, false otherwise
     */
    public static boolean clickAny(ImageTemplate... templates) {
        return clickAny(Source.current(), BotSettings.confidence(), templates);
    }

    /**
     * Clicks the first template (in order) that appears on the current capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @param templates the image templates to search for, in priority order
     * @return true if any template was found and clicked, false otherwise
     */
    public static boolean clickAny(double confidence, ImageTemplate... templates) {
        return clickAny(Source.current(), confidence, templates);
    }

    /**
     * Clicks the first template (in order) that appears on a specific capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param source    the capture source to search within
     * @param templates the image templates to search for, in priority order
     * @return true if any template was found and clicked, false otherwise
     */
    public static boolean clickAny(CaptureSource source, ImageTemplate... templates) {
        return clickAny(source, BotSettings.confidence(), templates);
    }

    /**
     * Clicks the first template (in order) that appears on a specific capture source with a custom confidence.
     * This is the core implementation that iterates through templates and clicks the first match found.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @param templates  the image templates to search for, in priority order
     * @return true if any template was found and clicked, false otherwise
     */
    public static boolean clickAny(CaptureSource source, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            if (click(template, source, confidence, BotSettings.foundDelay())) {
                return true;
            }
        }
        return false;
    }

    // --- clickAny over an ImageTemplateGroup ---

    /**
     * Clicks the first template in the group that appears on the current capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group the template group to search for
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickAny(ImageTemplateGroup group) {
        return clickAny(Source.current(), BotSettings.confidence(), group.toArray());
    }

    /**
     * Clicks the first template in the group that appears on the current capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickAny(ImageTemplateGroup group, double confidence) {
        return clickAny(Source.current(), confidence, group.toArray());
    }

    /**
     * Clicks the first template in the group that appears on a specific capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickAny(ImageTemplateGroup group, CaptureSource source) {
        return clickAny(source, BotSettings.confidence(), group.toArray());
    }

    /**
     * Clicks the first template in the group that appears on a specific capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickAny(ImageTemplateGroup group, CaptureSource source, double confidence) {
        return clickAny(source, confidence, group.toArray());
    }

    // --- clickBest (highest-scoring match) ---

    /**
     * Clicks the highest-scoring match for the template on the current capture source.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template the image template to search for
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplate template) {
        PopupGuard.check();
        CaptureSource source = Source.current();
        return clickResult(source, ImageFinder.findInternal(template, source, BotSettings.confidence()));
    }

    /**
     * Clicks the highest-scoring match for the template on the current capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplate template, double confidence) {
        PopupGuard.check();
        CaptureSource source = Source.current();
        return clickResult(source, ImageFinder.findInternal(template, source, confidence));
    }

    /**
     * Clicks the highest-scoring match for the template on a specific capture source.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplate template, CaptureSource source) {
        PopupGuard.check();
        return clickResult(source, ImageFinder.findInternal(template, source, BotSettings.confidence()));
    }

    /**
     * Clicks the highest-scoring match for the template on a specific capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplate template, CaptureSource source, double confidence) {
        PopupGuard.check();
        return clickResult(source, ImageFinder.findInternal(template, source, confidence));
    }

    // --- clickBest over an ImageTemplateGroup ---

    /**
     * Clicks the highest-scoring match for any template in the group on the current capture source.
     * Unlike {@link #clickAny(ImageTemplateGroup)}, this evaluates every template and clicks the best match.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group the template group to search for
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplateGroup group) {
        PopupGuard.check();
        CaptureSource source = Source.current();
        MatchResult result = findBestInternal(group, source, BotSettings.confidence());
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    /**
     * Clicks the highest-scoring match for any template in the group on the current capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplateGroup group, double confidence) {
        PopupGuard.check();
        CaptureSource source = Source.current();
        MatchResult result = findBestInternal(group, source, confidence);
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    /**
     * Clicks the highest-scoring match for any template in the group on a specific capture source.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplateGroup group, CaptureSource source) {
        PopupGuard.check();
        MatchResult result = findBestInternal(group, source, BotSettings.confidence());
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    /**
     * Clicks the highest-scoring match for any template in the group on a specific capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found and clicked, false otherwise
     */
    public static boolean clickBest(ImageTemplateGroup group, CaptureSource source, double confidence) {
        PopupGuard.check();
        MatchResult result = findBestInternal(group, source, confidence);
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    // --- clickCompare over ImageTemplateGroup ---

    /**
     * Among the {@code good} templates, clicks the best-scoring match that still beats every
     * {@code bad} template at its location by the default margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good the group of good templates to search for
     * @param bad  the group of bad templates that must NOT out-score the good templates
     * @return true if a good template was found, beats all bad templates, and was clicked, false otherwise
     */
    public static boolean clickCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        PopupGuard.check();
        CaptureSource source = Source.current();
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                BotSettings.confidence(), BotSettings.compareMargin());
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    /**
     * Among the {@code good} templates, clicks the best-scoring match that still beats every
     * {@code bad} template at its location by the specified margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good  the group of good templates to search for
     * @param bad   the group of bad templates that must NOT out-score the good templates
     * @param margin the minimum score difference required for a match
     * @return true if a good template was found, beats all bad templates, and was clicked, false otherwise
     */
    public static boolean clickCompare(ImageTemplateGroup good, ImageTemplateGroup bad, double margin) {
        PopupGuard.check();
        CaptureSource source = Source.current();
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                BotSettings.confidence(), margin);
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    /**
     * Among the {@code good} templates, clicks the best-scoring match that still beats every
     * {@code bad} template at its location by the default margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @return true if a good template was found, beats all bad templates, and was clicked, false otherwise
     */
    public static boolean clickCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        PopupGuard.check();
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                BotSettings.confidence(), BotSettings.compareMargin());
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    /**
     * Among the {@code good} templates, clicks the best-scoring match that still beats every
     * {@code bad} template at its location by the specified margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @param margin the minimum score difference required for a match
     * @return true if a good template was found, beats all bad templates, and was clicked, false otherwise
     */
    public static boolean clickCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source,
                                          double margin) {
        PopupGuard.check();
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                BotSettings.confidence(), margin);
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    // --- clickAnyCompare (click the FIRST good, in group order, that beats the bad set) ---

    /**
     * Walks the {@code good} templates <em>in group order</em> and clicks the first one whose best match
     * beats every {@code bad} template at that location by the default margin. Unlike
     * {@link #clickCompare(ImageTemplateGroup, ImageTemplateGroup)} (which picks the single highest-scoring
     * winner), this respects group order and stops at the first winner — use it when the group is an ordered
     * preference list.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good the ordered group of good templates to search for
     * @param bad  the group of bad templates that must NOT out-score the good templates
     * @return true if a good template was found, beat all bad templates, and was clicked, false otherwise
     */
    public static boolean clickAnyCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return clickAnyCompare(good, bad, Source.current(), BotSettings.compareMargin());
    }

    /**
     * As {@link #clickAnyCompare(ImageTemplateGroup, ImageTemplateGroup)} but on a specific capture source.
     *
     * @param good   the ordered group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @return true if a good template was found, beat all bad templates, and was clicked, false otherwise
     */
    public static boolean clickAnyCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        return clickAnyCompare(good, bad, source, BotSettings.compareMargin());
    }

    /**
     * As {@link #clickAnyCompare(ImageTemplateGroup, ImageTemplateGroup)} but on a specific capture source and
     * with a custom compare margin.
     *
     * @param good   the ordered group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @param margin the minimum score difference the good must beat the bad by
     * @return true if a good template was found, beat all bad templates, and was clicked, false otherwise
     */
    public static boolean clickAnyCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source,
                                          double margin) {
        PopupGuard.check();
        MatchResult result = compareAnyInternal(good.templates(), bad.templates(), source,
                BotSettings.confidence(), margin);
        VisionContext.setLastMatch(result);
        return clickResult(source, result);
    }

    // --- clickAllCompare (click EVERY good location that beats the bad set) ---

    /**
     * Clicks every location of every {@code good} template that beats all {@code bad} templates at that
     * location by the default margin. The good-vs-bad analogue of {@link #clickAll(ImageTemplateGroup)}.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param good the group of good templates to search for
     * @param bad  the group of bad templates that must NOT out-score the good templates
     * @return the number of winning locations clicked
     */
    public static int clickAllCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return clickAllCompare(good, bad, Source.current(), BotSettings.compareMargin());
    }

    /**
     * As {@link #clickAllCompare(ImageTemplateGroup, ImageTemplateGroup)} but on a specific capture source.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @return the number of winning locations clicked
     */
    public static int clickAllCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        return clickAllCompare(good, bad, source, BotSettings.compareMargin());
    }

    /**
     * As {@link #clickAllCompare(ImageTemplateGroup, ImageTemplateGroup)} but on a specific capture source and
     * with a custom compare margin.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @param margin the minimum score difference the good must beat the bad by
     * @return the number of winning locations clicked
     */
    public static int clickAllCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source,
                                      double margin) {
        PopupGuard.check();
        List<MatchResult> winners = compareAllInternal(good.templates(), bad.templates(), source,
                BotSettings.confidence(), margin);
        VisionContext.setLastMatchList(winners);
        for (MatchResult match : winners) {
            Point clickPoint = BotSettings.randomizeClicks() ? match.getRandomClickPoint() : match.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(BotSettings.foundDelay());
        }
        if (Debug.isEnabled() && !winners.isEmpty()) {
            Debug.log("Clicked " + winners.size() + " compare-winning locations");
        }
        return winners.size();
    }

    // --- clickAll (every location above the threshold) ---

    /**
     * Clicks all occurrences of the template on the current capture source using the default confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param template the image template to search for and click
     * @return the number of instances clicked
     */
    public static int clickAll(ImageTemplate template) {
        return clickAll(template, Source.current(), BotSettings.confidence());
    }

    /**
     * Clicks all occurrences of the template on the current capture source with a custom confidence threshold.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param template   the image template to search for and click
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the number of instances clicked
     */
    public static int clickAll(ImageTemplate template, double confidence) {
        return clickAll(template, Source.current(), confidence);
    }

    /**
     * Clicks all occurrences of the template on a specific capture source using the default confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param template the image template to search for and click
     * @param source   the capture source to search within
     * @return the number of instances clicked
     */
    public static int clickAll(ImageTemplate template, CaptureSource source) {
        return clickAll(template, source, BotSettings.confidence());
    }

    /**
     * Clicks all occurrences of the template on a specific capture source with a custom confidence threshold.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param template   the image template to search for and click
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the number of instances clicked
     */
    public static int clickAll(ImageTemplate template, CaptureSource source, double confidence) {
        PopupGuard.check();
        List<MatchResult> matches = ImageFinder.findAllInternal(template, source, confidence);
        VisionContext.setLastMatchList(matches);
        for (MatchResult match : matches) {
            Point clickPoint = BotSettings.randomizeClicks() ? match.getRandomClickPoint() : match.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(BotSettings.foundDelay());
        }
        if (Debug.isEnabled() && !matches.isEmpty()) {
            Debug.log("Clicked " + matches.size() + " instances of " + template.getId());
        }
        return matches.size();
    }

    // --- clickAll over an ImageTemplateGroup ---

    /**
     * Clicks all occurrences of every template in the group on the current capture source.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group the template group to search for and click
     * @return the total number of instances clicked across all templates in the group
     */
    public static int clickAll(ImageTemplateGroup group) {
        return clickAll(group, Source.current(), BotSettings.confidence());
    }

    /**
     * Clicks all occurrences of every template in the group on the current capture source with a custom confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group      the template group to search for and click
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the total number of instances clicked across all templates in the group
     */
    public static int clickAll(ImageTemplateGroup group, double confidence) {
        return clickAll(group, Source.current(), confidence);
    }

    /**
     * Clicks all occurrences of every template in the group on a specific capture source.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group  the template group to search for and click
     * @param source the capture source to search within
     * @return the total number of instances clicked across all templates in the group
     */
    public static int clickAll(ImageTemplateGroup group, CaptureSource source) {
        return clickAll(group, source, BotSettings.confidence());
    }

    /**
     * Clicks all occurrences of every template in the group on a specific capture source with a custom confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group      the template group to search for and click
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the total number of instances clicked across all templates in the group
     */
    public static int clickAll(ImageTemplateGroup group, CaptureSource source, double confidence) {
        PopupGuard.check();
        List<MatchResult> all = new java.util.ArrayList<>();
        for (ImageTemplate template : group.templates()) {
            all.addAll(ImageFinder.findAllInternal(template, source, confidence));
        }
        VisionContext.setLastMatchList(all);
        for (MatchResult match : all) {
            Point clickPoint = BotSettings.randomizeClicks() ? match.getRandomClickPoint() : match.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(BotSettings.foundDelay());
        }
        if (Debug.isEnabled() && !all.isEmpty()) {
            Debug.log("Clicked " + all.size() + " instances across the group");
        }
        return all.size();
    }

    // --- Internal helper methods ---

    /**
     * Internal method for clickBest that returns MatchResult for a group.
     */
    private static MatchResult findBestInternal(ImageTemplateGroup group, CaptureSource source, double confidence) {
        MatchResult best = MatchResult.notFound();
        for (ImageTemplate template : group.templates()) {
            MatchResult result = ImageFinder.findInternal(template, source, confidence);
            if (result.isFound() && (!best.isFound() || result.getConfidence() > best.getConfidence())) {
                best = result;
            }
        }
        return best;
    }

    /**
     * Internal compare method that returns MatchResult.
     */
    private static MatchResult compareInternal(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                               CaptureSource source, double confidence, double margin) {
        // Import the compare method from ImageFinder
        // We'll duplicate the logic here for now to avoid circular dependencies
        com.botmaker.shared.opencv.RawMatch bestRaw = null;
        MatchResult best = MatchResult.notFound();
        java.awt.image.BufferedImage screenshot = source.capture();
        if (screenshot == null) {
            return MatchResult.notFound();
        }
        org.opencv.core.Mat background = com.botmaker.shared.opencv.OpencvManager.bufferedImageToMat(screenshot);
        try {
            Point origin = source.origin();
            int offsetX = origin.x();
            int offsetY = origin.y();

            for (ImageTemplate good : goods) {
                com.botmaker.shared.opencv.RawMatch gm = com.botmaker.shared.opencv.OpencvManager.findBestMatch(
                        good.getMat(), background, false, confidence, good.authoredSize());
                if (gm == null) {
                    continue;
                }
                boolean wins = true;
                for (ImageTemplate bad : bads) {
                    double badScore = com.botmaker.shared.opencv.OpencvManager.scoreAround(
                            bad.getMat(), background, false, gm.x(), gm.y(), 4);
                    if (badScore >= gm.score() - margin) {
                        wins = false;
                        break;
                    }
                }
                if (wins && (!best.isFound() || gm.score() > best.getConfidence())) {
                    best = new MatchResult(
                            new Point(gm.x() + offsetX, gm.y() + offsetY),
                            gm.width(), gm.height(), gm.score(), good.getId());
                }
            }
            return best;
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    /**
     * Compare variant that returns the FIRST good (in list order) whose best match beats every bad template
     * at its location by {@code margin}. Powers {@link #clickAnyCompare}.
     */
    private static MatchResult compareAnyInternal(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                                  CaptureSource source, double confidence, double margin) {
        java.awt.image.BufferedImage screenshot = source.capture();
        if (screenshot == null) {
            return MatchResult.notFound();
        }
        org.opencv.core.Mat background = com.botmaker.shared.opencv.OpencvManager.bufferedImageToMat(screenshot);
        try {
            Point origin = source.origin();
            int offsetX = origin.x();
            int offsetY = origin.y();

            for (ImageTemplate good : goods) {
                com.botmaker.shared.opencv.RawMatch gm = com.botmaker.shared.opencv.OpencvManager.findBestMatch(
                        good.getMat(), background, false, confidence, good.authoredSize());
                if (gm == null) {
                    continue;
                }
                if (beatsAll(gm.x(), gm.y(), gm.score(), background, bads, margin)) {
                    return new MatchResult(
                            new Point(gm.x() + offsetX, gm.y() + offsetY),
                            gm.width(), gm.height(), gm.score(), good.getId());
                }
            }
            return MatchResult.notFound();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    /**
     * Compare variant that returns EVERY location of every good template that beats all bad templates by
     * {@code margin}. Powers {@link #clickAllCompare}.
     */
    private static List<MatchResult> compareAllInternal(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                                        CaptureSource source, double confidence, double margin) {
        List<MatchResult> winners = new java.util.ArrayList<>();
        java.awt.image.BufferedImage screenshot = source.capture();
        if (screenshot == null) {
            return winners;
        }
        org.opencv.core.Mat background = com.botmaker.shared.opencv.OpencvManager.bufferedImageToMat(screenshot);
        try {
            Point origin = source.origin();
            int offsetX = origin.x();
            int offsetY = origin.y();

            for (ImageTemplate good : goods) {
                List<com.botmaker.shared.opencv.RawMatch> matches =
                        com.botmaker.shared.opencv.OpencvManager.findMultipleMatches(
                                good.getMat(), background, false, confidence, good.authoredSize());
                for (com.botmaker.shared.opencv.RawMatch gm : matches) {
                    if (beatsAll(gm.x(), gm.y(), gm.score(), background, bads, margin)) {
                        winners.add(new MatchResult(
                                new Point(gm.x() + offsetX, gm.y() + offsetY),
                                gm.width(), gm.height(), gm.score(), good.getId()));
                    }
                }
            }
            return winners;
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    /**
     * True when no {@code bad} template scores within {@code margin} of {@code goodScore} in a small
     * neighbourhood of ({@code x},{@code y}) — i.e. the good match at that location wins.
     */
    private static boolean beatsAll(int x, int y, double goodScore, org.opencv.core.Mat background,
                                    List<ImageTemplate> bads, double margin) {
        for (ImageTemplate bad : bads) {
            double badScore = com.botmaker.shared.opencv.OpencvManager.scoreAround(
                    bad.getMat(), background, false, x, y, 4);
            if (badScore >= goodScore - margin) {
                return false;
            }
        }
        return true;
    }

    /**
     * Click a match already located (used by clickBest/clickCompare). The click is dispatched through
     * {@code source} so an emulator source taps via ADB instead of the desktop.
     *
     * @param source the source the match was located on
     * @param result the match result to click
     * @return true if the click was successful, false otherwise
     */
    private static boolean clickResult(CaptureSource source, MatchResult result) {
        return clickResult(source, result, BotSettings.foundDelay());
    }

    /**
     * Click a located match, waiting {@code delayMs} afterwards (the shared click body). The click is
     * dispatched through {@code source} (see {@link CaptureSource#click(Point)}).
     *
     * @param source  the source the match was located on
     * @param result  the match result to click
     * @param delayMs the delay in milliseconds after the click
     * @return true if the click was successful, false otherwise
     */
    private static boolean clickResult(CaptureSource source, MatchResult result, int delayMs) {
        if (result.isFound()) {
            Point clickPoint = BotSettings.randomizeClicks() ? result.getRandomClickPoint() : result.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(delayMs);

            if (Debug.isEnabled()) {
                Debug.log("Clicked " + result.getTemplateId() + " at " + clickPoint +
                        " (confidence: " + String.format("%.3f", result.getConfidence()) + ")");
            }
            return true;
        }
        Wait.milliseconds(BotSettings.notFoundDelay());
        if (Debug.isEnabled()) {
            Debug.log("Template not found");
        }
        return false;
    }

    /**
     * Reports a left click to registered {@link com.botmaker.sdk.api.observe.BotObserver}s.
     * Guarded by {@code hasObservers()} so a normal bot run pays nothing.
     *
     * @param clickPoint the point where the click occurred
     */
    private static void emitClick(Point clickPoint) {
        if (Bots.hasObservers()) {
            Bots.fireClick(new ClickEvent(Surface.ofScreen(), clickPoint, ClickEvent.LEFT));
        }
    }
}
