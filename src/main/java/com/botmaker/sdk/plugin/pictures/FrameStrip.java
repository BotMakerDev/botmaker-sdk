package com.botmaker.sdk.plugin.pictures;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A moving picture's file: its frames side by side, all one size, frame 1 at the left — what
 * {@code new ImageTemplate("images/plant.png").frames(4)} reads. A still picture is a strip of one. Links no JavaFX.
 */
public final class FrameStrip {

    /** The most frames one picture holds. */
    public static final int MOST = 8;

    private FrameStrip() {}

    /**
     * {@code frames} side by side, in order, as one image.
     *
     * @throws IllegalArgumentException with none, or when their sizes differ
     */
    public static BufferedImage join(List<BufferedImage> frames) {
        if (frames == null || frames.isEmpty()) throw new IllegalArgumentException("A strip needs a frame.");
        BufferedImage first = frames.getFirst();
        int width = first.getWidth();
        int height = first.getHeight();
        for (BufferedImage frame : frames) {
            if (frame.getWidth() != width || frame.getHeight() != height) {
                throw new IllegalArgumentException("Every frame is one size: " + width + "×" + height + ", not "
                        + frame.getWidth() + "×" + frame.getHeight() + ".");
            }
        }
        BufferedImage strip = new BufferedImage(width * frames.size(), height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = strip.createGraphics();
        for (int i = 0; i < frames.size(); i++) g.drawImage(frames.get(i), i * width, 0, null);
        g.dispose();
        return strip;
    }

    /**
     * The {@code frames} frames of {@code strip}, each a copy, frame 1 first.
     *
     * @throws IllegalArgumentException when {@code frames} is under 1 or does not divide its width
     */
    public static List<BufferedImage> split(BufferedImage strip, int frames) {
        int width = frameWidth(strip, frames);
        List<BufferedImage> split = new ArrayList<>(frames);
        for (int i = 0; i < frames; i++) split.add(copy(strip, i * width, width));
        return List.copyOf(split);
    }

    /** Frame 1 of {@code strip}: the leftmost, what a still view of the picture shows. */
    public static BufferedImage first(BufferedImage strip, int frames) {
        return frames <= 1 ? strip : copy(strip, 0, frameWidth(strip, frames));
    }

    private static int frameWidth(BufferedImage strip, int frames) {
        if (frames < 1) throw new IllegalArgumentException("A picture has at least one frame, not " + frames + ".");
        if (strip.getWidth() % frames != 0) {
            throw new IllegalArgumentException("A strip " + strip.getWidth() + " px wide does not split into "
                    + frames + " frames.");
        }
        return strip.getWidth() / frames;
    }

    private static BufferedImage copy(BufferedImage strip, int x, int width) {
        BufferedImage frame = new BufferedImage(width, strip.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        g.drawImage(strip.getSubimage(x, 0, width, strip.getHeight()), 0, 0, null);
        g.dispose();
        return frame;
    }
}
