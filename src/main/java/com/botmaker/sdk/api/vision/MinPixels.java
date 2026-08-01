package com.botmaker.sdk.api.vision;

/**
 * The smallest connected blob of matching colour that counts as a hit, measured as an <b>area in pixels</b>.
 *
 * <p>A type rather than a bare {@code int} because the unit is the thing people get wrong. Sitting in a
 * signature as {@code int minPixels} next to a {@code Rect} and a couple of coordinates, it reads as a
 * <em>size</em> — and a user reaching for "at least 20 pixels across" writes {@code 20} and gets a threshold
 * of 20 pixels of area, which is a blob barely 4×5. The name and this javadoc travel with the value now, and
 * the Studio's editor for it draws the area to scale for the same reason.
 *
 * <p>It is location precision, not colour precision — {@link Tolerance} decides which pixels match, this
 * decides how many of them have to be touching before the match is believed. Raising it is what stops a
 * single stray anti-aliased pixel from reporting a hit.
 *
 * <pre>{@code
 * Pixel.find(Color.RED, Tolerance.TIGHT, hud, MinPixels.DEFAULT);   // ignore specks
 * Pixel.find(Color.RED, Tolerance.TIGHT, hud, MinPixels.of(400));   // a real 20×20 patch
 * }</pre>
 *
 * @param pixels the minimum cluster area in pixels; always at least 1
 */
public record MinPixels(int pixels) {

    /** Any cluster at all, down to a single pixel — no location precision. */
    public static final MinPixels ANY = new MinPixels(1);
    /** Filters out stray anti-aliased pixels (4 px of area). What the no-minPixels overloads use. */
    public static final MinPixels DEFAULT = new MinPixels(4);

    public MinPixels {
        if (pixels < 1) {
            throw new IllegalArgumentException("minPixels must be an area of at least 1 pixel, got: " + pixels);
        }
    }

    /**
     * A threshold of {@code pixels} of area.
     *
     * @throws IllegalArgumentException if {@code pixels} is below 1 — zero or negative would mean "accept a
     *         cluster of nothing", which the cluster search has no way to honour
     */
    public static MinPixels of(int pixels) {
        return new MinPixels(pixels);
    }

    /** The side of the square, and roughly the diameter of the circle, this area covers — for previews. */
    public double equivalentSide() {
        return Math.sqrt(pixels);
    }

    @Override
    public String toString() {
        return pixels + " px²";
    }
}
