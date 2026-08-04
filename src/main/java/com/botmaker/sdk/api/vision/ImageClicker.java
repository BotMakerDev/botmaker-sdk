package com.botmaker.sdk.api.vision;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.BotSettings;
import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.ClickEvent;
import com.botmaker.sdk.api.observe.Surface;

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
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

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
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

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
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

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
