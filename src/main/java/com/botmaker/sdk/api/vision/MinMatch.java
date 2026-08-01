package com.botmaker.sdk.api.vision;

/**
 * How much of a colour has to be there before it counts — two thresholds that answer two different questions.
 *
 * <ul>
 *   <li>{@link #area()} — the smallest connected blob that counts, as an <b>area in pixels</b>. "Is there one
 *       real patch of this colour?" Raising it is what stops a stray anti-aliased pixel reporting a hit.</li>
 *   <li>{@link #count()} — how many matching pixels the search has to see in total, clustered or not. "Is
 *       there enough of this colour at all?" A health bar drawn as twenty separate segments answers this and
 *       fails the area test.</li>
 * </ul>
 *
 * <p><b>They travel together because neither is much use alone.</b> An area floor on an unbounded search
 * mostly says "not a speck", which is rarely the question anyone had; a count with no area floor cannot tell
 * one solid patch from the same number of pixels sprinkled over the screen. Bundling them into one value
 * makes it impossible to set one and silently leave the other at a default that was never considered — the
 * shape the earlier {@code MinPixels} had, which is why it read as useless on its own.
 *
 * <p>They are also not composable, and the difference shows at the boundary: {@code count} is measured on
 * every matching pixel <em>before</em> clustering, so a frame can pass it and still yield no blob big enough
 * to keep. {@code count} is not "the total area of what comes back".
 *
 * <pre>{@code
 * Pixel.find(Color.RED, Tolerance.TIGHT, hud);                        // MinMatch.DEFAULT — ignore specks
 * Pixel.find(Color.RED, Tolerance.TIGHT, hud, MinMatch.area(400));    // one real 20x20 patch
 * Pixel.find(Color.RED, Tolerance.TIGHT, hud, MinMatch.count(2000));  // enough red, however it clumps
 * Pixel.find(Color.RED, Tolerance.TIGHT, hud, MinMatch.of(400, 2000));// both
 * }</pre>
 *
 * @param area  the minimum cluster area in pixels; always at least 1
 * @param count the minimum number of matching pixels in the whole search; 0 for no requirement
 */
public record MinMatch(int area, int count) {

    /** Filters out stray anti-aliased pixels (4 px of area) and requires no particular total. */
    public static final MinMatch DEFAULT = new MinMatch(4, 0);
    /** Any cluster at all, down to a single pixel, with no total required — no location precision. */
    public static final MinMatch ANY = new MinMatch(1, 0);

    public MinMatch {
        if (area < 1) {
            throw new IllegalArgumentException("area must be at least 1 pixel, got: " + area);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative, got: " + count);
        }
    }

    /** Requires one connected blob of at least {@code area} pixels, with no total requirement. */
    public static MinMatch area(int area) {
        return new MinMatch(area, 0);
    }

    /**
     * Requires {@code count} matching pixels in total and accepts any blob size — the "is this colour on
     * screen at all, in quantity" test, deliberately blind to how the pixels clump.
     */
    public static MinMatch count(int count) {
        return new MinMatch(1, count);
    }

    /** Requires both: a blob of at least {@code area} pixels, in a search matching at least {@code count}. */
    public static MinMatch of(int area, int count) {
        return new MinMatch(area, count);
    }

    /** The side of the square, and roughly the diameter of the circle, {@link #area()} covers — for previews. */
    public double equivalentSide() {
        return Math.sqrt(area);
    }

    /**
     * Reads out both thresholds, naming the unit of each — the area is the one routinely misread as a width,
     * and seeing "400 px² blob" beside "2000 px total" is what separates them at a glance in a log.
     */
    @Override
    public String toString() {
        return count == 0 ? area + " px² blob" : area + " px² blob, " + count + " px total";
    }
}
