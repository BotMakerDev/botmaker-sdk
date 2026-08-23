package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Scaffolding;

/**
 * How exacting a {@link Pixel} search should be — every knob that decides whether something counts as a match,
 * in one value.
 *
 * <ul>
 *   <li>{@link #deltaE()} — <b>colour precision</b>: how far a pixel's colour may sit from the target, as a
 *       CIELAB ΔE distance. Perceptually uniform, so one value behaves the same across hues (that is the
 *       point of measuring in Lab rather than RGB) and unbounded, since it is a distance — black to white is
 *       ΔE ≈ 100, and anything past {@link #LOOSE} matches most of the colour wheel.</li>
 *   <li>{@link #minArea()} — the smallest connected blob that counts, as an <b>area in pixels</b>. "Is there
 *       one real patch of this colour?" Raising it stops a stray anti-aliased pixel reporting a hit.</li>
 *   <li>{@link #minCount()} — how many matching pixels the search must see in total, clustered or not. "Is
 *       there enough of this colour at all?" A health bar drawn as twenty separate segments answers this and
 *       fails the area test.</li>
 * </ul>
 *
 * <h2>Why one type and not three arguments</h2>
 *
 * <p>Each of these numbers is unreadable on its own — {@code Pixel.find(RED, 12.0, hud, 40, 0)} says nothing
 * about what 12 is measured in, that 40 is an area rather than a width, or what the 0 disables. And they are
 * only meaningful together: an area floor over an unbounded search mostly says "not a speck", which is rarely
 * the question anyone had, while a count with no area floor cannot tell one solid patch from the same pixels
 * sprinkled across the screen. Naming the whole set once, with anchors to start from, is what makes them
 * choosable:
 *
 * <pre>{@code
 * Pixel.find(Color.RED, healthBar, Precision.TIGHT);                        // this shade of red
 * Pixel.find(Color.RED, healthBar, Precision.TIGHT.minArea(400));           // ...as one real 20x20 patch
 * Pixel.find(Color.RED, healthBar, Precision.DEFAULT.minCount(2000));       // ...or just enough of it
 * Pixel.find(Color.RED, healthBar, Precision.of(18).minArea(400).minCount(2000));
 * }</pre>
 *
 * <h2>Not every operation reads every field</h2>
 *
 * <p>{@link Pixel#matchesAt} tests a single pixel and {@link Pixel#coverage} never clusters, so both read only
 * {@link #deltaE()}; {@link Pixel#findInRange} takes a colour band instead of a target colour, so it reads
 * only the two quantity gates. Passing a fuller {@code Precision} to any of them is harmless but has no
 * effect. Each of those methods says so on itself — and the Studio's editor shows only the knobs the call it
 * is attached to actually uses, so the question mostly does not come up while writing a bot by hand.
 *
 * <p>The two quantity gates also do not compose: {@link #minCount()} is measured on every matching pixel
 * <em>before</em> clustering, so a frame can pass it and still yield no blob big enough to keep. It is not
 * "the total area of what comes back".
 *
 * @param deltaE   the maximum CIELAB ΔE distance from the target colour; never negative
 * @param minArea  the minimum cluster area in pixels; always at least 1
 * @param minCount the minimum number of matching pixels in the whole search; 0 for no requirement
 */
// The generated Activities declares one per precision variable, and rebuilds it from the three stored numbers
// — so the three components are part of the scaffold's surface, not only the type name.
@Scaffolding
public record Precision(double deltaE, int minArea, int minCount) {

    /** The area floor the named constants start from: filters out stray anti-aliased pixels. */
    private static final int DEFAULT_MIN_AREA = 4;

    /** Only pixels of exactly the target colour (ΔE 0). */
    public static final Precision EXACT = new Precision(0.0, DEFAULT_MIN_AREA, 0);
    /** The same shade — tolerates little more than compression noise (ΔE ≈ 5). */
    public static final Precision TIGHT = new Precision(5.0, DEFAULT_MIN_AREA, 0);
    /** The same colour through mild shading / anti-aliasing (ΔE ≈ 12). What the short overloads use. */
    public static final Precision DEFAULT = new Precision(12.0, DEFAULT_MIN_AREA, 0);
    /** The whole colour family — "some kind of red" (ΔE ≈ 25). */
    public static final Precision LOOSE = new Precision(25.0, DEFAULT_MIN_AREA, 0);

    @Scaffolding   // the generated Activities' precision(String) helper calls the canonical constructor
    public Precision {
        if (Double.isNaN(deltaE) || deltaE < 0) {
            throw new IllegalArgumentException("tolerance must be a ΔE distance ≥ 0, got: " + deltaE);
        }
        if (minArea < 1) {
            throw new IllegalArgumentException("minArea must be at least 1 pixel, got: " + minArea);
        }
        if (minCount < 0) {
            throw new IllegalArgumentException("minCount cannot be negative, got: " + minCount);
        }
    }

    /**
     * A colour tolerance of {@code deltaE} with the default quantity gates — for a value between (or beyond)
     * the named constants.
     *
     * @throws IllegalArgumentException if {@code deltaE} is negative or NaN. A bot that asks for a nonsense
     *         threshold should say so at the call rather than quietly match everything or nothing
     */
    public static Precision of(double deltaE) {
        return new Precision(deltaE, DEFAULT_MIN_AREA, 0);
    }

    /** All three knobs at once, when none of the anchors is the starting point you want. */
    public static Precision of(double deltaE, int minArea, int minCount) {
        return new Precision(deltaE, minArea, minCount);
    }

    /** This precision at a different colour tolerance. */
    public Precision tolerance(double deltaE) {
        return new Precision(deltaE, minArea, minCount);
    }

    /**
     * This precision requiring a connected blob of at least {@code minArea} pixels.
     *
     * @throws IllegalArgumentException if {@code minArea} is below 1 — zero or negative would ask for a
     *         cluster of nothing, which the cluster search has no way to honour
     */
    public Precision minArea(int minArea) {
        return new Precision(deltaE, minArea, minCount);
    }

    /**
     * This precision requiring {@code minCount} matching pixels in total, however they clump. Pair it with
     * {@link #minArea(int)} of 1 to ask purely "is there enough of this colour", ignoring blob size.
     */
    public Precision minCount(int minCount) {
        return new Precision(deltaE, minArea, minCount);
    }

    /** The side of the square, and roughly the diameter of the circle, {@link #minArea()} covers — for previews. */
    public double equivalentSide() {
        return Math.sqrt(minArea);
    }

    /**
     * Reads out every knob that is doing something, naming the unit of each. The area is the one routinely
     * misread as a width, and a log line carrying only the numbers reintroduces the ambiguity this type
     * exists to remove.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ΔE ").append(deltaE);
        if (minArea > 1) sb.append(", ").append(minArea).append(" px² blob");
        if (minCount > 0) sb.append(", ").append(minCount).append(" px total");
        return sb.toString();
    }
}
