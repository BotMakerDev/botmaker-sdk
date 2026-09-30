package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;

import java.awt.Color;

/**
 * The result of a {@link Pixel} colour search: one connected cluster of pixels that matched.
 *
 * <p>Mirrors {@link MatchResult}'s contract — package-private constructors, a {@link #notFound()} sentinel,
 * and {@code null} accessors when {@link #isFound()} is false — so the two result types read the same way.
 *
 * <p>All coordinates are <b>absolute screen coordinates</b> (the search's capture-source origin is already
 * applied), so they can be handed straight to {@code Mouse}.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): all nine are offered, the same verdict and the same
 * reason as {@link MatchResult} — a result type has no rival spellings, only different questions about one
 * finding. {@link #color()} is the only member whose type comes from outside the SDK, and it is offered rather
 * than hidden because {@code java.awt.Color} is precisely the type Studio's own colour picker reads and
 * writes: it is a declarable variable type in the editor, so a menu entry producing one hands back a value the
 * user can name and store. That is the fillable/holdable rule pointing the other way from the usual, and it is
 * why the rule is stated in terms of what the editor can do rather than which package a type came from.
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
    public Color color() {
        return color;
    }

    /** Number of matched pixels in this cluster — the quantity {@code Precision.minArea} gates on. */
    public int pixelCount() {
        return pixelCount;
    }

    /** Fraction (0..1) of the searched region covered by matching pixels. */
    public double coverage() {
        return coverage;
    }

    /**
     * The cluster's centre of mass — a better click target than the bbox centre for a non-rectangular blob
     * (an L-shape's bbox centre can lie entirely outside the shape). {@code null} if not found.
     */
    public Point center() {
        // Point is immutable, so the defensive copy this used to make is no longer needed.
        return found ? centroid : null;
    }

    /** Top-left of the cluster's bounding box, or {@code null} if not found. */
    public Point topLeft() {
        return found ? location : null;
    }

    /** The cluster's bounding box, or {@code null} if not found. */
    public Rect bounds() {
        if (!found) return null;
        return new Rect(location, new Size(width, height));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public String toString() {
        if (!found) return "ColorMatch{notFound}";
        return "ColorMatch{color=" + color + ", center=" + center()
                + ", pixels=" + pixelCount + ", coverage=" + String.format("%.4f", coverage) + "}";
    }
}
