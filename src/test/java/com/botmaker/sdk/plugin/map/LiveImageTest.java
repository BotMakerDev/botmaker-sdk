package com.botmaker.sdk.plugin.map;

import com.botmaker.shared.capture.FrameGrab;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveImageTest {

    @Test
    void theNewestFramePublishedIsTheOneTakenAndOneNotTakenIsDropped() {
        LiveImage frames = new LiveImage();
        assertFalse(frames.take());
        assertNull(frames.shown());
        frames.pixels(1, 1)[0] = 0xFF000001;
        frames.publish();
        frames.pixels(1, 1)[0] = 0xFF000002;
        frames.publish();
        assertTrue(frames.take());
        assertEquals(2, frames.shown().getRGB(0, 0) & 0xFFFFFF);
        // Nothing new since: the frame shown stays.
        assertFalse(frames.take());
        assertEquals(2, frames.shown().getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void theCaptureNeverWritesTheSlotShownNorTheOneShownBefore() {
        LiveImage frames = new LiveImage();
        Set<int[]> seen = new HashSet<>();
        int[] shownArray = null;
        int[] shownBefore = null;
        for (int i = 0; i < 12; i++) {
            int[] written = frames.pixels(1, 1);
            assertNotSame(shownArray, written);
            assertNotSame(shownBefore, written);
            seen.add(written);
            written[0] = i;
            frames.publish();
            if (i % 2 == 0) {
                assertTrue(frames.take());
                shownBefore = shownArray;
                shownArray = written;
            }
        }
        assertEquals(4, seen.size());
    }

    @Test
    void aFrameOfAnotherSizeGetsAnArrayOfItsSize() {
        LiveImage frames = new LiveImage();
        assertEquals(6, frames.pixels(3, 2).length);
        assertEquals(4, frames.pixels(2, 2).length);
        assertEquals(2, frames.taken().getWidth());
    }

    @Test
    void aGrabWritesThroughTheBoardsSlots() {
        LiveImage frames = new LiveImage();
        BufferedImage frame = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(1, 1, 0xABCDEF);
        assertTrue(FrameGrab.of(() -> frame).grab(frames));
        assertEquals(0xABCDEF, frames.taken().getRGB(1, 1) & 0xFFFFFF);
    }

    @Test
    void framesCrossThreadsWhole() throws InterruptedException {
        LiveImage frames = new LiveImage();
        AtomicBoolean done = new AtomicBoolean();
        Thread capture = Thread.ofPlatform().start(() -> {
            for (int i = 1; i <= 20_000; i++) {
                int[] into = frames.pixels(64, 1);
                Arrays.fill(into, i);
                frames.publish();
            }
            done.set(true);
        });
        int last = 0;
        while (true) {
            boolean finished = done.get();
            if (!frames.take()) {
                if (finished) break;
                continue;
            }
            BufferedImage shown = frames.shown();
            int first = shown.getRGB(0, 0) & 0xFFFFFF;
            for (int x = 1; x < 64; x++) assertEquals(first, shown.getRGB(x, 0) & 0xFFFFFF, "a torn frame");
            assertTrue(first > last, "an older frame after a newer one");
            last = first;
        }
        capture.join();
    }

    @Test
    void aWindowFoundAgainIsTheSamePlaceAndAFeedOfFramesNever() {
        LiveFeed window = new LiveFeed(List.of(7L, "0,0"), () -> FrameGrab.of(() -> null));
        assertTrue(window.samePlace(new LiveFeed(List.of(7L, "0,0"), () -> FrameGrab.of(() -> null))));
        assertFalse(window.samePlace(new LiveFeed(List.of(7L, "5,0"), () -> FrameGrab.of(() -> null))));
        LiveFeed frames = LiveFeed.of(Optional::empty);
        assertFalse(frames.samePlace(LiveFeed.of(Optional::empty)));
        assertFalse(frames.samePlace(null));
    }

    @Test
    void theRateSaysWhatHoldsItDown() {
        assertEquals("· 60 fps read, 59 shown, 3 ms a read, 1 ms a draw",
                LiveBoard.rate(60, 59, 60, 180, 59, 1_000));
        assertEquals("· 8 fps read, 8 shown, 120 ms a read, 3 ms a draw: the capture is the limit",
                LiveBoard.rate(8, 8, 8, 960, 24, 1_000));
        assertEquals("· 60 fps read, 10 shown, 2 ms a read, 50 ms a draw: drawing is the limit",
                LiveBoard.rate(60, 10, 60, 120, 500, 1_000));
        assertEquals("· 0 fps read, 0 shown, 0 ms a read, 0 ms a draw", LiveBoard.rate(0, 0, 0, 0, 0, 1_000));
    }
}
