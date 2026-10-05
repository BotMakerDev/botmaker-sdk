package com.botmaker.sdk.plugin.editors;

import com.botmaker.shared.opencv.ColorMatcher;

import java.awt.Color;
import java.util.List;

/**
 * Teaching a tolerance by example: pixels that should match and pixels that should not, in; the
 * smallest ΔE that takes every good one, out, with the bad ones it cannot keep out named. Uses
 * {@link ColorMatcher#deltaE}, the metric the bot's search thresholds on, so the number taught is the number
 * that works. Pure: no JavaFX, no frame.
 */
final class ToleranceTeacher {

    /** Added to the farthest good pin so a pixel one shade further still matches. */
    static final double MARGIN = 0.5;

    record Conflict(Color color, double distance) {}

    record Lesson(double deltaE, List<Conflict> conflicts) {}

    private ToleranceTeacher() {}

    /**
     * The tolerance {@code good} asks for — the farthest good pin plus {@link #MARGIN}, rounded to 0.1, the
     * margin giving way to a bad pin just beyond it so the two stay separable — and every bad pin within that
     * tolerance. With no good pins nothing is asked: {@code current} stands and nothing conflicts, because a
     * bad pin inside a tolerance nobody taught says only "lower the slider", not "these cannot be told apart".
     */
    static Lesson teach(Color target, List<Color> good, List<Color> bad, double current) {
        if (good.isEmpty()) return new Lesson(current, List.of());
        double farthest = good.stream().mapToDouble(c -> ColorMatcher.deltaE(target, c)).max().orElse(0);
        double deltaE = Math.round((farthest + MARGIN) * 10) / 10.0;
        double takesEveryGood = Math.ceil(farthest * 10) / 10.0;
        double nearestBadBeyond = bad.stream().mapToDouble(c -> ColorMatcher.deltaE(target, c))
                .filter(d -> d > farthest).min().orElse(Double.MAX_VALUE);
        if (nearestBadBeyond <= deltaE) {
            double below = Math.floor(nearestBadBeyond * 10) / 10.0;
            if (below >= nearestBadBeyond) below -= 0.1;
            if (below >= takesEveryGood) deltaE = Math.round(below * 10) / 10.0;
        }
        double taught = deltaE;
        List<Conflict> conflicts = bad.stream()
                .map(c -> new Conflict(c, ColorMatcher.deltaE(target, c)))
                .filter(c -> c.distance() <= taught)
                .toList();
        return new Lesson(taught, conflicts);
    }

    /**
     * The darkest and lightest colours {@code deltaE} still accepts, straight along the lightness axis: the
     * target with its CIELAB L* moved by ±deltaE, back to sRGB and clamped. What "ΔE 12" looks like, shown
     * rather than said.
     */
    static Color[] boundary(Color target, double deltaE) {
        double[] lab = toLab(target);
        return new Color[]{
                fromLab(Math.max(0, lab[0] - deltaE), lab[1], lab[2]),
                fromLab(Math.min(100, lab[0] + deltaE), lab[1], lab[2])};
    }

    // sRGB (D65) <-> CIELAB, the textbook conversion. ColorMatcher's own is private and one-way.

    private static double[] toLab(Color c) {
        double r = linear(c.getRed()), g = linear(c.getGreen()), b = linear(c.getBlue());
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static Color fromLab(double l, double a, double bStar) {
        double fy = (l + 16) / 116, fx = fy + a / 500, fz = fy - bStar / 200;
        double x = 0.95047 * inverse(fx), y = inverse(fy), z = 1.08883 * inverse(fz);
        double r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
        double g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
        double b = 0.0557 * x - 0.2040 * y + 1.0570 * z;
        return new Color(gamma(r), gamma(g), gamma(b));
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int gamma(double linear) {
        double c = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.clamp(c, 0, 1) * 255);
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    private static double inverse(double t) {
        double cube = t * t * t;
        return cube > 216.0 / 24389 ? cube : (116 * t - 16) / (24389.0 / 27);
    }
}
