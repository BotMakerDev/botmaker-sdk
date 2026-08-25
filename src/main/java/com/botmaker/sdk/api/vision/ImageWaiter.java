package com.botmaker.sdk.api.vision;
import com.botmaker.sdk.api.util.Debug;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;

/**
 * Poll for a template to appear / disappear. Every method mirrors {@link ImageFinder}: a whole-desktop
 * default plus a {@link CaptureSource} form (window / monitor / desktop, optionally narrowed with
 * {@link CaptureSource#region(com.botmaker.sdk.api.geometry.Rect)}). Matches are returned in absolute coordinates.
 * <p>
 * Every method in this class also updates {@link Vision#setLastMatch(MatchResult)} for the current thread,
 * enabling access to the most recent match via {@link Vision#lastMatch()}.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}) by {@link ImageFinder}'s rule: each of the three
 * operations offers the plain and the {@code CaptureSource} form, and hides the one that also takes a bare
 * {@code double confidence} — {@link ImageTemplate#threshold()} is where that belongs. The timeout stays a
 * parameter in every offered shape: it is the question these methods exist to ask.
 */
public class ImageWaiter {

    // --- waitFor ---

    /**
     * Waits for the specified template to appear on the current capture source within the timeout period.
     * Polls every 100ms until the template is found or the timeout elapses.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait for
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found within timeout, false if the timeout elapsed
     * @see #waitFor(ImageTemplate, int, double)
     * @see #waitFor(ImageTemplate, CaptureSource, int)
     * @see #waitFor(ImageTemplate, CaptureSource, int, double)
     */
    public static boolean waitFor(ImageTemplate template, int timeoutSeconds) {
        return waitFor(template, Source.current(), timeoutSeconds, BotSettings.confidence());
    }

    /**
     * Waits for the specified template to appear on the current capture source with a custom confidence threshold.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait for
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @param confidence      the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found within timeout, false if the timeout elapsed
     */
    public static boolean waitFor(ImageTemplate template, int timeoutSeconds, double confidence) {
        return waitFor(template, Source.current(), timeoutSeconds, confidence);
    }

    /**
     * Waits for the specified template to appear on a specific capture source within the timeout period.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait for
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found within timeout, false if the timeout elapsed
     */
    public static boolean waitFor(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitFor(template, source, timeoutSeconds, BotSettings.confidence());
    }

    /**
     * Waits for the specified template to appear on a specific capture source with a custom confidence threshold.
     * This is the core implementation that polls for the template until found or timeout elapses.
     * <p>
     * Polls every 100ms. Updates {@link Vision} on each poll and on final result.
     *
     * @param template        the image template to wait for
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @param confidence      the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found within timeout, false if the timeout elapsed
     */
    public static boolean waitFor(ImageTemplate template, CaptureSource source,
                                      int timeoutSeconds, double confidence) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Per poll, not once at entry: a popup that opens *during* the wait is exactly the case that would
            // otherwise burn the whole timeout waiting for something it is covering.
            PopupGuard.check();
            MatchResult result = ImageFinder.findInternal(template, source, confidence);
            Vision.setLastMatch(result);
            if (result.isFound()) {
                Debug.log("[Vision] found " + template.id() + " after "
                        + (System.currentTimeMillis() - startTime) + "ms");
                return true;
            }
            Wait.milliseconds(100);
        }

        Debug.log("[Vision] timeout waiting for " + template.id());
        Vision.setLastMatch(MatchResult.notFound());
        return false;
    }

    // --- waitUntilGone ---

    /**
     * Waits for the specified template to disappear from the current capture source within the timeout period.
     * Polls every 100ms until the template is no longer found or the timeout elapses.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait to disappear
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template disappeared within the timeout, false if the timeout elapsed
     * @see #waitUntilGone(ImageTemplate, int, double)
     * @see #waitUntilGone(ImageTemplate, CaptureSource, int)
     * @see #waitUntilGone(ImageTemplate, CaptureSource, int, double)
     */
    public static boolean waitUntilGone(ImageTemplate template, int timeoutSeconds) {
        return waitUntilGone(template, Source.current(), timeoutSeconds, BotSettings.confidence());
    }

    /**
     * Waits for the specified template to disappear from the current capture source with a custom confidence threshold.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait to disappear
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @param confidence      the minimum confidence score (0.0 to 1.0) at which the template is considered visible
     * @return true if the template disappeared within the timeout, false if the timeout elapsed
     */
    public static boolean waitUntilGone(ImageTemplate template, int timeoutSeconds, double confidence) {
        return waitUntilGone(template, Source.current(), timeoutSeconds, confidence);
    }

    /**
     * Waits for the specified template to disappear from a specific capture source within the timeout period.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait to disappear
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template disappeared within the timeout, false if the timeout elapsed
     */
    public static boolean waitUntilGone(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitUntilGone(template, source, timeoutSeconds, BotSettings.confidence());
    }

    /**
     * Waits for the specified template to disappear from a specific capture source with a custom confidence threshold.
     * This is the core implementation that polls until the template is no longer found or timeout elapses.
     * <p>
     * Polls every 100ms. Updates {@link Vision} on each poll and on final result.
     *
     * @param template        the image template to wait to disappear
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @param confidence      the minimum confidence score (0.0 to 1.0) at which the template is considered visible
     * @return true if the template disappeared within the timeout, false if the timeout elapsed
     */
    public static boolean waitUntilGone(ImageTemplate template, CaptureSource source,
                                        int timeoutSeconds, double confidence) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            PopupGuard.check();   // per poll, as in waitFor
            MatchResult result = ImageFinder.findInternal(template, source, confidence);
            Vision.setLastMatch(result);
            if (!result.isFound()) {
                Debug.log("[Vision] " + template.id() + " disappeared after "
                        + (System.currentTimeMillis() - startTime) + "ms");
                return true;
            }
            Wait.milliseconds(100);
        }

        Debug.log("[Vision] timeout: " + template.id() + " still visible");
        // Set last match to notFound since we timed out waiting for it to disappear
        Vision.setLastMatch(MatchResult.notFound());
        return false;
    }

    // --- waitAndClick ---

    /**
     * Waits for the specified template to appear on the current capture source and clicks it.
     * Uses default confidence and waits for the specified timeout.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait for and click
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found and clicked within the timeout, false otherwise
     * @see #waitAndClick(ImageTemplate, int, double)
     * @see #waitAndClick(ImageTemplate, CaptureSource, int)
     * @see #waitAndClick(ImageTemplate, CaptureSource, int, double)
     */
    public static boolean waitAndClick(ImageTemplate template, int timeoutSeconds) {
        return waitAndClick(template, Source.current(), timeoutSeconds, BotSettings.confidence());
    }

    /**
     * Waits for the specified template to appear on the current capture source with a custom confidence threshold and clicks it.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait for and click
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @param confidence      the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found and clicked within the timeout, false otherwise
     */
    public static boolean waitAndClick(ImageTemplate template, int timeoutSeconds, double confidence) {
        return waitAndClick(template, Source.current(), timeoutSeconds, confidence);
    }

    /**
     * Waits for the specified template to appear on a specific capture source and clicks it.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait for and click
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found and clicked within the timeout, false otherwise
     */
    public static boolean waitAndClick(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitAndClick(template, source, timeoutSeconds, BotSettings.confidence());
    }

    /**
     * Waits for the specified template to appear on a specific capture source with a custom confidence threshold and clicks it.
     * This is the core implementation that combines waiting and clicking in one operation.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template        the image template to wait for and click
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @param confidence      the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found and clicked within the timeout, false otherwise
     */
    public static boolean waitAndClick(ImageTemplate template, CaptureSource source,
                                       int timeoutSeconds, double confidence) {
        if (waitFor(template, source, timeoutSeconds, confidence)) {
            MatchResult result = Vision.lastMatch();
            Point clickPoint = BotSettings.randomizeClicks() ? result.randomClickPoint() : result.center();
            Mouse.click(clickPoint);
            Wait.milliseconds(BotSettings.foundDelay());
            Debug.log("[Vision] found and clicked " + template.id());
            return true;
        }
        return false;
    }
}
