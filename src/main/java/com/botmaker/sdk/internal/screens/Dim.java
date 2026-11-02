package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.vision.TemplateCache;
import com.botmaker.shared.opencv.OpenCvNative;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;

/**
 * Whether a button shows dimmed or greyed out, told from its usual picture alone: the matcher scores a darkened copy
 * as well as the button itself, so what differs is its light and its colour. Sword x Staff's tutorial dims every
 * button but the one it points at to 0.45 of its light, its colour kept (2026-11-02); a greyed button loses its colour.
 */
public final class Dim {

    /** Light below this share of the cut's: dimmed. A glow that fades stays above it. */
    static final double DARKER = 0.7;
    /** Colour below this share of the cut's: greyed. */
    static final double PALER = 0.5;
    /** A cut with less colour than this has none to lose: told by its light only. */
    static final double SOME_COLOUR = 0.15;
    /** A cut darker than this is never judged: there is no light to lose either. */
    static final double SOME_LIGHT = 0.08;

    private Dim() {}

    /**
     * Whether the picture in {@code file} shows dimmed or greyed where it matched {@code frame}, at {@code found}; a
     * file that cannot be read is never dimmed.
     */
    public static boolean shows(String file, Rect found, BufferedImage frame) {
        Mat cut = null;
        try {
            OpenCvNative.ensureLoaded();
            cut = TemplateCache.load(file);
            return !cut.empty() && dimmed(Tone.of(image(cut), null), Tone.of(frame, found));
        } catch (RuntimeException unreadable) {
            return false;
        } finally {
            if (cut != null) cut.release();
        }
    }

    /**
     * How light and how colourful a picture is, each 0 to 1: the mean luminance, and the mean saturation (how far
     * its brightest channel stands above its dimmest, as a share of the brightest), so darkening keeps the colour.
     */
    record Tone(double light, double colour) {

        /** The tone of {@code image} in {@code area} (null: all of it, cut to the image), its see-through pixels left out. */
        static Tone of(BufferedImage image, Rect area) {
            int x0 = area == null ? 0 : Math.max(0, area.x());
            int y0 = area == null ? 0 : Math.max(0, area.y());
            int x1 = area == null ? image.getWidth() : Math.min(image.getWidth(), area.x() + area.width());
            int y1 = area == null ? image.getHeight() : Math.min(image.getHeight(), area.y() + area.height());
            boolean alpha = image.getColorModel().hasAlpha();
            long light = 0;
            long colour = 0;
            long count = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int argb = image.getRGB(x, y);
                    if (alpha && (argb >>> 24) < 128) continue;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    // Whole numbers summed, so equal pixels give equal tones however many there are.
                    light += 299L * r + 587L * g + 114L * b;
                    colour += max == 0 ? 0 : (max - min) * 1000L / max;
                    count++;
                }
            }
            if (count == 0) return new Tone(0, 0);
            return new Tone(light / (255_000.0 * count), colour / (1000.0 * count));
        }
    }

    /** Whether {@code shown}, where a button's usual picture matched, is that picture dimmed or greyed out. */
    static boolean dimmed(Tone cut, Tone shown) {
        if (cut.light() < SOME_LIGHT) return false;
        if (shown.light() < cut.light() * DARKER) return true;
        return cut.colour() >= SOME_COLOUR && shown.colour() < cut.colour() * PALER;
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
