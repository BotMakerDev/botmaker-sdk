package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * What the finder does with an <b>empty</b> {@link ImageTemplateGroup}: nothing, cheaply.
 *
 * <p>An empty group became legal so the generated {@code Popups} scaffold can ship a real
 * {@code whileFindAny} block with its templates still to be filled in. That only works if "matches
 * nothing" holds everywhere — and two families of helper get it wrong by default:
 *
 * <ul>
 *   <li>the <b>{@code …All}</b> ones, because "all of nothing is present" is <em>vacuously true</em>:
 *       {@code ifFindAll} would run its action on a blank screen and {@code whileFindAll} would loop
 *       forever doing so;</li>
 *   <li>{@code untilFindAny}, because nothing can ever appear, so it would run its action forever.</li>
 * </ul>
 *
 * <p>The second property is cost: a scaffolded bot calls its popup check before <em>every</em> vision
 * step, so an empty group that still grabbed a frame would silently double the capture work of every
 * bot that hasn't filled one in yet. The source here fails the test if it is captured at all.
 *
 * <p>Every loop is timed out — a regression here hangs rather than fails.
 */
class ImageFinderEmptyGroupTest {

    /** A source that must never be asked for a frame. */
    private static final class NeverCapturedSource implements CaptureSource {
        @Override public BufferedImage capture() {
            fail("an empty group must not take a capture");
            return null;
        }

        @Override public Point origin() {
            return new Point(0, 0);
        }

        @Override public void click(Point p) {
            fail("an empty group must not click");
        }
    }

    private static final ImageTemplateGroup EMPTY = ImageTemplateGroup.of();

    @Test
    @Timeout(10)
    void ifFindHelpersDoNotRunTheirAction() {
        CaptureSource source = new NeverCapturedSource();
        AtomicInteger runs = new AtomicInteger();

        assertFalse(ImageFinder.ifFindAny(EMPTY, source, m -> runs.incrementAndGet()));
        assertFalse(ImageFinder.ifFindAll(EMPTY, source, m -> runs.incrementAndGet()),
                "\"all of nothing\" must not be vacuously true");
        assertEquals(0, runs.get());
    }

    @Test
    @Timeout(10)
    void whileFindHelpersReturnImmediately() {
        CaptureSource source = new NeverCapturedSource();
        AtomicInteger runs = new AtomicInteger();

        ImageFinder.whileFindAny(EMPTY, source, m -> runs.incrementAndGet());
        ImageFinder.whileFindAll(EMPTY, source, m -> runs.incrementAndGet());
        assertEquals(0, runs.get(), "neither loop body may run — whileFindAll would otherwise never end");
    }

    @Test
    @Timeout(10)
    void untilFindHelpersReturnImmediately() {
        CaptureSource source = new NeverCapturedSource();
        AtomicInteger runs = new AtomicInteger();

        ImageFinder.untilFindAny(EMPTY, source, runs::incrementAndGet);
        ImageFinder.untilFindAll(EMPTY, source, runs::incrementAndGet);
        assertEquals(0, runs.get(), "nothing can ever appear, so waiting for it must not spin");
    }

    @Test
    @Timeout(10)
    void theQueriesReportNotFoundWithoutCapturing() {
        CaptureSource source = new NeverCapturedSource();

        assertFalse(ImageFinder.findAny(EMPTY, source));
        assertFalse(ImageFinder.findBest(EMPTY, source));
        assertEquals(0, ImageFinder.findAll(EMPTY, source));
    }
}
