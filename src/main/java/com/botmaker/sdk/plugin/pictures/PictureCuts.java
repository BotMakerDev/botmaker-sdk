package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.overlay.Area;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.plugin.screen.CutShape;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A picture cut out of a frame the caller already holds, saved and declared as a {@code Pictures} constant.
 * This is what the overlay's Picture tab and the assistant's {@code crop_pictures} do, without ✂ Capture
 * Templates' window.
 *
 * <p>It is the same save the capture tool makes: the PNG, then {@code Pictures.<NAME>} written with where it was
 * cut through {@link TemplateUses#cut}. Links no JavaFX.
 */
public final class PictureCuts {

    private PictureCuts() {}

    /** A saved picture: its name, the path {@code new ImageTemplate("…")} takes, and a note the host left. */
    public record Cut(String name, String path, Optional<String> note) {
    }

    /** A picture cut out of a frame, and where in that frame. */
    public record Piece(BufferedImage image, Rect place) {
    }

    /**
     * The part of {@code frame} under {@code region}, both in desktop pixels, with {@code frameArea} being where
     * the frame sits, and the box it took in the frame's own pixels. Empty when the region misses the frame or has
     * no size.
     */
    public static Optional<Piece> crop(BufferedImage frame, Area frameArea, Area region) {
        if (frame == null || region == null || region.width() <= 0 || region.height() <= 0) return Optional.empty();
        int ox = frameArea == null ? 0 : frameArea.x();
        int oy = frameArea == null ? 0 : frameArea.y();
        int x = Math.max(0, region.x() - ox);
        int y = Math.max(0, region.y() - oy);
        int right = Math.min(frame.getWidth(), region.x() - ox + region.width());
        int bottom = Math.min(frame.getHeight(), region.y() - oy + region.height());
        if (right <= x || bottom <= y) return Optional.empty();
        BufferedImage copy = new BufferedImage(right - x, bottom - y, BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(frame.getSubimage(x, y, right - x, bottom - y), 0, 0, null);
        return Optional.of(new Piece(copy, new Rect(x, y, right - x, bottom - y)));
    }

    /**
     * The part of {@code frame} a cut of {@code shape} takes, all in the frame's pixels: {@code box} for a rectangle and
     * an oval, the polygon through {@code points} for a polygon, cut to its bounding box. What lies outside an oval or
     * a polygon is transparent. Empty when it misses the frame or has no size, or a polygon has fewer than three points.
     */
    public static Optional<Piece> cut(BufferedImage frame, Rect box, CutShape shape, List<Point> points) {
        if (shape == CutShape.POLYGON) return polygon(frame, points);
        if (box == null) return Optional.empty();
        Optional<Piece> piece = crop(frame, null, new Area(box.x(), box.y(), box.width(), box.height()));
        return shape == CutShape.ELLIPSE ? piece.map(PictureCuts::ellipse) : piece;
    }

    /** The box around {@code points}, each point a pixel inside it; empty with none. */
    public static Optional<Rect> bounds(List<Point> points) {
        if (points == null || points.isEmpty()) return Optional.empty();
        int left = points.stream().mapToInt(Point::x).min().orElseThrow();
        int top = points.stream().mapToInt(Point::y).min().orElseThrow();
        int right = points.stream().mapToInt(Point::x).max().orElseThrow();
        int bottom = points.stream().mapToInt(Point::y).max().orElseThrow();
        return Optional.of(new Rect(left, top, right - left + 1, bottom - top + 1));
    }

    /** {@code piece} inside the oval inscribed in its box, transparent outside it. */
    public static Piece ellipse(Piece piece) {
        BufferedImage image = piece.image();
        return new Piece(maskedTo(image, new Ellipse2D.Double(0, 0, image.getWidth(), image.getHeight())), piece.place());
    }

    /**
     * The part of {@code frame} inside the polygon through {@code points}, in the frame's pixels: cut to the polygon's
     * bounding box, transparent outside it. Each point is a pixel's centre, so a point on the box's edge keeps half its
     * pixel. Empty with fewer than three points, or when the box misses the frame.
     */
    public static Optional<Piece> polygon(BufferedImage frame, List<Point> points) {
        if (points == null || points.size() < 3) return Optional.empty();
        Rect box = bounds(points).orElseThrow();
        return crop(frame, null, new Area(box.x(), box.y(), box.width(), box.height())).map(piece -> {
            Path2D.Double outline = new Path2D.Double();
            Rect place = piece.place();
            for (Point p : points) {
                double x = p.x() - place.x() + 0.5, y = p.y() - place.y() + 0.5;
                if (outline.getCurrentPoint() == null) outline.moveTo(x, y);
                else outline.lineTo(x, y);
            }
            outline.closePath();
            return new Piece(maskedTo(piece.image(), outline), place);
        });
    }

    /**
     * {@code image} inside {@code outline}, transparent outside it, with a smooth rim.
     *
     * <p>The outline is painted as an anti-aliased mask and the image drawn into it ({@code SRC_IN}). Clipping to it
     * instead would ignore anti-aliasing — a clip is all or nothing per pixel — and give every round picture a
     * stair-stepped edge the matcher had to forgive.
     */
    private static BufferedImage maskedTo(BufferedImage image, Shape outline) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(outline);
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Size of a square of {@link #checkered}'s board, in pixels. */
    private static final int CHECK = 6;

    /**
     * {@code image} over a grey checkerboard, opaque: what a preview shows, so the part a cut leaves out reads as cut
     * away and an icon the shape clipped is seen.
     */
    public static BufferedImage checkered(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        for (int y = 0; y < out.getHeight(); y += CHECK) {
            for (int x = 0; x < out.getWidth(); x += CHECK) {
                g.setColor((x / CHECK + y / CHECK) % 2 == 0 ? Color.LIGHT_GRAY : Color.GRAY);
                g.fillRect(x, y, CHECK, CHECK);
            }
        }
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    /**
     * {@code piece} with the part under {@code ignore}, in the frame's pixels as its place is, made transparent:
     * the matcher reads a transparent pixel as one to skip, so a part that changes from copy to copy (a timer's
     * digits on a map pin) no longer keeps the copies apart. Unchanged when {@code ignore} misses it.
     */
    public static Piece masked(Piece piece, Rect ignore) {
        Rect place = piece.place();
        int left = Math.max(ignore.x(), place.x()) - place.x();
        int top = Math.max(ignore.y(), place.y()) - place.y();
        int right = Math.min(ignore.x() + ignore.width(), place.x() + place.width()) - place.x();
        int bottom = Math.min(ignore.y() + ignore.height(), place.y() + place.height()) - place.y();
        if (right <= left || bottom <= top) return piece;
        BufferedImage copy = new BufferedImage(place.width(), place.height(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(piece.image(), 0, 0, null);
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) copy.setRGB(x, y, copy.getRGB(x, y) & 0x00FFFFFF);
        }
        return new Piece(copy, place);
    }

    /**
     * Saves {@code piece} as {@code typed} (sanitised the way every picture name is) and declares its constant,
     * cut at its place in {@code frame}. On the FX thread, since declaring writes the bot's Java.
     *
     * @throws IllegalArgumentException with the sentence to show: no name, a name taken or reserved, no project
     */
    public static Cut save(StudioServices services, Piece piece, String typed, BufferedImage frame) {
        Path resources = services == null ? null : services.resourcesDir();
        if (resources == null) throw new IllegalArgumentException("No project is open to save a picture into.");
        String name = TemplateLibrary.sanitizeName(typed);
        if (name.isEmpty()) throw new IllegalArgumentException("A picture needs a name.");
        if (TemplateLibrary.exists(resources, name)) {
            throw new IllegalArgumentException("A picture called \"" + name + "\" already exists.");
        }
        try {
            String path = TemplateLibrary.saveTemplate(resources, piece.image(), name);
            return new Cut(name, path, TemplateUses.cut(services, name,
                    FrameCut.of(frame.getWidth(), frame.getHeight(), piece.place())));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + name + ".png: " + e.getMessage(), e);
        }
    }
}
