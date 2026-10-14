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

    /** The folder under {@code src/main/resources} the sets are in. */
    public static final String FOLDER = "digits";
    /** How far one channel strays from the background before a pixel is ink. */
    private static final int INK = 64;

    private DigitCuts() {}

    /** The project-relative folder of the set {@code name}, as {@code new DigitSet("…")} takes it. */
    public static String pathFor(String name) {
        return "src/main/resources/" + FOLDER + "/" + name;
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
     * Saves the digits of {@code shown} out of {@code image}, character {@code i} being {@code boxes.get(i)}, into
     * the set {@code name}, replacing a digit it held; a separator is not saved. The first of a repeated digit is.
     *
     * @return the digits saved
     */
    public static List<Integer> save(Path resources, String name, BufferedImage image, List<Rect> boxes,
                                     String shown) throws IOException {
        Path folder = folder(resources, name);
        Files.createDirectories(folder);
        List<Integer> saved = new ArrayList<>();
        for (int i = 0; i < shown.length(); i++) {
            int digit = Character.digit(shown.charAt(i), 10);
            if (digit < 0 || saved.contains(digit)) continue;
            ImageIO.write(Images.cut(image, boxes.get(i)), "png", folder.resolve(digit + ".png").toFile());
            saved.add(digit);
        }
        return saved;
    }

    /** The digit pictures the set {@code name} holds on disk. */
    public static List<DigitReader.Glyph> held(Path resources, String name) {
        Path folder = folder(resources, name);
        return DigitReader.load(digit -> {
            try {
                Path file = folder.resolve(digit + ".png");
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
