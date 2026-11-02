package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A watch of a box on a drawn feed, its clock moving one grab's 16 ms each frame. */
class FrameWatchTest {

    private static final int SIDE = 24;
    private static final int GRAB_MS = 16;
    private static final Rect BOX = new Rect(10, 10, SIDE, SIDE);
    private static final double CONFIDENCE = 0.9;

    @Test
    void cycleOfThreeKeepsThree() {
        Feed feed = new Feed(grab -> pose("ABC".charAt(grab / 2 % 3)));
        FrameWatch.Watched watched = FrameWatch.watch(feed, BOX, CONFIDENCE, feed);
        assertEquals(3, watched.frames().size());
        assertEquals(FrameWatch.Ending.LOOP_CLOSED, watched.ending());
        assertTrue(watched.saveable());
        long loop = watched.loop().orElseThrow().toMillis();
        assertTrue(Math.abs(loop - 96) <= GRAB_MS, "loop " + loop + " ms");
        assertTrue(same(watched.frames().getFirst(), pose('A')), "frame 1 is the first pose");
    }

    @Test
    void stillBoxKeepsOneFrame() {
        Feed feed = new Feed(grab -> pose('A'));
        FrameWatch.Watched watched = FrameWatch.watch(feed, BOX, CONFIDENCE, feed);
        assertEquals(1, watched.frames().size());
        assertEquals(FrameWatch.Ending.LOOP_CLOSED, watched.ending());
        assertTrue(watched.watched().compareTo(FrameWatch.SHORTEST) >= 0, "watched at least the shortest");
        assertTrue(watched.watched().compareTo(FrameWatch.SHORTEST.plusMillis(GRAB_MS)) <= 0, "and no longer");
        assertTrue(watched.loop().isEmpty());
    }

    @Test
    void posesPastTheCapStop() {
        Feed feed = new Feed(grab -> pose((char) ('A' + grab / 2 % (FrameWatch.CAP + 1))));
        FrameWatch.Watched watched = FrameWatch.watch(feed, BOX, CONFIDENCE, feed);
        assertEquals(FrameWatch.Ending.TOO_MANY_POSES, watched.ending());
        assertFalse(watched.saveable());
    }

    /** A thing swaying up to 5 px each way, which kept two poses at full size and 0.9: one pose, matched coarsely. */
    @Test
    void aSmallSwayIsOnePose() {
        Rect box = new Rect(16, 16, 96, 96);
        int[] sway = {0, 1, 2, 3, 4, 5, 4, 3, 2, 1};
        Feed feed = new Feed(grab -> swayed(box, sway[grab % sway.length]));
        FrameWatch.Watched watched = FrameWatch.watch(feed, box, CONFIDENCE, feed);
        assertEquals(1, watched.frames().size());
        assertEquals(FrameWatch.Ending.LOOP_CLOSED, watched.ending());
    }

    /** A disc and a block drawn sharp in {@code box}, moved {@code dx} px right. */
    private static BufferedImage swayed(Rect box, int dx) {
        BufferedImage frame = new BufferedImage(128, 128, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = frame.createGraphics();
        try {
            g.setColor(new Color(200, 200, 200));
            g.fillRect(box.x(), box.y(), box.width(), box.height());
            g.setColor(new Color(90, 60, 40));
            g.fillOval(box.x() + 18 + dx, box.y() + 18, 60, 60);
            g.setColor(new Color(200, 10, 10));
            g.fillRect(box.x() + 40 + dx, box.y() + 10, 16, 20);
        } finally {
            g.dispose();
        }
        return frame;
    }

    @Test
    void feedLostSavesNothing() {
        Feed feed = new Feed(grab -> grab < 5 ? pose("AB".charAt(grab % 2)) : null);
        FrameWatch.Watched watched = FrameWatch.watch(feed, BOX, CONFIDENCE, feed);
        assertEquals(FrameWatch.Ending.FEED_LOST, watched.ending());
        assertFalse(watched.saveable());
    }

    @Test
    void oneMissedGrabGoesOn() {
        Feed feed = new Feed(grab -> grab == 3 ? null : pose("AB".charAt(grab / 2 % 2)));
        FrameWatch.Watched watched = FrameWatch.watch(feed, BOX, CONFIDENCE, feed);
        assertEquals(FrameWatch.Ending.LOOP_CLOSED, watched.ending());
        assertEquals(2, watched.frames().size());
    }

    @Test
    void slowPoseStillCaught() {
        // A alone until 0.9 s, then B, which keeps the watch going to 1.8 s; C shows first at 1.7 s: to 3.4 s.
        int bAt = 900 / GRAB_MS;
        int cAt = 1700 / GRAB_MS;
        Feed feed = new Feed(grab -> pose(grab < bAt ? 'A' : grab >= cAt && (grab - cAt) % 50 < 3 ? 'C'
                : "AB".charAt(grab / 2 % 2)));
        FrameWatch.Watched watched = FrameWatch.watch(feed, BOX, CONFIDENCE, feed);
        assertEquals(3, watched.frames().size());
        assertEquals(FrameWatch.Ending.LOOP_CLOSED, watched.ending());
        assertTrue(watched.watched().compareTo(Duration.ofMillis(3400)) >= 0, "watched " + watched.watched());
    }

    @Test
    void longerAddsToKept() {
        Feed feed = new Feed(grab -> pose('C'));
        FrameWatch.Watched watched = FrameWatch.watch(feed, BOX, CONFIDENCE, feed,
                List.of(cut(pose('A')), cut(pose('B'))));
        assertEquals(3, watched.frames().size());
        assertTrue(same(watched.frames().get(2), pose('C')), "the new pose comes last");
    }

    @Test
    void aSlowFeedIsSaidSlow() {
        Feed feed = new Feed(grab -> pose('A'), 100);
        assertTrue(FrameWatch.watch(feed, BOX, CONFIDENCE, feed).slow());
        Feed quick = new Feed(grab -> pose('A'));
        assertFalse(FrameWatch.watch(quick, BOX, CONFIDENCE, quick).slow());
    }

    /** A frame showing pose {@code letter} in {@link #BOX}, the rest of the screen alike for every pose. */
    private static BufferedImage pose(char letter) {
        BufferedImage frame = new BufferedImage(64, 64, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(letter);
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) frame.setRGB(BOX.x() + x, BOX.y() + y, rnd.nextInt(0xFFFFFF));
        }
        return frame;
    }

    private static BufferedImage cut(BufferedImage frame) {
        return frame.getSubimage(BOX.x(), BOX.y(), SIDE, SIDE);
    }

    /** Whether the kept frame {@code kept} is {@code frame}'s box, pixel for pixel. */
    private static boolean same(BufferedImage kept, BufferedImage frame) {
        BufferedImage box = cut(frame);
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) if (kept.getRGB(x, y) != box.getRGB(x, y)) return false;
        }
        return true;
    }

    /** Frame {@code n} of {@code poses} on each grab (null: none), the clock moving {@code ms} a grab. */
    private static final class Feed implements Supplier<Optional<BufferedImage>>, InstantSource {
        private final IntFunction<BufferedImage> poses;
        private final int ms;
        private int grab;
        private long now;

        Feed(IntFunction<BufferedImage> poses) {
            this(poses, GRAB_MS);
        }

        Feed(IntFunction<BufferedImage> poses, int ms) {
            this.poses = poses;
            this.ms = ms;
        }

        @Override
        public Optional<BufferedImage> get() {
            now += ms;
            return Optional.ofNullable(poses.apply(grab++));
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now);
        }
    }
}
