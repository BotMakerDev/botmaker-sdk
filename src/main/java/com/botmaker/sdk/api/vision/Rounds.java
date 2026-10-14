package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.bot.BotStuckException;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * How a vision loop ends when its action does not end it. Each loop counts its rounds and throws
 * {@link BotStuckException} past {@link BotSettings#maxRounds()}, which the run recovers from: the watchdog's
 * checkpoint runs between activities only, so a loop inside a body that never ends was never seen by it. And each
 * round gives its action {@link BotSettings#patience()} to show before the loop looks again, so a round is not a
 * blind re-find of a screen the game has not redrawn yet.
 *
 * <p>A loop also tells a frozen game (2026-10-14): it acts every round, so a whole frame that stays the same
 * from round to round for {@link BotSettings#frozenAfter()} is a game that no longer answers, and the loop throws
 * before it has run its rounds out. Each loop watches its own frames, with its own acts: a program-wide count of
 * clicks and captures credited acts to screens the bot never looked at.
 */
final class Rounds {

    /** The frame a round compares is shrunk to grey this size. */
    static final int THUMB_W = 64;
    static final int THUMB_H = 36;
    /** A mean grey difference per thumbnail pixel above this, out of 255, is a screen that moved. */
    static final double MOVED = 2.0;

    /** The time a still screen is measured in; a test replaces it. */
    static LongSupplier clock = System::currentTimeMillis;

    /** How often a round looks at the screen while it waits. */
    static final int POLL_MS = 100;

    /** A box this much bigger on each side, at least, is compared: the text beside a button changes, not it. */
    static final int MARGIN = 16;

    /** A mean difference per channel above this, out of 255, is a change; below it, noise and compression. */
    static final double CHANGED = 6;

    private final String loop;
    private final int max;
    private final int frozenAfter;
    private int done;
    /** The last round's frame, shrunk; and when the frame was last seen to move. */
    private int[] thumb;
    private long stillSince;

    Rounds(String loop) {
        this.loop = loop;
        this.max = BotSettings.current().maxRounds();
        this.frozenAfter = BotSettings.current().frozenAfter();
    }

    /**
     * Counts one round about to act on {@code seen}, the frame it looked at (null when there is none): false when
     * the run is stopping (its thread interrupted), so the loop ends without acting again; throws when the loop
     * has run all its rounds, or when its frames have not moved for {@link BotSettings#frozenAfter()}.
     */
    boolean next(BufferedImage seen) {
        if (Thread.currentThread().isInterrupted()) return false;
        if (seen != null) {
            long now = clock.getAsLong();
            int[] shrunk = thumb(seen);
            if (thumb == null || moved(thumb, shrunk)) {
                thumb = shrunk;
                stillSince = now;
            } else if (now - stillSince >= frozenAfter) {
                throw new BotStuckException("The game looks frozen: " + loop + " acted on the same screen for "
                        + (now - stillSince) / 1000 + " s (BotSettings.frozenAfter)");
            }
        }
        if (++done > max) {
            throw new BotStuckException(loop + " ran " + max + " rounds and did not end; its action does not "
                    + "change what it looks for (BotSettings.maxRounds)");
        }
        return true;
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

    /** Waits until {@code seen} is true, looking every {@link #POLL_MS}, at most {@code patienceMs}. */
    static boolean await(BooleanSupplier seen, long patienceMs) {
        long deadline = System.currentTimeMillis() + patienceMs;
        while (System.currentTimeMillis() < deadline) {
            Wait.milliseconds(POLL_MS);
            if (Thread.currentThread().isInterrupted()) return false;
            if (seen.getAsBoolean()) return true;
        }
        return false;
    }

    /**
     * Waits until what {@code frame} matched looks different on {@code source}, at most {@code patienceMs}: the
     * matched boxes, each grown by {@link #MARGIN}, compared with the frame's own pixels. A frame without pixels
     * or matches has nothing to wait on.
     */
    static void awaitChange(Vision.Frame frame, long patienceMs) {
        BufferedImage before = frame.pixels();
        if (before == null || frame.matches().isEmpty() || patienceMs <= 0) return;
        List<Rect> areas = areas(frame, before);
        if (areas.isEmpty()) return;
        await(() -> {
            BufferedImage after = frame.source().capture();
            return after == null || after.getWidth() != before.getWidth() || after.getHeight() != before.getHeight()
                    || areas.stream().anyMatch(a -> changed(before, after, a));
        }, patienceMs);
    }

    /** The matched boxes in the frame's own pixels, grown and kept inside it. */
    static List<Rect> areas(Vision.Frame frame, BufferedImage pixels) {
        Point origin = frame.source().origin();
        Rect whole = new Rect(0, 0, pixels.getWidth(), pixels.getHeight());
        List<Rect> areas = new ArrayList<>();
        for (MatchResult match : frame.matches().all()) {
            Rect r = match.rect();
            int grow = Math.max(MARGIN, Math.max(r.width(), r.height()) / 4);
            Rect inside = new Rect(r.x() - origin.x(), r.y() - origin.y(), r.width(), r.height()).expand(grow)
                    .intersection(whole);
            if (inside != null && inside.width() > 0 && inside.height() > 0) areas.add(inside);
        }
        return areas;
    }

    /** Whether {@code area} of the two images differs by more than {@link #CHANGED} on average. */
    static boolean changed(BufferedImage before, BufferedImage after, Rect area) {
        long sum = 0;
        int step = Math.max(1, Math.min(area.width(), area.height()) / 32);
        int samples = 0;
        for (int y = area.y(); y < area.y() + area.height(); y += step) {
            for (int x = area.x(); x < area.x() + area.width(); x += step) {
                int a = before.getRGB(x, y);
                int b = after.getRGB(x, y);
                sum += Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                        + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                        + Math.abs((a & 0xFF) - (b & 0xFF));
                samples++;
            }
        }
        return samples > 0 && sum / (3.0 * samples) > CHANGED;
    }
}
