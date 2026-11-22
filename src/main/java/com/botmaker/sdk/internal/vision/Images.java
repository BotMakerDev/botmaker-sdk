// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.geometry.Rect;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** A frame's area, a part of it as its own image, and an image resized: what a look at a frame cuts and shows. */
public final class Images {

    private Images() {}

    /** The whole of {@code image}, at {@code (0, 0)}. */
    public static Rect bounds(BufferedImage image) {
        return new Rect(0, 0, image.getWidth(), image.getHeight());
    }

    /** The part of {@code frame} under {@code area}, as its own image: a copy, not a view sharing its pixels. */
    public static BufferedImage cut(BufferedImage frame, Rect area) {
        BufferedImage copy = new BufferedImage(area.width(), area.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(frame.getSubimage(area.x(), area.y(), area.width(), area.height()), 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    /** {@code image} resized by {@code scale}, smoothly; at least one pixel each way. */
    public static BufferedImage scaled(BufferedImage image, double scale) {
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return small;
    }

    /**
     * Whether {@code image} shows no detail: its brightness spread less than {@link #FLAT} of the full range, one
     * colour and its grain. A picture cut from a section not shown yet is its background, which matches anywhere.
     */
    public static boolean flat(BufferedImage image) {
        long n = (long) image.getWidth() * image.getHeight();
        if (n == 0) return true;
        double sum = 0;
        double squares = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                double grey = (0.299 * (rgb >> 16 & 0xFF) + 0.587 * (rgb >> 8 & 0xFF) + 0.114 * (rgb & 0xFF)) / 255;
                sum += grey;
                squares += grey * grey;
            }
        }
        double mean = sum / n;
        return Math.sqrt(Math.max(0, squares / n - mean * mean)) < FLAT;
    }

    /** {@link #flat}'s spread: nunu.ai's blank crops spread under 0.02, its least detailed real one 0.11. */
    private static final double FLAT = 0.03;
}
