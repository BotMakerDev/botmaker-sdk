package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.vision.TemplateCache;
import com.botmaker.sdk.internal.vision.TemplateSource;
import com.botmaker.shared.opencv.OpenCvNative;
import org.opencv.core.Mat;

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
import java.util.Set;

/**
 * How a button's usual picture shows where it matched, told by colour: the shown pixels fitted against the cut's by
 * least squares over its opaque pixels, {@code shown = gain × cut + offset} in each channel, or each channel from the
 * cut's light for a greyed one. The fit is checked against the bot's learned {@link Shade}s; none fits, the look's own
 * state holds (2026-11-02: no threshold is built in, a bot with no shade sees no computed version).
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
    /** How many cuts and tinted copies are kept, the least used dropped first. */
    private static final int KEPT = 64;

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
            return new Shade(kind(), round(gain), round(offset), Shade.TOLERANCE, cause, state);
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
        return cut(picture).flatMap(cut -> fit(cut, picture.frames(), frame, found))
                .flatMap(fit -> closest(fit, shades, causes));
    }

    /**
     * Of {@code shades} of one of {@code causes}, the one {@code fit} names and is closest to; a hover or a press
     * first, so the button under the cursor never reads as the game's dim its hover looks like. Focus is weighed with
     * the game's by distance: it lasts until the next press, and the game may dim the button just pressed.
     */
    static Optional<Shade> closest(Fit fit, List<Shade> shades, Set<Shade.Cause> causes) {
        Shade best = null;
        for (Shade shade : shades) {
            if (!causes.contains(shade.cause()) || !fit.fits(shade)) continue;
            if (best == null || rank(shade) < rank(best)
                    || rank(shade) == rank(best) && fit.distance(shade) < fit.distance(best)) {
                best = shade;
            }
        }
        return Optional.ofNullable(best);
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
            Fit one = fit(cut, f * width, width, height, frame, found);
            if (one != null && (best == null || one.error() < best.error())) best = one;
        }
        return Optional.ofNullable(best);
    }

    /** {@link #fit} of the frame of {@code cut} starting at {@code x0}; null with nothing to fit. */
    private static Fit fit(BufferedImage cut, int x0, int width, int height, BufferedImage frame, Rect found) {
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
                if (fx < 0 || fx >= frame.getWidth()) continue;
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
