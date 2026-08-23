package com.botmaker.sdk.api.geometry;

import com.botmaker.sdk.api.meta.Scaffolding;

/**
 * A rectangular region in pixels — a capture region, a match's bounds, a window's frame.
 *
 * <p>{@code x, y} is the top-left corner; the region spans {@code width} to the right and {@code height}
 * down, so the right and bottom edges are <em>exclusive</em>: {@link #contains(Point)} is true for
 * {@code x} and false for {@code x + width}. That is the same convention the capture stack uses, and it is
 * what makes two abutting regions tile without overlapping.
 *
 * @param x      pixels from the left of the coordinate space
 * @param y      pixels from the top of the coordinate space
 * @param width  pixels across
 * @param height pixels down
 */
// The generated Activities declares one per area variable, rebuilt from the four stored numbers.
@Scaffolding
public record Rect(int x, int y, int width, int height) {

    /**
     * Declared only to carry {@link Scaffolding}: the annotation targets a constructor, not a record
     * component, and the generated {@code Activities}' {@code area(String)} helper calls this one.
     */
    @Scaffolding
    public Rect {
    }

    /** An empty region at the origin — what a value-typed variable defaults to before it is set. */
    public Rect() {
        this(0, 0, 0, 0);
    }

    /** The region between two corners, in either order. */
    public Rect(Point a, Point b) {
        this(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                Math.abs(b.x() - a.x()), Math.abs(b.y() - a.y()));
    }

    /** The region of size {@code s} with its top-left at {@code origin}. */
    public Rect(Point origin, Size s) {
        this(origin.x(), origin.y(), s.width(), s.height());
    }

    /** A {@code width × height} region centred on {@code centre}. */
    public static Rect around(Point centre, int width, int height) {
        return new Rect(centre.x() - width / 2, centre.y() - height / 2, width, height);
    }

    public Point getTopLeft() {
        return new Point(x, y);
    }

    public Point getTopRight() {
        return new Point(x + width, y);
    }

    public Point getBottomLeft() {
        return new Point(x, y + height);
    }

    public Point getBottomRight() {
        return new Point(x + width, y + height);
    }

    /** The centre pixel — the midpoint rounded, since a click lands on a whole pixel. */
    public Point getCenter() {
        return new Point(x + width / 2, y + height / 2);
    }

    public Size size() {
        return new Size(width, height);
    }

    /** {@code width × height}. {@code long} because two screen dimensions can exceed an {@code int}. */
    public long area() {
        return (long) width * height;
    }

    /** Whether either dimension is zero or negative, so the region encloses nothing. */
    public boolean empty() {
        return width <= 0 || height <= 0;
    }

    /** Whether {@code p} falls inside, left/top inclusive and right/bottom exclusive. */
    public boolean contains(Point p) {
        return x <= p.x() && p.x() < x + width && y <= p.y() && p.y() < y + height;
    }

    /** Whether the two regions share at least one pixel. */
    public boolean overlaps(Rect other) {
        return x < other.x + other.width
                && x + width > other.x
                && y < other.y + other.height
                && y + height > other.y;
    }

    /** The shared region, or {@code null} when they do not {@linkplain #overlaps overlap}. */
    public Rect intersection(Rect other) {
        if (!overlaps(other)) {
            return null;
        }
        int newX = Math.max(x, other.x);
        int newY = Math.max(y, other.y);
        return new Rect(newX, newY,
                Math.min(x + width, other.x + other.width) - newX,
                Math.min(y + height, other.y + other.height) - newY);
    }

    /** This region grown by {@code amount} on every side. */
    public Rect expand(int amount) {
        return new Rect(x - amount, y - amount, width + 2 * amount, height + 2 * amount);
    }

    /** This region shrunk by {@code amount} on every side. */
    public Rect shrink(int amount) {
        return expand(-amount);
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + ", " + width + "x" + height + "}";
    }
}
