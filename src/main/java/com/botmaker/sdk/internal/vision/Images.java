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
}
