package com.botmaker.sdk.api.geometry;

import java.util.Objects;

/**
 * A named spot of the game, in the bot's own pixels: {@code x, y} from the top-left corner of the bot's screen
 * ({@code Sdk.screen()}), wherever the game window sits and whatever size it shows (2026-10-11).
 *
 * <p>{@code Mouse.click(Spots.CLAIM)} finds it at run time: the run's capture source gives the corner, and a game
 * showing another size than the bot's scales it. A game of another shape keeps it with its {@link Anchor}
 * ({@link #anchored}); the centre unless it says otherwise. A {@link Point} is a pixel of the desktop, as a
 * match reports it; a spot is a place of the game.
 */
public final class Spot {

    private final int x;
    private final int y;
    private final Anchor anchor;

    /** The spot {@code x, y} pixels from the top-left of the bot's screen, staying with its centre. */
    public Spot(int x, int y) {
        this(x, y, Anchor.CENTER);
    }

    private Spot(int x, int y, Anchor anchor) {
        this.x = x;
        this.y = y;
        this.anchor = anchor == null ? Anchor.CENTER : anchor;
    }

    /** This spot, staying with {@code anchor} when the game shows another shape than the bot's screen. */
    public Spot anchored(Anchor anchor) {
        return new Spot(x, y, anchor);
    }

    /** Pixels from the left of the bot's screen. */
    public int x() {
        return x;
    }

    /** Pixels from the top of the bot's screen. */
    public int y() {
        return y;
    }

    /** The part of the screen it stays with. */
    public Anchor anchor() {
        return anchor;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Spot other && x == other.x && y == other.y && anchor == other.anchor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, anchor);
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + (anchor == Anchor.CENTER ? "" : " from " + anchor) + "}";
    }
}
