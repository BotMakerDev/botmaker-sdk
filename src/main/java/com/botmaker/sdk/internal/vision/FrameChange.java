package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.geometry.Rect;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What changed between two frames of one source, by one rule: what {@code Screen.waitStable} and
 * {@code Screen.waitChange} ask between polls, and what the assistant's click, key and scroll wait on and report.
 *
 * <p>The tolerance is fixed so a bot writes no number. A pixel is the same while each of its channels moved by less
 * than {@link #SAME} of 255: capture noise, compression, a blended edge. A frame changed when more than
 * {@link #CHANGED_SHARE} of its compared pixels moved: a blinking caret stays under it; a loading bar, a fade or a
 * new screen do not. Pixels are compared every {@link #STEP}, on a coarser grid past {@link #SAMPLES} along the
 * longer side, so a 4K frame costs what a 1080p one does. Until 2026-10-14 the bot's waits compared brightness on a
 * 320-wide grid and the assistant each channel every 2 pixels; a red button turning green kept its brightness.
 *
 * <p>A {@link Motion} makes a compare blind where the screen moves on its own (Firestone's leaves and heroes), and
 * {@link #answered} reads the screen until a game answers an input.
 */
public final class FrameChange {

    /** A channel moving less than this is the same pixel. */
    static final int SAME = 24;
    /** The share of compared pixels that must move for the frame to have changed. */
    static final double CHANGED_SHARE = 0.005;
    /** Compare every this many pixels at the finest; a change smaller than this is not an answer. */
    static final int STEP = 2;
    /** Past this many compared pixels along the longer side the grid coarsens. */
    static final int SAMPLES = 960;

    /** How often the screen is read while the game answers. */
    public static final int POLL_MS = 200;
    /**
     * How long a screen that started changing is given to finish: one that animates on its own never holds still.
     * A game can take seconds to start answering (Firestone's Library, 2026-10-13), so no fixed wait fits.
     */
    public static final int SETTLING_MS = 1_500;
    /**
     * Before an input the screen is read this many times more, {@link #POLL_MS} apart: what moves among those reads
     * moves on its own, and is not the game's answer. Counted as one, the first read after a click already differed
     * and the next held still, so the answer was the screen before the Tavern opened (2026-10-13).
     */
    public static final int AMBIENT_READS = 2;
    /** A pixel that moved on its own hides this many pixels around it: a leaf sways a little further next time. */
    static final int AMBIENT_GROW = 6;
    /**
     * The share of the screen that must change for {@link #answered} to end early: a new screen or a popup. The mask
     * misses sway that paused while it looked, and that alone ended the wait before Firestone drew the Engineer
     * (2026-10-13). A smaller change, a button greying, is read at the end of the wait.
     */
    public static final double ANSWER_SHARE = 0.03;
    /**
     * How long Discovery waits on an input that moved nothing at all, not a pixel past the screen's own motion: the
     * whole cap was spent on every input with no effect, five seconds each (2026-10-14). A game slow to start
     * answering still shows the button pressed, which keeps the wait going.
     */
    public static final int QUIET_MS = 2_000;

    private FrameChange() {}

    /** The grid a {@code width}×{@code height} frame is compared on. */
    static int step(int width, int height) {
        return Math.max(STEP, Math.max(width, height) / SAMPLES);
    }

    /** Whether {@code b} differs from {@code a}; frames of different sizes always do, a missing one never. */
    public static boolean changed(BufferedImage a, BufferedImage b) {
        return changed(a, b, Motion.NONE);
    }

    /** As {@link #changed(BufferedImage, BufferedImage)}, blind where {@code motion} moves on its own. */
    public static boolean changed(BufferedImage a, BufferedImage b, Motion motion) {
        if (a == null || b == null) return false;
        return share(a, b, motion) > CHANGED_SHARE;
    }

    /**
     * The share of the compared pixels that moved from {@code before} to {@code after}, out of the still ones only:
     * a pixel that moves on its own can never count, so counting it would shrink a panel opened over a swaying fight
     * (Firestone's Bag, 11% of the whole screen, 2026-10-14). Another size is all changed.
     */
    public static double share(BufferedImage before, BufferedImage after, Motion motion) {
        if (before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight()) return 1;
        int width = after.getWidth();
        int height = after.getHeight();
        int step = step(width, height);
        int[] a = new int[width];
        int[] b = new int[width];
        int moved = 0;
        int samples = 0;
        for (int y = 0; y < height; y += step) {
            row(before, y, a);
            row(after, y, b);
            for (int x = 0; x < width; x += step) {
                if (motion.ignores(x, y, width, height)) continue;
                samples++;
                if (!same(a[x], b[x])) moved++;
            }
        }
        return samples == 0 ? 0 : (double) moved / samples;
    }

    /**
     * The box around every compared pixel that moved from {@code before} to {@code after}, blind where
     * {@code motion} moves, or empty when none did: a button greying shows here though it is no change of screen.
     * Another size is the whole of {@code after}: the window was resized or replaced.
     */
    public static Optional<Rect> box(BufferedImage before, BufferedImage after, Motion motion) {
        int width = after.getWidth();
        int height = after.getHeight();
        if (before.getWidth() != width || before.getHeight() != height) {
            return Optional.of(new Rect(0, 0, width, height));
        }
        int step = step(width, height);
        int[] a = new int[width];
        int[] b = new int[width];
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y += step) {
            row(before, y, a);
            row(after, y, b);
            for (int x = 0; x < width; x += step) {
                if (same(a[x], b[x]) || motion.ignores(x, y, width, height)) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < 0) return Optional.empty();
        return Optional.of(new Rect(left, top, Math.min(width, right + step) - left,
                Math.min(height, bottom + step) - top));
    }

    /**
     * Row {@code y} of {@code image} into {@code into}, in one call: a pixel at a time went through the colour model
     * for each, and cost a 1080p compare tens of milliseconds.
     */
    private static void row(BufferedImage image, int y, int[] into) {
        image.getRGB(0, y, into.length, 1, into, 0, into.length);
    }

    private static boolean same(int a, int b) {
        return Math.abs((a >> 16 & 0xff) - (b >> 16 & 0xff)) < SAME
                && Math.abs((a >> 8 & 0xff) - (b >> 8 & 0xff)) < SAME
                && Math.abs((a & 0xff) - (b & 0xff)) < SAME;
    }

    /**
     * The compared pixels that moved on their own across some frames, each grown by {@link #AMBIENT_GROW}: a change
     * there is not an answer. Frames of another size than the first add nothing.
     */
    public record Motion(int width, int height, BitSet moving) {

        public static final Motion NONE = new Motion(0, 0, new BitSet());

        public static Motion of(List<BufferedImage> frames) {
            if (frames.size() < 2) return NONE;
            BufferedImage first = frames.getFirst();
            int width = first.getWidth();
            int height = first.getHeight();
            int step = step(width, height);
            int[] ra = new int[width];
            int[] rb = new int[width];
            BitSet moved = new BitSet();
            for (int i = 1; i < frames.size(); i++) {
                BufferedImage a = frames.get(i - 1);
                BufferedImage b = frames.get(i);
                if (a.getWidth() != width || a.getHeight() != height || b.getWidth() != width
                        || b.getHeight() != height) {
                    continue;
                }
                for (int y = 0; y < height; y += step) {
                    row(a, y, ra);
                    row(b, y, rb);
                    for (int x = 0; x < width; x += step) {
                        if (!same(ra[x], rb[x])) moved.set(y * width + x);
                    }
                }
            }
            // A screen that moves nearly all over would hide every answer: compared whole, it still says something.
            int samples = ((width + step - 1) / step) * ((height + step - 1) / step);
            if (moved.isEmpty() || moved.cardinality() * 2 > samples) return NONE;
            // Grown on the grid only: a compare asks of no other pixel.
            BitSet grown = new BitSet();
            int reach = AMBIENT_GROW / step * step;
            for (int at = moved.nextSetBit(0); at >= 0; at = moved.nextSetBit(at + 1)) {
                int x = at % width;
                int y = at / width;
                for (int gy = Math.max(0, y - reach); gy <= Math.min(height - 1, y + reach); gy += step) {
                    for (int gx = Math.max(0, x - reach); gx <= Math.min(width - 1, x + reach); gx += step) {
                        grown.set(gy * width + gx);
                    }
                }
            }
            return new Motion(width, height, grown);
        }

        /** Whether the pixel at {@code x},{@code y} of a {@code w}×{@code h} frame moves on its own. */
        public boolean ignores(int x, int y, int w, int h) {
            return w == width && h == height && moving.get(y * width + x);
        }
    }

    /** Time as {@link #answered} counts it: the wall clock in a run, a counter in a test. */
    public interface Clock {
        Clock REAL = new Clock() {
            @Override
            public long nowMs() {
                return System.nanoTime() / 1_000_000;
            }

            @Override
            public void sleep(long ms) throws InterruptedException {
                Thread.sleep(ms);
            }
        };

        long nowMs();

        void sleep(long ms) throws InterruptedException;
    }

    /** {@link #AMBIENT_READS} more reads of the screen, {@link #POLL_MS} apart; a read that failed is left out. */
    public static List<BufferedImage> ambient(Supplier<Optional<BufferedImage>> look, Clock clock)
            throws InterruptedException {
        List<BufferedImage> read = new ArrayList<>();
        for (int i = 0; i < AMBIENT_READS; i++) {
            clock.sleep(POLL_MS);
            look.get().ifPresent(read::add);
        }
        return read;
    }

    /**
     * The frame the game answered with: the screen read every {@link #POLL_MS} until {@code share} of it differs
     * from {@code before}, then until two reads in a row differ in no compared pixel ({@link #box}; a fade under
     * {@link #CHANGED_SHARE} a poll is still answering) or {@link #SETTLING_MS} went by,
     * never past {@code capMs} (reading a frame takes time too), each compare blind to {@code motion}. A smaller
     * change is read at the cap. When nothing changed by then, the last frame read; empty when none could be read
     * for {@link #SETTLING_MS}: a capture that is gone does not hold the answer for the whole cap.
     */
    public static Optional<BufferedImage> answered(BufferedImage before, Motion motion,
                                                   Supplier<Optional<BufferedImage>> look, int capMs, double share,
                                                   Clock clock) throws InterruptedException {
        return answered(before, motion, look, capMs, share, 0, clock);
    }

    /**
     * As {@link #answered(BufferedImage, Motion, Supplier, int, double, Clock)}, ending at {@code quietMs} too when
     * no read so far moved a compared pixel from {@code before} ({@link #box}): an input with no effect. 0 waits on.
     */
    public static Optional<BufferedImage> answered(BufferedImage before, Motion motion,
                                                   Supplier<Optional<BufferedImage>> look, int capMs, double share,
                                                   int quietMs, Clock clock) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        long start = clock.nowMs();
        long changedAt = -1;
        boolean stirred = false;
        Optional<BufferedImage> last = Optional.empty();
        while (true) {
            Optional<BufferedImage> now = look.get();
            long waited = clock.nowMs() - start;
            if (now.isPresent()) {
                if (changedAt < 0) {
                    if (share(before, now.get(), motion) >= share) changedAt = waited;
                    else if (!stirred && quietMs > 0) stirred = box(before, now.get(), motion).isPresent();
                } else if (last.isPresent() && box(last.get(), now.get(), motion).isEmpty()) {
                    return now;
                }
                last = now;
            }
            if (waited >= capMs || changedAt >= 0 && waited - changedAt >= SETTLING_MS
                    || last.isEmpty() && waited >= SETTLING_MS
                    || quietMs > 0 && changedAt < 0 && !stirred && last.isPresent() && waited >= quietMs) {
                return last;
            }
            clock.sleep(Math.min(POLL_MS, capMs - waited));
        }
    }
}
