package com.botmaker.sdk.api.vision;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.Point;
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
        return click(template, Source.current(), ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_FOUND_DELAY);
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
        return click(template, Source.current(), confidence, ClickConfig.DEFAULT_FOUND_DELAY);
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
        return click(template, source, ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_FOUND_DELAY);
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
        return click(template, source, confidence, ClickConfig.DEFAULT_FOUND_DELAY);
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
        MatchResult result = ImageFinder.findInternal(template, source, confidence);
        VisionContext.setLastMatch(result);
        return clickResult(source, result, delayMs > 0 ? delayMs : ClickConfig.DEFAULT_FOUND_DELAY);
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
        return clickAny(Source.current(), ClickConfig.DEFAULT_CONFIDENCE, templates);
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
        return clickAny(source, ClickConfig.DEFAULT_CONFIDENCE, templates);
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
            if (click(template, source, confidence, ClickConfig.DEFAULT_FOUND_DELAY)) {
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
        return clickAny(Source.current(), ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
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
        return clickAny(source, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
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
        CaptureSource source = Source.current();
        return clickResult(source, ImageFinder.findInternal(template, source, ClickConfig.DEFAULT_CONFIDENCE));
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
        return clickResult(source, ImageFinder.findInternal(template, source, ClickConfig.DEFAULT_CONFIDENCE));
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
        CaptureSource source = Source.current();
        MatchResult result = findBestInternal(group, source, ClickConfig.DEFAULT_CONFIDENCE);
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
        MatchResult result = findBestInternal(group, source, ClickConfig.DEFAULT_CONFIDENCE);
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
        CaptureSource source = Source.current();
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
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
        CaptureSource source = Source.current();
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, margin);
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
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
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
        MatchResult result = compareInternal(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, margin);
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
        return clickAnyCompare(good, bad, Source.current(), ClickConfig.DEFAULT_COMPARE_MARGIN);
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
        return clickAnyCompare(good, bad, source, ClickConfig.DEFAULT_COMPARE_MARGIN);
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
        MatchResult result = compareAnyInternal(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, margin);
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
        return clickAllCompare(good, bad, Source.current(), ClickConfig.DEFAULT_COMPARE_MARGIN);
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
        return clickAllCompare(good, bad, source, ClickConfig.DEFAULT_COMPARE_MARGIN);
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
        List<MatchResult> winners = compareAllInternal(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, margin);
        VisionContext.setLastMatchList(winners);
        for (MatchResult match : winners) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? match.getRandomClickPoint() : match.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
        }
        if (Debug.isEnabled() && !winners.isEmpty()) {
            System.out.println("Clicked " + winners.size() + " compare-winning locations");
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
        return clickAll(template, Source.current(), ClickConfig.DEFAULT_CONFIDENCE);
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
        return clickAll(template, source, ClickConfig.DEFAULT_CONFIDENCE);
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
        List<MatchResult> matches = ImageFinder.findAllInternal(template, source, confidence);
        VisionContext.setLastMatchList(matches);
        for (MatchResult match : matches) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? match.getRandomClickPoint() : match.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
        }
        if (Debug.isEnabled() && !matches.isEmpty()) {
            System.out.println("Clicked " + matches.size() + " instances of " + template.getId());
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
        return clickAll(group, Source.current(), ClickConfig.DEFAULT_CONFIDENCE);
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
        return clickAll(group, source, ClickConfig.DEFAULT_CONFIDENCE);
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
        List<MatchResult> all = new java.util.ArrayList<>();
        for (ImageTemplate template : group.templates()) {
            all.addAll(ImageFinder.findAllInternal(template, source, confidence));
        }
        VisionContext.setLastMatchList(all);
        for (MatchResult match : all) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? match.getRandomClickPoint() : match.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
        }
        if (Debug.isEnabled() && !all.isEmpty()) {
            System.out.println("Clicked " + all.size() + " instances across the group");
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
        com.botmaker.sdk.internal.opencv.RawMatch bestRaw = null;
        MatchResult best = MatchResult.notFound();
        java.awt.image.BufferedImage screenshot = source.capture();
        if (screenshot == null) {
            return MatchResult.notFound();
        }
        org.opencv.core.Mat background = com.botmaker.sdk.internal.opencv.OpencvManager.bufferedImageToMat(screenshot);
        try {
            Point origin = source.origin();
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

            for (ImageTemplate good : goods) {
                com.botmaker.sdk.internal.opencv.RawMatch gm = com.botmaker.sdk.internal.opencv.OpencvManager.findBestMatch(
                        good.getMat(), background, false, confidence, good.captureResolution());
                if (gm == null) {
                    continue;
                }
                boolean wins = true;
                for (ImageTemplate bad : bads) {
                    double badScore = com.botmaker.sdk.internal.opencv.OpencvManager.scoreAround(
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
        org.opencv.core.Mat background = com.botmaker.sdk.internal.opencv.OpencvManager.bufferedImageToMat(screenshot);
        try {
            Point origin = source.origin();
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

            for (ImageTemplate good : goods) {
                com.botmaker.sdk.internal.opencv.RawMatch gm = com.botmaker.sdk.internal.opencv.OpencvManager.findBestMatch(
                        good.getMat(), background, false, confidence, good.captureResolution());
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
        org.opencv.core.Mat background = com.botmaker.sdk.internal.opencv.OpencvManager.bufferedImageToMat(screenshot);
        try {
            Point origin = source.origin();
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

            for (ImageTemplate good : goods) {
                List<com.botmaker.sdk.internal.opencv.RawMatch> matches =
                        com.botmaker.sdk.internal.opencv.OpencvManager.findMultipleMatches(
                                good.getMat(), background, false, confidence, good.captureResolution());
                for (com.botmaker.sdk.internal.opencv.RawMatch gm : matches) {
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
            double badScore = com.botmaker.sdk.internal.opencv.OpencvManager.scoreAround(
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
        return clickResult(source, result, ClickConfig.DEFAULT_FOUND_DELAY);
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
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? result.getRandomClickPoint() : result.getCenter();
            source.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(delayMs);

            if (Debug.isEnabled()) {
                System.out.println("Clicked " + result.getTemplateId() + " at " + clickPoint +
                        " (confidence: " + String.format("%.3f", result.getConfidence()) + ")");
            }
            return true;
        }
        Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
        if (Debug.isEnabled()) {
            System.out.println("Template not found");
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
