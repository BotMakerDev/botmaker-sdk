package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;

import java.awt.Color;

/**
 * The result of a {@link Pixel} colour search: one connected cluster of pixels that matched.
 *
 * <p>Mirrors {@link MatchResult}'s contract — package-private constructors, a {@link #notFound()} sentinel,
 * and {@code null} accessors when {@link #isFound()} is false — so the two result types read the same way.
 *
 * <p>All coordinates are <b>absolute screen coordinates</b> (the search's capture-source origin is already
 * applied), so they can be handed straight to {@code Mouse}.
 */
public class ColorMatch {

    private final Point location;      // top-left of the cluster's bounding box, absolute
    private final int width;
    private final int height;
    private final int pixelCount;      // matched pixels in this cluster
    private final double coverage;     // fraction of the searched region this cluster covers (0..1)
    private final Point centroid;      // centre of mass, absolute
    private final Color color;         // the colour that was searched for
    private final boolean found;

    ColorMatch(Point location, int width, int height, int pixelCount, double coverage,
               Point centroid, Color color) {
        this.location = location;
        this.width = width;
        this.height = height;
        this.pixelCount = pixelCount;
        this.coverage = coverage;
        this.centroid = centroid;
        this.color = color;
        this.found = true;
    }

    private ColorMatch() {
        this.location = new Point(0, 0);
        this.width = 0;
        this.height = 0;
        this.pixelCount = 0;
        this.coverage = 0.0;
        this.centroid = null;
        this.color = null;
        this.found = false;
    }

    static ColorMatch notFound() {
        return new ColorMatch();
    }

    public boolean isFound() {
        return found;
    }

    /** The colour this search was looking for, or {@code null} if not found. */
    public Color getColor() {
        return color;
    }

    /** Number of matched pixels in this cluster — the quantity {@code minPixels} gates on. */
    public int getPixelCount() {
        return pixelCount;
    }

    /** Fraction (0..1) of the searched region covered by matching pixels. */
    public double getCoverage() {
        return coverage;
    }

    /**
     * The cluster's centre of mass — a better click target than the bbox centre for a non-rectangular blob
     * (an L-shape's bbox centre can lie entirely outside the shape). {@code null} if not found.
     */
    public Point getCenter() {
        if (!found) return null;
        return new Point(centroid.x, centroid.y);
    }

    /** Top-left of the cluster's bounding box, or {@code null} if not found. */
    public Point getTopLeft() {
        if (!found) return null;
        return new Point(location.x, location.y);
    }

    /** The cluster's bounding box, or {@code null} if not found. */
    public Rect getBounds() {
        if (!found) return null;
        return new Rect((int) location.x, (int) location.y, width, height);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String toString() {
        if (!found) return "ColorMatch{notFound}";
        return "ColorMatch{color=" + color + ", center=" + getCenter()
                + ", pixels=" + pixelCount + ", coverage=" + String.format("%.4f", coverage) + "}";
    }
}
