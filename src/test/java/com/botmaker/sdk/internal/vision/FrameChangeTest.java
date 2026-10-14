package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.vision.FrameChange.Clock;
import com.botmaker.sdk.internal.vision.FrameChange.Motion;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as the screen changing: a new screen or a fade does, noise and a caret do not; what moves on its own
 * is not an input's answer; an answer is waited for until it holds still.
 */
class FrameChangeTest {

    private static BufferedImage filled(Color color, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static BufferedImage noise(int width, int height, long seed) {
        BufferedImage bg = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        return bg;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return copy;
    }

    /** A clock that only counts: {@code waited[0]} is how long it was asked to sleep. */
    private static Clock counting(long[] waited) {
        return new Clock() {
            @Override
            public long nowMs() {
                return waited[0];
            }

            @Override
            public void sleep(long ms) {
                waited[0] += ms;
            }
        };
    }

    @Test
    void theSameFrameHasNotChanged() {
        assertFalse(FrameChange.changed(filled(Color.GRAY, 640, 360), filled(Color.GRAY, 640, 360)));
    }

    @Test
    void aCaretBlinkingIsNotAChange() {
        BufferedImage after = filled(Color.GRAY, 640, 360);
        Graphics2D g = after.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(300, 170, 2, 16);
        g.dispose();
        assertFalse(FrameChange.changed(filled(Color.GRAY, 640, 360), after));
    }

    @Test
    void slightNoiseIsNotAChange() {
        assertFalse(FrameChange.changed(filled(new Color(128, 128, 128), 640, 360),
                filled(new Color(136, 136, 136), 640, 360)));
    }

    @Test
    void aNewScreenOrAPanelIsAChange() {
        assertTrue(FrameChange.changed(filled(Color.GRAY, 640, 360), filled(Color.BLACK, 640, 360)));
        BufferedImage panel = filled(Color.GRAY, 640, 360);
        Graphics2D g = panel.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(100, 100, 120, 60);
        g.dispose();
        assertTrue(FrameChange.changed(filled(Color.GRAY, 640, 360), panel));
    }

    /** Brightness alone missed it: this red and this green are equally bright. */
    @Test
    void aColourChangeOfTheSameBrightnessIsAChange() {
        assertTrue(FrameChange.changed(filled(new Color(200, 60, 60), 640, 360),
                filled(new Color(60, 133, 60), 640, 360)));
    }

    @Test
    void anotherSizeIsAChangeAndAMissingFrameIsNot() {
        assertTrue(FrameChange.changed(filled(Color.GRAY, 640, 360), filled(Color.GRAY, 320, 180)));
        assertFalse(FrameChange.changed(null, filled(Color.GRAY, 640, 360)));
    }

    @Test
    void aLargeFrameIsComparedOnACoarserGrid() {
        assertEquals(FrameChange.STEP, FrameChange.step(1920, 1080));
        assertEquals(4, FrameChange.step(3840, 2160));
    }

    @Test
    void theChangedBoxHoldsEveryMovedPixelAndNothingElse() {
        BufferedImage before = noise(200, 100, 5);
        BufferedImage after = copy(before);
        assertEquals(Optional.empty(), FrameChange.box(before, after, Motion.NONE));
        after.setRGB(40, 30, after.getRGB(40, 30) ^ 0x808080);
        after.setRGB(120, 60, after.getRGB(120, 60) ^ 0x808080);
        assertEquals(Optional.of(new Rect(40, 30, 82, 32)), FrameChange.box(before, after, Motion.NONE));
        assertEquals(Optional.of(new Rect(0, 0, 50, 50)), FrameChange.box(before, noise(50, 50, 5), Motion.NONE),
                "another size is all changed");
    }

    /**
     * A game that answers late is waited for: Firestone's Library tab switched after three seconds, and a fixed
     * 800 ms wait said "nothing changed" (2026-10-13).
     */
    @Test
    void anInputIsAnsweredWhenTheScreenChangedAndHeldStill() throws InterruptedException {
        BufferedImage before = noise(40, 20, 1);
        BufferedImage opening = noise(40, 20, 2);
        BufferedImage open = noise(40, 20, 3);
        long[] waited = {0};
        Clock clock = counting(waited);
        double share = FrameChange.ANSWER_SHARE;
        assertTrue(FrameChange.changed(before, opening) && FrameChange.changed(opening, open),
                "the frames differ, or this test proves nothing");

        Iterator<BufferedImage> late = List.of(before, before, before, opening, open, open, open).iterator();
        Motion none = Motion.NONE;
        assertSame(open, FrameChange.answered(before, none, () -> Optional.of(late.next()), 5_000, share, clock)
                .orElseThrow(), "the frame once it holds still, not the first that differs");
        assertEquals(5 * FrameChange.POLL_MS, waited[0]);

        waited[0] = 0;
        assertSame(before, FrameChange.answered(before, none, () -> Optional.of(before), 1_000, share, clock)
                .orElseThrow());
        assertEquals(1_000, waited[0], "nothing changed: read until the cap");

        waited[0] = 0;
        int[] frame = {0};
        FrameChange.answered(before, none, () -> Optional.of(noise(40, 20, 10 + frame[0]++)), 10_000, share, clock);
        assertTrue(waited[0] >= FrameChange.SETTLING_MS && waited[0] < FrameChange.SETTLING_MS + FrameChange.POLL_MS,
                "a screen that never holds still is given SETTLING_MS, not the cap: " + waited[0]);

        waited[0] = 0;
        assertSame(before, FrameChange.answered(before, none, () -> Optional.of(before), 0, share, clock)
                .orElseThrow());
        assertEquals(0, waited[0], "0 reads it at once");

        waited[0] = 0;
        assertEquals(Optional.empty(), FrameChange.answered(before, none, Optional::empty, 10_000, share, clock));
        assertTrue(waited[0] < FrameChange.SETTLING_MS + FrameChange.POLL_MS,
                "a capture that is gone is not waited for");

        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class,
                () -> FrameChange.answered(before, none, () -> Optional.of(before), 0, share, clock));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    /**
     * A screen that moves on its own is not the game's answer: Firestone's home sways, and a click on the Tavern
     * was answered with the home screen before the Tavern drew (2026-10-13).
     */
    @Test
    void motionBeforeTheInputIsNotItsAnswer() throws InterruptedException {
        BufferedImage still = noise(60, 40, 1);
        BufferedImage swayed = copy(still);
        BufferedImage swayedBack = copy(still);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                swayed.setRGB(x, y, swayed.getRGB(x, y) ^ 0x808080);
                swayedBack.setRGB(x + 2, y, swayedBack.getRGB(x + 2, y) ^ 0x808080);
            }
        }
        Motion motion = Motion.of(List.of(still, swayed));
        assertTrue(FrameChange.box(still, swayedBack, Motion.NONE).isPresent(), "unmasked, the sway is a change");
        assertEquals(Optional.empty(), FrameChange.box(still, swayedBack, motion), "masked, a sway a little "
                + "further is not");
        BufferedImage opened = copy(swayedBack);
        opened.setRGB(40, 30, opened.getRGB(40, 30) ^ 0x808080);
        assertEquals(Optional.of(new Rect(40, 30, 2, 2)), FrameChange.box(still, opened, motion));

        long[] waited = {0};
        Clock clock = counting(waited);
        double share = FrameChange.ANSWER_SHARE;
        BufferedImage popup = copy(swayedBack);
        for (int y = 20; y < 40; y++) {
            for (int x = 30; x < 50; x++) popup.setRGB(x, y, popup.getRGB(x, y) ^ 0x808080);
        }
        assertEquals(100.0 / (600 - 36), FrameChange.share(still, popup, motion), 1e-9,
                "a share of the still samples only: the 36 that sway can never count");
        Iterator<BufferedImage> late = List.of(swayedBack, swayed, popup, popup).iterator();
        assertSame(popup, FrameChange.answered(still, motion, () -> Optional.of(late.next()), 5_000, share, clock)
                .orElseThrow(), "the sway is waited past, not taken for the answer");

        waited[0] = 0;
        assertSame(opened, FrameChange.answered(still, motion, () -> Optional.of(opened), 1_000, share, clock)
                .orElseThrow());
        assertEquals(1_000, waited[0], "a change under ANSWER_SHARE is read at the cap: it may be stray sway");

        waited[0] = 0;
        Iterator<BufferedImage> reads = List.of(swayed, swayedBack).iterator();
        assertEquals(List.of(swayed, swayedBack), FrameChange.ambient(() -> Optional.of(reads.next()), clock));
        assertEquals(FrameChange.AMBIENT_READS * FrameChange.POLL_MS, waited[0]);
        assertSame(Motion.NONE, Motion.of(List.of(still, still)));
        assertSame(Motion.NONE, Motion.of(List.of(still)));

        Iterator<Optional<BufferedImage>> failing = List.of(Optional.<BufferedImage>empty(), Optional.of(swayed))
                .iterator();
        assertEquals(List.of(swayed), FrameChange.ambient(failing::next, clock), "a failed read is left out");
    }

    @Test
    void motionReachesOnlyItsGrowAndNeverAnotherSize() {
        BufferedImage still = noise(60, 40, 1);
        BufferedImage swayed = copy(still);
        swayed.setRGB(20, 20, swayed.getRGB(20, 20) ^ 0x808080);
        Motion motion = Motion.of(List.of(still, swayed));
        int reach = FrameChange.AMBIENT_GROW;
        assertTrue(motion.ignores(20 + reach, 20 - reach, 60, 40));
        assertFalse(motion.ignores(20 + reach + 2, 20, 60, 40), "past the grow it is the game's answer");
        assertFalse(motion.ignores(20, 20, 61, 40), "a frame of another size is compared whole");
        assertSame(Motion.NONE, Motion.of(List.of(still, noise(30, 20, 2))));

        assertSame(Motion.NONE, Motion.of(List.of(still, noise(60, 40, 2))),
                "a screen moving all over is compared whole, or no answer would show");
    }
}
