package com.botmaker.sdk.api.vision;

/**
 * How far a pixel's colour may sit from the target and still count as a match, as a <b>CIELAB ΔE</b> distance.
 *
 * <p>A type rather than a bare {@code double} for the same reason {@code PlatformId} is a type rather than a
 * bare {@code String}: the number alone says nothing. {@code Pixel.find(RED, 12.0, hud)} gives the reader no
 * way to know whether 12 is strict or loose, what the scale runs to, or that it is a perceptual distance and
 * not a percentage — and nothing stops {@code 0.12} (meant as "12%") from silently becoming a near-exact
 * match. {@code Pixel.find(RED, Tolerance.DEFAULT, hud)} answers all of that in the call itself, and the
 * named constants below give the Studio a set of anchors to lay a slider out against.
 *
 * <p>The scale is perceptually uniform, so one value behaves the same across hues — that is the whole point of
 * measuring in Lab rather than RGB. It is a distance, so it has no upper bound (black to white is ΔE ≈ 100);
 * anything past {@link #LOOSE} is matching most of the colour wheel.
 *
 * <pre>{@code
 * Pixel.find(Color.RED, Tolerance.TIGHT, healthBar);   // this shade of red
 * Pixel.find(Color.RED, Tolerance.of(18), healthBar);  // somewhere between DEFAULT and LOOSE
 * }</pre>
 *
 * @param deltaE the maximum CIELAB ΔE distance from the target colour; never negative
 */
public record Tolerance(double deltaE) {

    /** Only pixels of exactly the target colour (ΔE 0). */
    public static final Tolerance EXACT = new Tolerance(0.0);
    /** The same shade — tolerates little more than compression noise (ΔE ≈ 5). */
    public static final Tolerance TIGHT = new Tolerance(5.0);
    /** The same colour through mild shading / anti-aliasing (ΔE ≈ 12). What the no-tolerance overloads use. */
    public static final Tolerance DEFAULT = new Tolerance(12.0);
    /** The whole colour family — "some kind of red" (ΔE ≈ 25). */
    public static final Tolerance LOOSE = new Tolerance(25.0);

    public Tolerance {
        if (Double.isNaN(deltaE) || deltaE < 0) {
            throw new IllegalArgumentException("tolerance must be a ΔE distance ≥ 0, got: " + deltaE);
        }
    }

    /**
     * A tolerance of {@code deltaE}, for a value between (or beyond) the named constants.
     *
     * @throws IllegalArgumentException if {@code deltaE} is negative or NaN — a bot that asks for a nonsense
     *         threshold should say so at the call, not quietly match everything or nothing
     */
    public static Tolerance of(double deltaE) {
        return new Tolerance(deltaE);
    }

    @Override
    public String toString() {
        return "ΔE " + deltaE;
    }
}
