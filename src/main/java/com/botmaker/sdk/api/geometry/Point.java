package com.botmaker.sdk.api.geometry;


/**
 * A screen coordinate, in pixels, absolute unless a method says otherwise.
 *
 * <p><b>Integers, deliberately.</b> Every producer of a {@code Point} in this API is a pixel — a window
 * origin, a match's top-left, a capture region's corner — and every consumer is an input event, which the
 * native layer can only deliver at a whole pixel. The one place a fraction arises is a midpoint
 * ({@link Rect#getCenter()}, {@link com.botmaker.sdk.api.vision.MatchResult#getCenter()}), and it is rounded
 * there rather than carried through the type: a bot that clicks the centre of a match wants the pixel
 * nearest the centre, and had to round anyway.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): everything is offered — {@code Rect}'s verdict for
 * {@code Rect}'s reason, that a geometry type has no rival spellings to choose between, every member answering
 * a different question about the same two numbers. The annotation is still worth carrying: it is what makes
 * "this type was looked at and nothing was hidden" a recorded fact rather than an omission, which is the whole
 * difference between a curated type and an uncurated one under strict mode.
 *
 * @param x pixels from the left of the coordinate space
 * @param y pixels from the top of the coordinate space
 */
// The generated Activities declares one per point variable, rebuilt from the two stored numbers.
public record Point(int x, int y) {

    /** The origin, {@code (0, 0)} — what a value-typed variable defaults to before it is set. */
    public Point() {
        this(0, 0);
    }

    /** Whether this point falls inside {@code r} (left/top inclusive, right/bottom exclusive). */
    public boolean inside(Rect r) {
        return r.contains(this);
    }

    /** This point moved by {@code dx}, {@code dy}. */
    public Point offset(int dx, int dy) {
        return new Point(x + dx, y + dy);
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + "}";
    }
}
