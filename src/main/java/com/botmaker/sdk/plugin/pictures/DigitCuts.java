package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.vision.DigitReader;
import com.botmaker.sdk.internal.vision.Images;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A number on screen cut into its characters, and a game font's digit pictures saved from them: what the
 * assistant's {@code save_digits} does (2026-10-11). Links no JavaFX.
 *
 * <p>A character is a run of columns holding ink, a pixel that strays from the background (the median of the
 * number's border); every character is cut at the whole number's height, plus a pixel of background around it,
 * so each digit is matched with the line it stands in.
 */
public final class DigitCuts {

    /** The folder under the SDK's folder of the resources the sets are in. */
    public static final String FOLDER = "digits";
    /** How far one channel strays from the background before a pixel is ink. */
    private static final int INK = 64;

    private DigitCuts() {}

    /** The folder of the set {@code name}, as {@code new DigitSet("…")} takes it: {@code digits/gold}. */
    public static String pathFor(String name) {
        return FOLDER + "/" + name;
    }

    /** The folder of the set {@code name} on disk. */
    public static Path folder(Path resources, String name) {
        return resources.resolve(FOLDER).resolve(name);
    }

    /** Each character of the number in {@code image}, left to right, as its box in the image. */
    public static List<Rect> characters(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int background = background(image);
        boolean[] inkColumn = new boolean[width];
        int top = height;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ink(image.getRGB(x, y), background)) {
                    inkColumn[x] = true;
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        List<Rect> boxes = new ArrayList<>();
        if (bottom < 0) return boxes;
        int y0 = Math.max(0, top - 1);
        int y1 = Math.min(height - 1, bottom + 1);
        for (int x = 0; x < width; x++) {
            if (!inkColumn[x]) continue;
            int start = x;
            while (x + 1 < width && inkColumn[x + 1]) x++;
            int x0 = Math.max(0, start - 1);
            int x1 = Math.min(width - 1, x + 1);
            boxes.add(new Rect(x0, y0, x1 - x0 + 1, y1 - y0 + 1));
        }
        return boxes;
    }

    /**
     * {@link #characters(BufferedImage)}, with characters that touch split until there are {@code count}: while
     * fewer, the widest box is cut at its column of least ink, between a quarter and three quarters of its width
     * (an outlined font joins {@code 3} and {@code 0}). More than {@code count} are left as found.
     */
    public static List<Rect> characters(BufferedImage image, int count) {
        List<Rect> boxes = new ArrayList<>(characters(image));
        if (boxes.isEmpty()) return boxes;
        int background = background(image);
        while (boxes.size() < count) {
            Rect wide = boxes.stream().max(Comparator.comparingInt(Rect::width)).orElseThrow();
            int from = wide.x() + wide.width() / 4;
            int to = wide.x() + wide.width() * 3 / 4;
            if (to <= from) break;
            int cut = from;
            int least = Integer.MAX_VALUE;
            for (int x = from; x <= to; x++) {
                int inked = 0;
                for (int y = wide.y(); y < wide.y() + wide.height(); y++) {
                    if (ink(image.getRGB(x, y), background)) inked++;
                }
                if (inked < least) {
                    least = inked;
                    cut = x;
                }
            }
            int at = boxes.indexOf(wide);
            boxes.set(at, new Rect(wide.x(), wide.y(), cut - wide.x() + 1, wide.height()));
            boxes.add(at + 1, new Rect(cut, wide.y(), wide.x() + wide.width() - cut, wide.height()));
        }
        return boxes;
    }

    /**
     * Saves the characters of {@code shown} out of {@code image}, character {@code i} being {@code boxes.get(i)},
     * into the set {@code name}, replacing one it held: each digit, and each mark a number is written with
     * ({@link DigitReader#SYMBOLS}); a space or any other character is not saved. The first of a repeated one is.
     *
     * @return the characters saved, a multiplier as its capital
     */
    public static List<Character> save(Path resources, String name, BufferedImage image, List<Rect> boxes,
                                       String shown) throws IOException {
        Path folder = folder(resources, name);
        Files.createDirectories(folder);
        List<Character> saved = new ArrayList<>();
        for (int i = 0; i < shown.length(); i++) {
            char symbol = Character.toUpperCase(shown.charAt(i));
            Optional<String> file = DigitReader.fileOf(symbol);
            if (file.isEmpty() || saved.contains(symbol)) continue;
            ImageIO.write(Images.cut(image, boxes.get(i)), "png", folder.resolve(file.get() + ".png").toFile());
            saved.add(symbol);
        }
        return saved;
    }

    /** The pictures the set {@code name} holds on disk. */
    public static List<DigitReader.Glyph> held(Path resources, String name) {
        Path folder = folder(resources, name);
        return DigitReader.load(stem -> {
            try {
                Path file = folder.resolve(stem + ".png");
                return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
            } catch (IOException unreadable) {
                return Optional.empty();
            }
        });
    }

    /** The median colour of the image's border, channel by channel. */
    private static int background(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<Integer> border = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            border.add(image.getRGB(x, 0));
            border.add(image.getRGB(x, height - 1));
        }
        for (int y = 1; y < height - 1; y++) {
            border.add(image.getRGB(0, y));
            border.add(image.getRGB(width - 1, y));
        }
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int at = shift;
            int[] channel = border.stream().mapToInt(c -> (c >> at) & 0xff).sorted().toArray();
            rgb |= channel[channel.length / 2] << shift;
        }
        return rgb;
    }

    private static boolean ink(int rgb, int background) {
        for (int shift = 16; shift >= 0; shift -= 8) {
            if (Math.abs(((rgb >> shift) & 0xff) - ((background >> shift) & 0xff)) > INK) return true;
        }
        return false;
    }
}
