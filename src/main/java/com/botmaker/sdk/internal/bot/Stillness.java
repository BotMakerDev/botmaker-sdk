package com.botmaker.sdk.internal.bot;

import java.awt.image.BufferedImage;

/**
 * How long a game's picture has stayed still although input was sent to it: the one rule that says a game is
 * frozen (2026-11-03). A still picture with no input is a game waiting, never a frozen one; a picture that does not
 * change while the bot clicks, swipes or types is a game that no longer answers.
 *
 * <p>Frames are compared shrunk to grey {@link #THUMB_W}×{@link #THUMB_H}; a mean difference above {@link #MOVED}
 * is a picture that moved. A vision loop keeps its own (its frames, its acts); the run keeps one fed by every
 * capture and every input ({@link Watchdog#saw}, {@link Watchdog#acted}).
 */
public final class Stillness {

    /** The frame compared is shrunk to grey this size. */
    static final int THUMB_W = 64;
    static final int THUMB_H = 36;
    /** A mean grey difference per thumbnail pixel above this, out of 255, is a picture that moved. */
    static final double MOVED = 2.0;

    /**
     * The last frame seen, shrunk; when a frame was last seen; the first and the last input since the picture last
     * moved.
     */
    private int[] thumb;
    private long seenAt;
    private long actedSince = -1;
    private long actedLast;

    /** Counts {@code frame}, seen at {@code now}: a picture that moved clears the input waiting on it. */
    public synchronized void saw(BufferedImage frame, long now) {
        if (frame == null) return;
        int[] shrunk = thumb(frame);
        if (thumb == null || moved(thumb, shrunk)) {
            thumb = shrunk;
            actedSince = -1;
        }
        seenAt = now;
    }

    /**
     * Inputs further apart than this are not one run of input: the wait between them (a timer an idle game counts
     * down) is a game waiting, so the count starts again at the later one.
     */
    static final long MOST_GAP_MS = 30_000;

    /** Counts an input sent at {@code now}. */
    public synchronized void acted(long now) {
        if (thumb == null) return;
        if (actedSince < 0 || now - actedLast > MOST_GAP_MS) actedSince = now;
        actedLast = now;
    }

    /**
     * How long, in ms, input kept coming while the picture stayed still: from the first input since it last moved to
     * the last one a frame was seen after. 0 when none waits. One input followed by a wait never grows it: a change
     * too small to see (a toggle) and a still screen are a game waiting, not a frozen one.
     */
    public synchronized long stillAfterInput() {
        return actedSince < 0 ? 0 : Math.max(0, Math.min(actedLast, seenAt) - actedSince);
    }

    /** Forgets every frame and input: a restarted game starts from no picture. */
    public synchronized void clear() {
        thumb = null;
        actedSince = -1;
    }

    static int[] thumb(BufferedImage frame) {
        int[] grey = new int[THUMB_W * THUMB_H];
        int w = frame.getWidth();
        int h = frame.getHeight();
        for (int ty = 0; ty < THUMB_H; ty++) {
            for (int tx = 0; tx < THUMB_W; tx++) {
                int rgb = frame.getRGB(Math.min(w - 1, (tx * w + w / 2) / THUMB_W),
                        Math.min(h - 1, (ty * h + h / 2) / THUMB_H));
                grey[ty * THUMB_W + tx] = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
            }
        }
        return grey;
    }

    static boolean moved(int[] before, int[] after) {
        long sum = 0;
        for (int i = 0; i < before.length; i++) sum += Math.abs(before[i] - after[i]);
        return sum / (double) before.length > MOVED;
    }
}
