package com.botmaker.sdk.api.vision;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;

/**
 * Poll for a template to appear / disappear. Every method mirrors {@link ImageFinder}: a whole-desktop
 * default plus a {@link CaptureSource} form (window / monitor / desktop, optionally narrowed with
 * {@link CaptureSource#region(com.botmaker.sdk.api.Rect)}). Matches are returned in absolute coordinates.
 * <p>
 * Every method in this class also updates {@link VisionContext#setLastMatch(MatchResult)} for the current thread,
 * enabling access to the most recent match via {@link VisionContext#getLastMatch()}.
 */
public class ImageWaiter {

    // --- waitFor ---

    /**
     * Waits for the specified template to appear on the current capture source within the timeout period.
     * Polls every 100ms until the template is found or the timeout elapses.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template        the image template to wait for
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found within timeout, false if the timeout elapsed
     * @see #waitFor(ImageTemplate, int, double)
     * @see #waitFor(ImageTemplate, CaptureSource, int)
     * @see #waitFor(ImageTemplate, CaptureSource, int, double)
     */
    public static boolean waitFor(ImageTemplate template, int timeoutSeconds) {
        return waitFor(template, Source.current(), timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Waits for the specified template to appear on the current capture source with a custom confidence threshold.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
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
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template        the image template to wait for
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found within timeout, false if the timeout elapsed
     */
    public static boolean waitFor(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitFor(template, source, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Waits for the specified template to appear on a specific capture source with a custom confidence threshold.
     * This is the core implementation that polls for the template until found or timeout elapses.
     * <p>
     * Polls every 100ms. Updates {@link VisionContext} on each poll and on final result.
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
            MatchResult result = ImageFinder.findInternal(template, source, confidence);
            VisionContext.setLastMatch(result);
            if (result.isFound()) {
                if (Debug.isEnabled()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println("Found " + template.getId() + " after " + elapsed + "ms");
                }
                return true;
            }
            Wait.milliseconds(100);
        }

        if (Debug.isEnabled()) {
            System.out.println("Timeout waiting for " + template.getId());
        }
        VisionContext.setLastMatch(MatchResult.notFound());
        return false;
    }

    // --- waitUntilGone ---

    /**
     * Waits for the specified template to disappear from the current capture source within the timeout period.
     * Polls every 100ms until the template is no longer found or the timeout elapses.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template        the image template to wait to disappear
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template disappeared within the timeout, false if the timeout elapsed
     * @see #waitUntilGone(ImageTemplate, int, double)
     * @see #waitUntilGone(ImageTemplate, CaptureSource, int)
     * @see #waitUntilGone(ImageTemplate, CaptureSource, int, double)
     */
    public static boolean waitUntilGone(ImageTemplate template, int timeoutSeconds) {
        return waitUntilGone(template, Source.current(), timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Waits for the specified template to disappear from the current capture source with a custom confidence threshold.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
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
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template        the image template to wait to disappear
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template disappeared within the timeout, false if the timeout elapsed
     */
    public static boolean waitUntilGone(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitUntilGone(template, source, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Waits for the specified template to disappear from a specific capture source with a custom confidence threshold.
     * This is the core implementation that polls until the template is no longer found or timeout elapses.
     * <p>
     * Polls every 100ms. Updates {@link VisionContext} on each poll and on final result.
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
            MatchResult result = ImageFinder.findInternal(template, source, confidence);
            VisionContext.setLastMatch(result);
            if (!result.isFound()) {
                if (Debug.isEnabled()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println(template.getId() + " disappeared after " + elapsed + "ms");
                }
                return true;
            }
            Wait.milliseconds(100);
        }

        if (Debug.isEnabled()) {
            System.out.println("Timeout: " + template.getId() + " still visible");
        }
        // Set last match to notFound since we timed out waiting for it to disappear
        VisionContext.setLastMatch(MatchResult.notFound());
        return false;
    }

    // --- waitAndClick ---

    /**
     * Waits for the specified template to appear on the current capture source and clicks it.
     * Uses default confidence and waits for the specified timeout.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template        the image template to wait for and click
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found and clicked within the timeout, false otherwise
     * @see #waitAndClick(ImageTemplate, int, double)
     * @see #waitAndClick(ImageTemplate, CaptureSource, int)
     * @see #waitAndClick(ImageTemplate, CaptureSource, int, double)
     */
    public static boolean waitAndClick(ImageTemplate template, int timeoutSeconds) {
        return waitAndClick(template, Source.current(), timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Waits for the specified template to appear on the current capture source with a custom confidence threshold and clicks it.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
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
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template        the image template to wait for and click
     * @param source          the capture source (window, monitor, or desktop region) to search within
     * @param timeoutSeconds  maximum time to wait, in seconds
     * @return true if the template was found and clicked within the timeout, false otherwise
     */
    public static boolean waitAndClick(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitAndClick(template, source, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Waits for the specified template to appear on a specific capture source with a custom confidence threshold and clicks it.
     * This is the core implementation that combines waiting and clicking in one operation.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
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
            MatchResult result = VisionContext.getLastMatch();
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? result.getRandomClickPoint() : result.getCenter();
            Mouse.click(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
            if (Debug.isEnabled()) {
                System.out.println("Found and clicked " + template.getId());
            }
            return true;
        }
        return false;
    }
}
