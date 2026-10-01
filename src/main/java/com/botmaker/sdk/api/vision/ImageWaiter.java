package com.botmaker.sdk.api.vision;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.plugin.api.record.Gesture;
import com.botmaker.plugin.api.record.Records;
import com.botmaker.sdk.api.console.Debug;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.input.Mouse;
import com.botmaker.sdk.api.time.Wait;

import java.time.Duration;

/**
 * Poll for a template to appear / disappear. Every method mirrors {@link ImageFinder}: a whole-desktop
 * default plus a {@link CaptureSource} form (window / monitor / desktop, optionally narrowed with
 * {@link CaptureSource#region(com.botmaker.sdk.api.geometry.Rect)}). Matches are returned in absolute coordinates.
 * <p>
 * Every method in this class also updates {@link Vision#setLastMatch(MatchResult)} for the current thread,
 * enabling access to the most recent match via {@link Vision#lastMatch()}.
 *
 * <p><b>Two shapes per operation</b>, as in {@link ImageFinder}, and the timeout is a {@link Duration} in both:
 * it is the question these methods exist to ask. The whole-seconds {@code int} spellings and the per-call
 * {@code double confidence} ones were deleted on 2026-10-01; the confidence is {@link BotSettings}'.
 */
@Palette(category = "vision", categoryLabel = "Vision", icon = "⏳")
public class ImageWaiter {

    // --- waitFor ---

    /**
     * Waits up to {@code timeout} for {@code template} to appear on the current capture source, polling every
     * 100ms.
     * <p>
     * The match result is stored in {@link Vision} and can be retrieved with
     * {@link Vision#lastMatch()}.
     *
     * @param template the image template to wait for
     * @param timeout  how long to wait
     * @return true if the template was found within timeout, false if the timeout elapsed
     */
    @Records(Gesture.AWAIT)
    public static boolean waitFor(ImageTemplate template, Duration timeout) {
        return waitFor(template, Source.current(), timeout);
    }

    /** {@link #waitFor(ImageTemplate, Duration)} on {@code source}. */
    public static boolean waitFor(ImageTemplate template, CaptureSource source, Duration timeout) {
        return awaitFor(template, source, timeout.toMillis(), ImageFinder.confidence());
    }

    private static boolean awaitFor(ImageTemplate template, CaptureSource source, long timeoutMs,
                                    double confidence) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Per poll, not once at entry: a popup that opens *during* the wait is exactly the case that would
            // otherwise burn the whole timeout waiting for something it is covering.
            PopupGuard.check();
            MatchResult result = ImageFinder.findInternal(template, source, confidence);
            Vision.setLastMatch(result);
            if (result.isFound()) {
                Debug.log("found " + template.id() + " after "
                        + (System.currentTimeMillis() - startTime) + "ms");
                return true;
            }
            Wait.milliseconds(100);
        }

        Debug.log("timeout waiting for " + template.id());
        Vision.setLastMatch(MatchResult.notFound());
        return false;
    }

    // --- waitUntilGone ---

    /**
     * Waits up to {@code timeout} for {@code template} to leave the current capture source, polling every
     * 100ms.
     *
     * @param template the image template to wait to disappear
     * @param timeout  how long to wait
     * @return true if the template disappeared within the timeout, false if the timeout elapsed
     */
    public static boolean waitUntilGone(ImageTemplate template, Duration timeout) {
        return waitUntilGone(template, Source.current(), timeout);
    }

    /** {@link #waitUntilGone(ImageTemplate, Duration)} on {@code source}. */
    public static boolean waitUntilGone(ImageTemplate template, CaptureSource source, Duration timeout) {
        return awaitGone(template, source, timeout.toMillis(), ImageFinder.confidence());
    }

    private static boolean awaitGone(ImageTemplate template, CaptureSource source, long timeoutMs,
                                     double confidence) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            PopupGuard.check();   // per poll, as in waitFor
            MatchResult result = ImageFinder.findInternal(template, source, confidence);
            Vision.setLastMatch(result);
            if (!result.isFound()) {
                Debug.log("" + template.id() + " disappeared after "
                        + (System.currentTimeMillis() - startTime) + "ms");
                return true;
            }
            Wait.milliseconds(100);
        }

        Debug.log("timeout: " + template.id() + " still visible");
        // Set last match to notFound since we timed out waiting for it to disappear
        Vision.setLastMatch(MatchResult.notFound());
        return false;
    }

    // --- waitAndClick ---

    /**
     * Waits up to {@code timeout} for {@code template} on the current capture source, then clicks it.
     *
     * @param template the image template to wait for and click
     * @param timeout  how long to wait
     * @return true if the template was found and clicked within the timeout, false otherwise
     */
    public static boolean waitAndClick(ImageTemplate template, Duration timeout) {
        return waitAndClick(template, Source.current(), timeout);
    }

    /** {@link #waitAndClick(ImageTemplate, Duration)} on {@code source}. */
    public static boolean waitAndClick(ImageTemplate template, CaptureSource source, Duration timeout) {
        return awaitAndClick(template, source, timeout.toMillis(), ImageFinder.confidence());
    }

    private static boolean awaitAndClick(ImageTemplate template, CaptureSource source, long timeoutMs,
                                         double confidence) {
        if (awaitFor(template, source, timeoutMs, confidence)) {
            MatchResult result = Vision.lastMatch();
            Point clickPoint = BotSettings.current().randomizeClicks() ? result.randomClickPoint() : result.center();
            Mouse.click(clickPoint);
            Wait.milliseconds(BotSettings.current().foundDelay());
            Debug.log("found and clicked " + template.id());
            return true;
        }
        return false;
    }
}
