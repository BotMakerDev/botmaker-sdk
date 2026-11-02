package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.vision.BotScreen;
import com.botmaker.sdk.internal.vision.TemplateCache;
import com.botmaker.sdk.internal.vision.TemplateSource;
import com.botmaker.shared.opencv.OpenCvNative;
import org.opencv.core.Mat;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * How a button's usual picture shows where it matched, told by colour: the shown pixels fitted against the cut's by
 * least squares over its opaque pixels, {@code shown = gain × cut + offset} in each channel, or each channel from the
 * cut's light for a greyed one. The fit is checked against the bot's learned {@link Shade}s; none fits, the look's own
 * state holds (2026-11-02: no threshold is built in, a bot with no shade sees no computed version).
 *
 * <p>A glow, a scale and a shift are told by the match's geometry instead: the ring around it against the frame
 * beyond, its size against the cut's, its place against the cut's place, each scaled to the frame as places are.
 */
public final class ShadeFit {

    /** A fit off by more than this, on average over the pixels, is no colour change: another picture, or a glow. */
    static final double MOST_ERROR = 0.08;
    /** A channel with less spread than this in the cut is flat: its gain is told by its mean. */
    private static final double FLAT = 1e-4;
    /** Gains this far apart are a tint; offsets above this lift a fade or a brightening. */
    private static final double APART = 0.1;
    private static final double LIFTED = 0.04;
    /** A light fit this close to the own-channel one fits as well. */
    private static final double CLOSE = 0.01;
    /**
     * How many cuts and tinted or scaled copies are kept, the least used dropped first: a map's every picture, its copy
     * at two sizes and a tint, so a poll never redraws one.
     */
    private static final int KEPT = 512;

    /** The run's learned shades, as {@code Sdk.shades()} says, handed over by the run before the first look. */
    private static volatile List<Shade> learned = List.of();

    /**
     * Where the run's own input last was, run-wide since each call makes its reach: a shade the bot's input causes
     * (hover, press, focus) applies to the button under it only. Input a flow sends past the map is not seen.
     */
    private static volatile Pointer pointer;

    /** Each picture file's pixels and each tinted copy, by the file's path and stamp: a re-cut file is read anew. */
    private static final Map<String, Object> KEPT_PIXELS = Collections.synchronizedMap(
            new LinkedHashMap<>(KEPT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    boolean drop = size() > KEPT;
                    // A dropped copy's pixels are native memory; one still in a caller's hands loads them again.
                    if (drop && eldest.getValue() instanceof ImageTemplate copy) copy.close();
                    return drop;
                }
            });

    private ShadeFit() {}

    /** Takes the bot's shades, as the run hands them over; a new run starts with no input seen. */
    public static void use(Shades shades) {
        learned = shades == null ? List.of() : shades.list();
        pointer = null;
    }

    /** The bot's own input went to {@code where}. */
    public static void moved(Pointer where) {
        pointer = where;
    }

    /** Where the bot's own input last was; null before any. */
    public static Pointer pointer() {
        return pointer;
    }

    /** The run's learned shades. */
    public static List<Shade> learned() {
        return learned;
    }

    /**
     * How {@code shown} relates to {@code cut}: per channel a gain and an offset, both 0 to 1 scaled, and how far the
     * pixels stray from them.
     *
     * @param grey    whether each channel follows the cut's light rather than its own channel
     * @param error   the root mean square of what the fit leaves, 0 to 1
     * @param greyToo the fit by the cut's light when it fits as well (a cut with no colour): a learned grey is checked
     *                against it; null otherwise
     */
    public record Fit(boolean grey, Shade.Channels gain, Shade.Channels offset, double error, Fit greyToo) {

        /** Whether {@code shade} names this change: its model, its gain and offset within its tolerance. */
        boolean fits(Shade shade) {
            if (shade.moves()) return false;
            if (asGrey(shade)) return greyToo.fits(shade);
            if (error > MOST_ERROR || grey != (shade.kind() == Shade.Kind.GREY)) return false;
            return distance(shade) <= shade.tolerance();
        }

        private boolean asGrey(Shade shade) {
            return !grey && greyToo != null && shade.kind() == Shade.Kind.GREY;
        }

        /** The farthest of this fit's gains and offsets from {@code shade}'s. */
        double distance(Shade shade) {
            if (asGrey(shade)) return greyToo.distance(shade);
            double far = 0;
            for (int c = 0; c < 3; c++) {
                far = Math.max(far, Math.abs(gain.get(c) - shade.gain().get(c)));
                far = Math.max(far, Math.abs(offset.get(c) - shade.offset().get(c)));
            }
            return far;
        }

        /**
         * Whether this is a change of the cut at all: a colour change (within {@link #MOST_ERROR}), its gain or offset
         * off the cut's own by more than a shade's {@link Shade#TOLERANCE}, or the colour lost.
         */
        public boolean offIdentity() {
            if (error > MOST_ERROR) return false;
            if (grey) return true;
            for (int c = 0; c < 3; c++) {
                if (Math.abs(gain.get(c) - 1) > Shade.TOLERANCE || Math.abs(offset.get(c)) > Shade.TOLERANCE) {
                    return true;
                }
            }
            return false;
        }

        /** Whether the button shows as cut: a fit within {@link #MOST_ERROR} that changes nothing; a bad fit is not. */
        public boolean asCut() {
            return error <= MOST_ERROR && !offIdentity();
        }

        /** Whether {@code other} is the same change: both grey or neither, each gain and offset within a tolerance. */
        public boolean alike(Fit other) {
            if (grey != other.grey) return false;
            for (int c = 0; c < 3; c++) {
                if (Math.abs(gain.get(c) - other.gain.get(c)) > Shade.TOLERANCE
                        || Math.abs(offset.get(c) - other.offset.get(c)) > Shade.TOLERANCE) {
                    return false;
                }
            }
            return true;
        }

        /** What kind of change this is, by its numbers. */
        public Shade.Kind kind() {
            if (grey) return Shade.Kind.GREY;
            double low = Math.min(gain.red(), Math.min(gain.green(), gain.blue()));
            double high = Math.max(gain.red(), Math.max(gain.green(), gain.blue()));
            if (high - low > APART) return Shade.Kind.TINT;
            double mean = (gain.red() + gain.green() + gain.blue()) / 3;
            boolean lifted = (offset.red() + offset.green() + offset.blue()) / 3 > LIFTED;
            if (mean < 1) return lifted ? Shade.Kind.FADE : Shade.Kind.DIM;
            return Shade.Kind.BRIGHT;
        }

        /** This fit as a shade of {@code cause} meaning {@code state}: what Discovery writes from one button seen both ways. */
        public Shade learned(Shade.Cause cause, ButtonState state) {
            return new Shade(kind(), round(gain), round(offset), Shade.TOLERANCE, cause, state, Shade.Geometry.NONE);
        }

        private static Shade.Channels round(Shade.Channels c) {
            return new Shade.Channels(hundredth(c.red()), hundredth(c.green()), hundredth(c.blue()));
        }

        private static double hundredth(double value) {
            return Math.round(value * 100) / 100.0;
        }
    }

    /** How long after a press its button still shows held down: a frame read right after the tap. */
    static final long PRESS_MS = 600;

    /**
     * Where the bot's own input last was, in the frame's pixels: a shade it causes applies to the button under it only.
     *
     * @param at      where it moved or pressed
     * @param atMs    when, by the reach's clock
     * @param pressed whether it pressed there, so the button there has the focus; a hover only moved
     * @param hovers  whether the target has a cursor that rests: an emulator has none
     */
    public record Pointer(Point at, long atMs, boolean pressed, boolean hovers) {}

    /**
     * The causes a shade of a button matched at {@code found} may have, the bot's last input {@code last} at
     * {@code nowMs}: the game's always; hover where the cursor rests on it; focus on the button last pressed; press
     * just after pressing it.
     */
    public static Set<Shade.Cause> causes(Rect found, Pointer last, long nowMs) {
        if (last == null || !found.contains(last.at())) return Set.of(Shade.Cause.GAME);
        Set<Shade.Cause> causes = EnumSet.of(Shade.Cause.GAME);
        if (last.hovers()) causes.add(Shade.Cause.HOVER);
        if (last.pressed()) causes.add(Shade.Cause.FOCUS);
        if (last.pressed() && nowMs - last.atMs() < PRESS_MS) causes.add(Shade.Cause.PRESS);
        return causes;
    }

    /**
     * The shade of {@code shades}, of one of {@code causes}, that {@code picture} shows where it matched {@code frame}
     * at {@code found}: the one the fit is closest to. Empty for none, and for a picture whose file cannot be read.
     */
    public static Optional<Shade> shown(ImageTemplate picture, Rect found, BufferedImage frame, List<Shade> shades,
                                        Set<Shade.Cause> causes) {
        if (shades.stream().noneMatch(s -> causes.contains(s.cause()))) return Optional.empty();
        return cut(picture).flatMap(cut -> shown(new Cut(cut, picture.frames(), picture.place(), authored(picture)),
                found, frame, shades, causes));
    }

    /**
     * A picture's pixels and where it was cut: what the geometry of a match is told against.
     *
     * @param pixels   the file's pixels, its frames side by side
     * @param frames   how many frames
     * @param place    where it was cut, in the frame it was cut from; null when it says none
     * @param authored the size of that frame; null when nothing says it (the match is then not scaled)
     */
    record Cut(BufferedImage pixels, int frames, Rect place, Size authored) {

        int width() {
            return pixels.getWidth() / Math.max(1, frames);
        }

        int height() {
            return pixels.getHeight();
        }
    }

    /** {@link #shown(ImageTemplate, Rect, BufferedImage, List, Set)} of a cut already read. */
    static Optional<Shade> shown(Cut cut, Rect found, BufferedImage frame, List<Shade> shades, Set<Shade.Cause> causes) {
        Fit fit = shades.stream().anyMatch(s -> !s.moves() && causes.contains(s.cause()))
                ? fit(cut.pixels(), cut.frames(), frame, found).orElse(null) : null;
        double[] scale = scale(cut.authored(), frame);
        return closest(shades, causes, shade -> shade.moves() ? moved(shade, cut, scale, found, frame)
                : fit != null && fit.fits(shade) ? OptionalDouble.of(fit.distance(shade)) : OptionalDouble.empty());
    }

    /**
     * Of {@code shades} of one of {@code causes}, the one {@code fit} names and is closest to; a hover or a press
     * first, so the button under the cursor never reads as the game's dim its hover looks like. Focus is weighed with
     * the game's by distance: it lasts until the next press, and the game may dim the button just pressed.
     */
    public static Optional<Shade> closest(Fit fit, List<Shade> shades, Set<Shade.Cause> causes) {
        return closest(shades, causes,
                shade -> fit.fits(shade) ? OptionalDouble.of(fit.distance(shade)) : OptionalDouble.empty());
    }

    /** {@link #closest(Fit, List, Set)} by {@code distance}, empty for a shade that does not fit. */
    private static Optional<Shade> closest(List<Shade> shades, Set<Shade.Cause> causes,
                                           Function<Shade, OptionalDouble> distance) {
        Shade best = null;
        double nearest = 0;
        for (Shade shade : shades) {
            if (!causes.contains(shade.cause())) continue;
            OptionalDouble far = distance.apply(shade);
            if (far.isEmpty()) continue;
            if (best == null || rank(shade) < rank(best)
                    || rank(shade) == rank(best) && far.getAsDouble() < nearest) {
                best = shade;
                nearest = far.getAsDouble();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * How far the match at {@code found} is from the move {@code shade} learned, as a share like its tolerance; empty
     * when it does not show it: a glow's ring no lighter, a match at the cut's size or place.
     */
    private static OptionalDouble moved(Shade shade, Cut cut, double[] scale, Rect found, BufferedImage frame) {
        Shade.Geometry move = shade.geometry();
        double tolerance = shade.tolerance();
        return switch (shade.kind()) {
            case GLOW -> glowed(shade, (int) Math.round(move.ring() * (scale[0] + scale[1]) / 2), found, frame);
            case SCALE -> {
                double across = found.width() / (cut.width() * scale[0]);
                double down = found.height() / (cut.height() * scale[1]);
                double size = (across + down) / 2;
                if (Math.abs(across - down) > tolerance || Math.abs(size - 1) <= tolerance) yield OptionalDouble.empty();
                double off = size < move.smallest() ? move.smallest() - size
                        : size > move.largest() ? size - move.largest() : 0;
                yield off <= tolerance ? OptionalDouble.of(off) : OptionalDouble.empty();
            }
            case SHIFT -> {
                if (cut.place() == null) yield OptionalDouble.empty();
                double across = found.x() - cut.place().x() * scale[0];
                double down = found.y() - cut.place().y() * scale[1];
                double side = Math.max(1, Math.min(found.width(), found.height()));
                double slack = Math.max(SHIFT_SLACK, tolerance * side);
                if (Math.hypot(across, down) <= slack) yield OptionalDouble.empty();
                double off = Math.max(Math.abs(across - move.dx() * scale[0]), Math.abs(down - move.dy() * scale[1]));
                yield off <= slack ? OptionalDouble.of(off / side) : OptionalDouble.empty();
            }
            default -> OptionalDouble.empty();
        };
    }

    /** A shift of this many frame pixels or fewer is a match's own rounding, never a move. */
    private static final int SHIFT_SLACK = 2;

    /**
     * How far the ring {@code width} wide around {@code found} is lighter than the band as wide beyond it, against
     * {@code shade}'s offset; empty when the ring is not lit by the shade's tolerance or strays from it.
     */
    private static OptionalDouble glowed(Shade shade, int width, Rect found, BufferedImage frame) {
        Optional<Shade.Channels> lift = ring(frame, found, Math.max(1, width));
        if (lift.isEmpty()) return OptionalDouble.empty();
        double most = 0;
        double far = 0;
        for (int c = 0; c < 3; c++) {
            most = Math.max(most, lift.get().get(c));
            far = Math.max(far, Math.abs(lift.get().get(c) - shade.offset().get(c)));
        }
        return most >= shade.tolerance() && far <= shade.tolerance() ? OptionalDouble.of(far) : OptionalDouble.empty();
    }

    /**
     * How much lighter, per channel 0 to 1, the ring {@code width} wide around {@code found} is than the band as wide
     * beyond it: what a glow shows, and what Discovery learns one from. Empty when either is off the frame.
     */
    public static Optional<Shade.Channels> ring(BufferedImage frame, Rect found, int width) {
        double[] ring = new double[4];
        double[] beyond = new double[4];
        int x0 = found.x() - 2 * width;
        int y0 = found.y() - 2 * width;
        int x1 = found.x() + found.width() + 2 * width;
        int y1 = found.y() + found.height() + 2 * width;
        for (int y = Math.max(0, y0); y < Math.min(frame.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(frame.getWidth(), x1); x++) {
                // How many pixels outside the button: 0 inside it.
                int out = Math.max(Math.max(found.x() - x, x - (found.x() + found.width() - 1)),
                        Math.max(found.y() - y, y - (found.y() + found.height() - 1)));
                if (out <= 0) continue;
                double[] into = out <= width ? ring : beyond;
                double[] rgb = channels(frame.getRGB(x, y));
                for (int c = 0; c < 3; c++) into[c] += rgb[c];
                into[3]++;
            }
        }
        if (ring[3] == 0 || beyond[3] == 0) return Optional.empty();
        return Optional.of(new Shade.Channels(ring[0] / ring[3] - beyond[0] / beyond[3],
                ring[1] / ring[3] - beyond[1] / beyond[3], ring[2] / ring[3] - beyond[2] / beyond[3]));
    }

    /** The size of the frame {@code picture} was cut from: its own, else the bot's screen; null for neither. */
    private static Size authored(ImageTemplate picture) {
        return picture.frame() != null ? picture.frame() : BotScreen.size().orElse(null);
    }

    /** How much bigger {@code frame} shows than {@code authored}, across and down: 1 when nothing says. */
    private static double[] scale(Size authored, BufferedImage frame) {
        if (authored == null || authored.empty()) return new double[]{1, 1};
        return new double[]{frame.getWidth() / (double) authored.width(), frame.getHeight() / (double) authored.height()};
    }

    private static int rank(Shade shade) {
        return shade.cause() == Shade.Cause.HOVER || shade.cause() == Shade.Cause.PRESS ? 0 : 1;
    }

    /**
     * How {@code frame} at {@code found} shows {@code cut}'s first of {@code frames} frames, each cut pixel read at
     * its place in {@code found} (the match may be scaled). Empty when there is nothing to fit: no opaque pixel, a
     * place off the frame.
     */
    public static Optional<Fit> fit(BufferedImage cut, int frames, BufferedImage frame, Rect found) {
        int width = cut.getWidth() / Math.max(1, frames);
        int height = cut.getHeight();
        if (width < 1 || height < 1 || found.width() < 1 || found.height() < 1) return Optional.empty();
        // The match does not say which frame it was: the one the shown pixels fit best.
        Fit best = null;
        for (int f = 0; f < Math.max(1, frames); f++) {
            Fit one = fit(cut, f * width, width, height, frame, found, null);
            if (one != null && (best == null || one.error() < best.error())) best = one;
        }
        return Optional.ofNullable(best);
    }

    /** {@link #fit(BufferedImage, int, BufferedImage, Rect)} of {@code picture}'s file; empty when it cannot be read. */
    public static Optional<Fit> fit(ImageTemplate picture, Rect found, BufferedImage frame) {
        return cut(picture).flatMap(cut -> fit(cut, picture.frames(), frame, found));
    }

    /** A pixel off its fit by more than this, 0 to 1 averaged over the channels, is not the fit's. */
    private static final double OFF = 0.2;
    /** The share of the cut's opaque pixels an addition covers: more than a speck, less than half. */
    private static final double LEAST_ADDED = 0.02;
    private static final double MOST_ADDED = 0.5;
    /** A whole fit off by no more than this, root mean square, leaves nothing to look for an addition in. */
    private static final double ROUGH = 0.03;
    /** The share of what the whole fit leaves that the addition must hold. */
    private static final double MOST_OF = 0.6;

    /**
     * A part shown on the button that its cut lacks — a lock, a badge, a check — after the best colour fit: one patch of
     * pixels off that fit holding most of what it leaves, the rest fitting once it is set aside.
     *
     * @param area where it shows, in the frame's pixels
     * @param fit  the colour fit of the rest of the button
     */
    public record Addition(Rect area, Fit fit) {}

    /** {@code picture}'s addition where it matched {@code frame} at {@code found}; empty for none, or a moving picture. */
    public static Optional<Addition> addition(ImageTemplate picture, Rect found, BufferedImage frame) {
        if (picture.frames() > 1) return Optional.empty();
        return cut(picture).flatMap(cut -> addition(cut, frame, found));
    }

    static Optional<Addition> addition(BufferedImage cut, BufferedImage frame, Rect found) {
        int width = cut.getWidth();
        int height = cut.getHeight();
        if (width < 1 || height < 1 || found.width() < 1 || found.height() < 1) return Optional.empty();
        Fit whole = fit(cut, 0, width, height, frame, found, null);
        // A button its fit reads well holds no addition: most buttons on a frame, spared the patch search.
        if (whole == null || whole.error() <= ROUGH) return Optional.empty();
        boolean[] patch = largestPatch(offBy(cut, width, height, frame, found, whole), width, height);
        if (patch == null) return Optional.empty();
        Fit rest = fit(cut, 0, width, height, frame, found, patch);
        if (rest == null || rest.error() > MOST_ERROR || rest.error() * 2 > whole.error()) return Optional.empty();
        // Weighed against the rest's own fit, which the patch no longer pulls off.
        double[] off = offBy(cut, width, height, frame, found, rest);
        double total = 0;
        double held = 0;
        int opaque = 0;
        int size = 0;
        int left = width, top = height, right = -1, bottom = -1;
        for (int i = 0; i < off.length; i++) {
            if (off[i] < 0) continue;
            total += off[i];
            opaque++;
            if (!patch[i]) continue;
            size++;
            held += off[i];
            left = Math.min(left, i % width);
            right = Math.max(right, i % width);
            top = Math.min(top, i / width);
            bottom = Math.max(bottom, i / width);
        }
        if (total <= 0 || size < LEAST_ADDED * opaque || size > MOST_ADDED * opaque || held < MOST_OF * total) {
            return Optional.empty();
        }
        int x = found.x() + left * found.width() / width;
        int y = found.y() + top * found.height() / height;
        int x2 = found.x() + (right + 1) * found.width() / width;
        int y2 = found.y() + (bottom + 1) * found.height() / height;
        return Optional.of(new Addition(new Rect(x, y, Math.max(1, x2 - x), Math.max(1, y2 - y)), rest));
    }

    /** How far each cut pixel is off {@code fit}, averaged over the channels; -1 for one see-through or off the frame. */
    private static double[] offBy(BufferedImage cut, int width, int height, BufferedImage frame, Rect found, Fit fit) {
        boolean alpha = cut.getColorModel().hasAlpha();
        double[] off = new double[width * height];
        for (int y = 0; y < height; y++) {
            int fy = found.y() + y * found.height() / height;
            for (int x = 0; x < width; x++) {
                int fx = found.x() + x * found.width() / width;
                int argb = cut.getRGB(x, y);
                if (fy < 0 || fy >= frame.getHeight() || fx < 0 || fx >= frame.getWidth()
                        || alpha && (argb >>> 24) < 128) {
                    off[y * width + x] = -1;
                    continue;
                }
                double[] in = channels(argb);
                double[] out = channels(frame.getRGB(fx, fy));
                double light = 0.299 * in[0] + 0.587 * in[1] + 0.114 * in[2];
                double sum = 0;
                for (int c = 0; c < 3; c++) {
                    sum += Math.abs(out[c] - (fit.gain().get(c) * (fit.grey() ? light : in[c]) + fit.offset().get(c)));
                }
                off[y * width + x] = sum / 3;
            }
        }
        return off;
    }

    /** The largest 4-connected patch of pixels off by more than {@link #OFF}, by what they hold; null for none. */
    private static boolean[] largestPatch(double[] off, int width, int height) {
        int[] label = new int[off.length];
        int labels = 0;
        int best = 0;
        double bestHeld = 0;
        int[] queue = new int[off.length];
        for (int start = 0; start < off.length; start++) {
            if (label[start] != 0 || off[start] <= OFF) continue;
            labels++;
            double held = 0;
            int head = 0, tail = 0;
            queue[tail++] = start;
            label[start] = labels;
            while (head < tail) {
                int i = queue[head++];
                held += off[i];
                int x = i % width;
                int y = i / width;
                int[] next = {x > 0 ? i - 1 : -1, x < width - 1 ? i + 1 : -1, y > 0 ? i - width : -1,
                        y < height - 1 ? i + width : -1};
                for (int n : next) {
                    if (n >= 0 && label[n] == 0 && off[n] > OFF) {
                        label[n] = labels;
                        queue[tail++] = n;
                    }
                }
            }
            if (held > bestHeld) {
                bestHeld = held;
                best = labels;
            }
        }
        if (best == 0) return null;
        boolean[] patch = new boolean[off.length];
        for (int i = 0; i < off.length; i++) patch[i] = label[i] == best;
        return patch;
    }

    /**
     * {@link #fit} of the frame of {@code cut} starting at {@code x0}, the pixels {@code skip} marks (by
     * {@code y × width + x}) set aside; null with nothing to fit.
     */
    private static Fit fit(BufferedImage cut, int x0, int width, int height, BufferedImage frame, Rect found,
                           boolean[] skip) {
        boolean alpha = cut.getColorModel().hasAlpha();
        // Sums for the least squares: per channel over the cut's own channel, and over the cut's light.
        double n = 0;
        double[] sx = new double[3], sxx = new double[3], sy = new double[3], syy = new double[3], sxy = new double[3];
        double sl = 0, sll = 0;
        double[] sly = new double[3];
        for (int y = 0; y < height; y++) {
            int fy = found.y() + y * found.height() / height;
            if (fy < 0 || fy >= frame.getHeight()) continue;
            for (int x = 0; x < width; x++) {
                int fx = found.x() + x * found.width() / width;
                if (fx < 0 || fx >= frame.getWidth() || skip != null && skip[y * width + x]) continue;
                int argb = cut.getRGB(x0 + x, y);
                if (alpha && (argb >>> 24) < 128) continue;
                int rgb = frame.getRGB(fx, fy);
                double[] in = channels(argb);
                double[] out = channels(rgb);
                double light = 0.299 * in[0] + 0.587 * in[1] + 0.114 * in[2];
                n++;
                sl += light;
                sll += light * light;
                for (int c = 0; c < 3; c++) {
                    sx[c] += in[c];
                    sxx[c] += in[c] * in[c];
                    sy[c] += out[c];
                    syy[c] += out[c] * out[c];
                    sxy[c] += in[c] * out[c];
                    sly[c] += light * out[c];
                }
            }
        }
        if (n == 0) return null;
        double[] gain = new double[3], offset = new double[3], greyGain = new double[3], greyOffset = new double[3];
        double error = 0, greyError = 0;
        for (int c = 0; c < 3; c++) {
            double[] own = line(n, sx[c], sxx[c], sy[c], sxy[c]);
            gain[c] = own[0];
            offset[c] = own[1];
            error += residual(n, sx[c], sxx[c], sy[c], syy[c], sxy[c], own);
            double[] lit = line(n, sl, sll, sy[c], sly[c]);
            greyGain[c] = lit[0];
            greyOffset[c] = lit[1];
            greyError += residual(n, sl, sll, sy[c], syy[c], sly[c], lit);
        }
        error = Math.sqrt(Math.max(0, error / 3));
        greyError = Math.sqrt(Math.max(0, greyError / 3));
        Fit grey = new Fit(true, channels3(greyGain), channels3(greyOffset), greyError, null);
        if (greyError < error * 0.8) return grey;
        // A grey cut fits both alike: its own channels win, as a dim, and a learned grey still names it.
        return new Fit(false, channels3(gain), channels3(offset), error, greyError <= error + CLOSE ? grey : null);
    }

    /** {@code [gain, offset]} of the line through the points summed; a flat cut's gain is told by its mean. */
    private static double[] line(double n, double sx, double sxx, double sy, double sxy) {
        double mx = sx / n;
        double my = sy / n;
        double spread = sxx / n - mx * mx;
        if (spread < FLAT) return mx > 0.02 ? new double[]{my / mx, 0} : new double[]{1, my - mx};
        double gain = (sxy / n - mx * my) / spread;
        return new double[]{gain, my - gain * mx};
    }

    /** The mean square of what {@code line} leaves of the points summed. */
    private static double residual(double n, double sx, double sxx, double sy, double syy, double sxy, double[] line) {
        double a = line[0];
        double b = line[1];
        // Σ(y − a·x − b)², expanded over the sums.
        double sum = syy - 2 * a * sxy - 2 * b * sy + a * a * sxx + 2 * a * b * sx + n * b * b;
        return sum / n;
    }

    private static double[] channels(int rgb) {
        return new double[]{((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0};
    }

    private static Shade.Channels channels3(double[] values) {
        return new Shade.Channels(values[0], values[1], values[2]);
    }

    /**
     * {@code picture} as {@code shade} shows it, matched in its place: a tint can drop a match below the bot's
     * confidence, so a run matches the cut tinted too. Made once per picture and shade; empty for a picture whose file
     * cannot be read.
     */
    public static Optional<ImageTemplate> tinted(ImageTemplate picture, Shade shade) {
        String key = stamp(picture) + "|" + picture.place() + "|" + picture.frame() + "|" + shade;
        if (KEPT_PIXELS.get(key) instanceof ImageTemplate kept) return Optional.of(kept);
        Optional<ImageTemplate> made = cut(picture).map(cut -> {
            ImageTemplate copy = ImageTemplate.of(picture.filePath() + "#" + shade.kind().name().toLowerCase(),
                            shaded(cut, shade))
                    .frames(picture.frames());
            copy = picture.place() == null ? copy : copy.at(picture.place());
            return picture.frame() == null ? copy : copy.in(picture.frame());
        });
        made.ifPresent(copy -> KEPT_PIXELS.put(key, copy));
        return made;
    }

    /**
     * The sizes {@code shades}' scales learned, each one more match per picture: a size within its shade's tolerance
     * of the cut's is left out, as it is never read as the scale.
     */
    public static List<Double> sizes(List<Shade> shades) {
        Set<Double> sizes = new TreeSet<>();
        for (Shade shade : shades) {
            if (shade.kind() != Shade.Kind.SCALE) continue;
            for (double size : new double[]{shade.geometry().smallest(), shade.geometry().largest()}) {
                if (Math.abs(size - 1) > shade.tolerance()) sizes.add(size);
            }
        }
        return List.copyOf(sizes);
    }

    /**
     * {@code picture} drawn {@code size} times as big, each frame alike, its place grown around its centre: a scaled
     * button no longer matches its cut at the bot's confidence. Made once per picture and size; empty for a picture
     * whose file cannot be read.
     */
    public static Optional<ImageTemplate> scaled(ImageTemplate picture, double size) {
        String key = stamp(picture) + "|" + picture.place() + "|" + picture.frame() + "|×" + size;
        if (KEPT_PIXELS.get(key) instanceof ImageTemplate kept) return Optional.of(kept);
        Optional<ImageTemplate> made = cut(picture).map(cut -> {
            int frames = Math.max(1, picture.frames());
            int from = cut.getWidth() / frames;
            int width = Math.max(1, (int) Math.round(from * size));
            int height = Math.max(1, (int) Math.round(cut.getHeight() * size));
            BufferedImage out = new BufferedImage(width * frames, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                for (int f = 0; f < frames; f++) {
                    g.drawImage(cut, f * width, 0, (f + 1) * width, height, f * from, 0, (f + 1) * from,
                            cut.getHeight(), null);
                }
            } finally {
                g.dispose();
            }
            ImageTemplate copy = ImageTemplate.of(picture.filePath() + "#x" + size, out).frames(picture.frames());
            Rect place = picture.place();
            copy = place == null ? copy : copy.at(centred(place.x() + place.width() / 2.0,
                    place.y() + place.height() / 2.0, width, height));
            return picture.frame() == null ? copy : copy.in(picture.frame());
        });
        made.ifPresent(copy -> KEPT_PIXELS.put(key, copy));
        return made;
    }

    /**
     * {@link #scaled} placed on {@code found}, a match of {@code picture} on {@code frame}: the button grown or shrunk
     * where it shows, looked for near there only. A copy of its own, which the caller closes.
     */
    public static Optional<ImageTemplate> scaledAround(ImageTemplate picture, double size, Rect found,
                                                       BufferedImage frame) {
        double[] scale = scale(authored(picture), frame);
        // Sized as the copy's own pixels are, so the place is the copy's.
        return scaled(picture, size).map(copy -> copy.at(centred((found.x() + found.width() / 2.0) / scale[0],
                (found.y() + found.height() / 2.0) / scale[1], copy.width() / Math.max(1, copy.frames()),
                copy.height())));
    }

    private static Rect centred(double x, double y, int width, int height) {
        return new Rect((int) Math.round(x - width / 2.0), (int) Math.round(y - height / 2.0), width, height);
    }

    /** {@code picture}'s file as found, with when it was written and its size: a re-cut file is another stamp. */
    private static String stamp(ImageTemplate picture) {
        return TemplateSource.file(picture.filePath(), Path.of("").toAbsolutePath()).map(file -> {
            try {
                return file + "@" + Files.getLastModifiedTime(file).toMillis() + "+" + Files.size(file);
            } catch (IOException gone) {
                return file.toString();
            }
        }).orElse(picture.filePath());
    }

    /** {@code cut} with {@code shade}'s gain and offset applied, its see-through pixels kept. */
    static BufferedImage shaded(BufferedImage cut, Shade shade) {
        BufferedImage out = new BufferedImage(cut.getWidth(), cut.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cut.getHeight(); y++) {
            for (int x = 0; x < cut.getWidth(); x++) {
                int argb = cut.getRGB(x, y);
                double[] in = channels(argb);
                double light = 0.299 * in[0] + 0.587 * in[1] + 0.114 * in[2];
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double from = shade.kind() == Shade.Kind.GREY ? light : in[c];
                    double v = shade.gain().get(c) * from + shade.offset().get(c);
                    rgb = rgb << 8 | (int) Math.round(Math.clamp(v, 0, 1) * 255);
                }
                out.setRGB(x, y, (argb & 0xFF000000) | rgb);
            }
        }
        return out;
    }

    /** The pixels of {@code picture}'s file, empty when it cannot be read (a picture held in memory has none here). */
    private static Optional<BufferedImage> cut(ImageTemplate picture) {
        String key = stamp(picture);
        if (KEPT_PIXELS.get(key) instanceof BufferedImage kept) return Optional.of(kept);
        Mat mat = null;
        try {
            OpenCvNative.ensureLoaded();
            mat = TemplateCache.load(picture.filePath());
            if (mat.empty()) return Optional.empty();
            BufferedImage image = image(mat);
            KEPT_PIXELS.put(key, image);
            return Optional.of(image);
        } catch (RuntimeException | LinkageError unreadable) {
            return Optional.empty();
        } finally {
            if (mat != null) mat.release();
        }
    }

    /** {@code mat}'s pixels, one, three or four channels as decoded (grey, BGR, BGRA), as ARGB. */
    private static BufferedImage image(Mat mat) {
        int channels = mat.channels();
        byte[] data = new byte[(int) mat.total() * channels];
        mat.get(0, 0, data);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), BufferedImage.TYPE_INT_ARGB);
        for (int i = 0, p = 0; p < mat.total(); p++, i += channels) {
            int b = data[i] & 0xFF;
            int g = channels == 1 ? b : data[i + 1] & 0xFF;
            int r = channels == 1 ? b : data[i + 2] & 0xFF;
            int a = channels == 4 ? data[i + 3] & 0xFF : 0xFF;
            image.setRGB(p % mat.cols(), p / mat.cols(), a << 24 | r << 16 | g << 8 | b);
        }
        return image;
    }
}
