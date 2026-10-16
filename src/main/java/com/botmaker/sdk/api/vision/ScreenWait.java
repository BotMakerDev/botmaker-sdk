package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.internal.vision.FrameChange;

import java.awt.image.BufferedImage;
import java.time.Duration;

/**
 * Waits on the screen itself rather than on something in it: until it holds still (a loading screen done, an
 * animation over) or until it changes (a click took effect). Narrow the watched area with
 * {@link CaptureSource#region(com.botmaker.sdk.api.geometry.Rect)}, so a clock or an idle animation elsewhere
 * does not count.
 *
 * <p>How much change counts is fixed, not a parameter: capture noise and a blinking caret never count, a fade or
 * a new screen always does. Each polls every 100ms, checks for popups on each poll as the other waits do, and on
 * a timeout saves the frame it last saw, named in the trace.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): all four are offered, each wait on the current
 * source and on a named one.
 */
@Palette(category = "vision", categoryLabel = "Vision", icon = "🖥")
public final class ScreenWait {

    private static final long POLL_MS = 100;

    private ScreenWait() {}

    /**
     * Waits until the current source has not changed for {@code quiet}, or {@code timeout} elapses.
     *
     * @param quiet   how long it must hold still
     * @param timeout how long to wait at most
     * @return true once it held still for {@code quiet}; false on the timeout
     */
    public static boolean waitStable(Duration quiet, Duration timeout) {
        return waitStable(Source.current(), quiet, timeout);
    }

    /**
     * {@link #waitStable(Duration, Duration)} on {@code source}. A missing {@code quiet} or {@code timeout} is
     * zero; it is checked once more at the deadline, so a quiet as long as the timeout can still be met.
     */
    public static boolean waitStable(CaptureSource source, Duration quiet, Duration timeout) {
        long start = System.currentTimeMillis();
        long deadline = start + millis(timeout);
        long still = start;
        BufferedImage last = source.capture();
        while (true) {
            if (pause()) return false;
            PopupGuard.check();
            BufferedImage now = source.capture();
            if (now == null || last == null || FrameChange.changed(last, now)) still = System.currentTimeMillis();
            if (now != null) last = now;
            if (System.currentTimeMillis() - still >= millis(quiet)) return true;
            if (System.currentTimeMillis() >= deadline) break;
        }
        MissFrames.missed("screen did not hold still", source, last);
        return false;
    }

    /**
     * Waits until the current source looks different from how it looked when called, or {@code timeout} elapses.
     *
     * @param timeout how long to wait at most
     * @return true once it changed; false on the timeout
     */
    public static boolean waitChange(Duration timeout) {
        return waitChange(Source.current(), timeout);
    }

    /** {@link #waitChange(Duration)} on {@code source}; a missing {@code timeout} is zero, one look. */
    public static boolean waitChange(CaptureSource source, Duration timeout) {
        long deadline = System.currentTimeMillis() + millis(timeout);
        BufferedImage before = source.capture();
        BufferedImage now = before;
        while (true) {
            if (pause()) return false;
            PopupGuard.check();
            now = source.capture();
            if (before == null) {
                before = now;
            } else if (FrameChange.changed(before, now)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) break;
        }
        MissFrames.missed("screen did not change", source, now);
        return false;
    }

    private static long millis(Duration d) {
        return d == null ? 0 : Math.max(0, d.toMillis());
    }

    /** Sleeps one poll; true when the run is being stopped. */
    private static boolean pause() {
        try {
            Thread.sleep(POLL_MS);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
