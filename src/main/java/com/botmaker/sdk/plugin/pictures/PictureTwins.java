package com.botmaker.sdk.plugin.pictures;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which pictures are the same picture, or nearly: two cuts of one button, a recut a few pixels off, the same
 * icon saved under two names. Exact copies are {@link TemplateLibrary#duplicatePictures}'s; this also finds the
 * ones a box drawn twice makes, which never hash alike.
 *
 * <p>Two pictures are twins when their sizes are close and, laid one over the other at the best shift, their
 * overlap differs by at most {@link #MAX_DIFFERENCE} per colour channel on average. Large pictures are compared
 * shrunk, so a whole-screen region costs what a button does.
 */
public final class PictureTwins {

    /** How far a side may differ, as a share of the longer one, before two pictures are not one cut. */
    static final double SIZE_SLACK = 0.15;
    /** A shift may leave this share of the smaller picture outside the overlap, on each axis. */
    static final double SHIFT_SLACK = 0.15;
    /** The average difference per channel (0–255) still read as the same picture. */
    static final double MAX_DIFFERENCE = 8;
    /** The longer side pictures are shrunk to before they are compared. */
    static final int COMPARED_SIDE = 48;

    private PictureTwins() {}

    /**
     * The groups of twins among {@code pictures} (name → image), each of two or more names, sorted; a picture with
     * no twin is in no group. A null image has no opinion.
     */
    public static List<List<String>> groups(Map<String, BufferedImage> pictures) {
        List<String> names = pictures.entrySet().stream().filter(e -> e.getValue() != null).map(Map.Entry::getKey)
                .sorted().toList();
        Map<String, String> parent = new HashMap<>();
        names.forEach(name -> parent.put(name, name));
        Map<String, Shrunk> shrunk = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String a = names.get(i);
                String b = names.get(j);
                if (root(parent, a).equals(root(parent, b))) continue;
                if (twins(pictures.get(a), pictures.get(b), shrunk, a, b)) parent.put(root(parent, b), root(parent, a));
            }
        }
        Map<String, List<String>> byRoot = new LinkedHashMap<>();
        for (String name : names) byRoot.computeIfAbsent(root(parent, name), r -> new ArrayList<>()).add(name);
        return byRoot.values().stream().filter(group -> group.size() > 1).map(List::copyOf)
                .sorted(Comparator.comparing(List::getFirst)).toList();
    }

    /** Whether {@code a} and {@code b} are one picture, give or take a few pixels of the box around it. */
    public static boolean twins(BufferedImage a, BufferedImage b) {
        return twins(a, b, new HashMap<>(), "a", "b");
    }

    private static boolean twins(BufferedImage a, BufferedImage b, Map<String, Shrunk> cache, String nameA,
                                 String nameB) {
        if (!close(a.getWidth(), b.getWidth()) || !close(a.getHeight(), b.getHeight())) return false;
        int longest = Math.max(Math.max(a.getWidth(), a.getHeight()), Math.max(b.getWidth(), b.getHeight()));
        int factor = Math.max(1, (longest + COMPARED_SIDE - 1) / COMPARED_SIDE);
        Shrunk sa = cache.computeIfAbsent(nameA + "@" + factor, k -> Shrunk.of(a, factor));
        Shrunk sb = cache.computeIfAbsent(nameB + "@" + factor, k -> Shrunk.of(b, factor));
        // Loose: the means take in the border a recut adds or drops, which the overlap leaves out.
        if (Math.abs(sa.mean - sb.mean) > 3 * MAX_DIFFERENCE) return false;
        return bestDifference(sa, sb) <= MAX_DIFFERENCE;
    }

    private static boolean close(int x, int y) {
        return Math.abs(x - y) <= Math.max(2, SIZE_SLACK * Math.max(x, y));
    }

    /** The least average channel difference over every shift that keeps most of both pictures in the overlap. */
    static double bestDifference(Shrunk a, Shrunk b) {
        int minW = Math.min(a.width, b.width);
        int minH = Math.min(a.height, b.height);
        int needW = Math.max(1, (int) Math.ceil(minW * (1 - SHIFT_SLACK)));
        int needH = Math.max(1, (int) Math.ceil(minH * (1 - SHIFT_SLACK)));
        double best = Double.MAX_VALUE;
        // b placed at (dx, dy) in a's pixels; the overlap is where both have a pixel.
        for (int dy = needH - b.height; dy <= a.height - needH; dy++) {
            int y0 = Math.max(0, dy);
            int y1 = Math.min(a.height, dy + b.height);
            if (y1 - y0 < needH) continue;
            for (int dx = needW - b.width; dx <= a.width - needW; dx++) {
                int x0 = Math.max(0, dx);
                int x1 = Math.min(a.width, dx + b.width);
                if (x1 - x0 < needW) continue;
                best = Math.min(best, difference(a, b, dx, dy, x0, x1, y0, y1, best));
            }
        }
        return best;
    }

    /**
     * The average channel difference over the overlap; stops early once it can neither beat {@code beat} nor be a
     * twin's.
     */
    private static double difference(Shrunk a, Shrunk b, int dx, int dy, int x0, int x1, int y0, int y1,
                                     double beat) {
        long samples = 3L * (x1 - x0) * (y1 - y0);
        double limit = Math.min(beat, MAX_DIFFERENCE) * samples;
        long sum = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int p = a.rgb[y * a.width + x];
                int q = b.rgb[(y - dy) * b.width + (x - dx)];
                sum += Math.abs((p >> 16 & 0xff) - (q >> 16 & 0xff)) + Math.abs((p >> 8 & 0xff) - (q >> 8 & 0xff))
                        + Math.abs((p & 0xff) - (q & 0xff));
            }
            if (sum > limit) return Double.MAX_VALUE;
        }
        return (double) sum / samples;
    }

    private static String root(Map<String, String> parent, String name) {
        String at = name;
        while (!parent.get(at).equals(at)) at = parent.get(at);
        parent.put(name, at);
        return at;
    }

    /** A picture averaged down by a whole factor, its pixels as packed RGB, and its mean channel value. */
    record Shrunk(int width, int height, int[] rgb, double mean) {

        static Shrunk of(BufferedImage image, int factor) {
            int w = Math.max(1, image.getWidth() / factor);
            int h = Math.max(1, image.getHeight() / factor);
            int[] rgb = new int[w * h];
            long total = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    long r = 0;
                    long g = 0;
                    long bl = 0;
                    int n = 0;
                    for (int sy = y * factor; sy < Math.min(image.getHeight(), (y + 1) * factor); sy++) {
                        for (int sx = x * factor; sx < Math.min(image.getWidth(), (x + 1) * factor); sx++) {
                            int p = image.getRGB(sx, sy);
                            int alpha = p >>> 24;   // over black: a cleared part is not the colour under it
                            r += (p >> 16 & 0xff) * alpha / 255;
                            g += (p >> 8 & 0xff) * alpha / 255;
                            bl += (p & 0xff) * alpha / 255;
                            n++;
                        }
                    }
                    int pixel = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
                    rgb[y * w + x] = pixel;
                    total += r / n + g / n + bl / n;
                }
            }
            return new Shrunk(w, h, rgb, total / (3.0 * w * h));
        }
    }
}
