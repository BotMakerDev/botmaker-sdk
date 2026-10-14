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

/**
 * How a vision loop ends when its action does not end it. Each loop counts its rounds and throws
 * {@link BotStuckException} past {@link BotSettings#maxRounds()}, which the run recovers from: the watchdog's
 * checkpoint runs between activities only, so a loop inside a body that never ends was never seen by it. And each
 * round gives its action {@link BotSettings#patience()} to show before the loop looks again, so a round is not a
 * blind re-find of a screen the game has not redrawn yet.
 */
final class Rounds {

    /** How often a round looks at the screen while it waits. */
    static final int POLL_MS = 100;

    /** A box this much bigger on each side, at least, is compared: the text beside a button changes, not it. */
    static final int MARGIN = 16;

    /** A mean difference per channel above this, out of 255, is a change; below it, noise and compression. */
    static final double CHANGED = 6;

    private final String loop;
    private final int max;
    private int done;

    Rounds(String loop) {
        this.loop = loop;
        this.max = BotSettings.current().maxRounds();
    }

    /**
     * Counts one round: false when the run is stopping (its thread interrupted), so the loop ends without acting
     * again; throws when the loop has run all of them.
     */
    boolean next() {
        if (Thread.currentThread().isInterrupted()) return false;
        if (++done > max) {
            throw new BotStuckException(loop + " ran " + max + " rounds and did not end; its action does not "
                    + "change what it looks for (BotSettings.maxRounds)");
        }
        return true;
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
