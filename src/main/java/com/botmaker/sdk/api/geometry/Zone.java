package com.botmaker.sdk.api.geometry;

import java.util.Objects;

/**
 * A named area of the game, in the bot's own pixels (2026-10-11).
 *
 * <p>{@code source.region(Zones.BAG)} narrows a search to it at run time: placed on the source's whole surface,
 * scaled when the game shows another size than the bot's screen, and kept with its {@link Anchor} when it shows
 * another shape. A {@link Rect} is pixels of a surface as it is now; a zone is a place of the game.
 */
public final class Zone {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Anchor anchor;

    /** The area at {@code x, y} of the bot's screen, {@code width × height} of its pixels, staying with its centre. */
    public Zone(int x, int y, int width, int height) {
        this(x, y, width, height, Anchor.CENTER);
    }

    private Zone(int x, int y, int width, int height, Anchor anchor) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.anchor = anchor == null ? Anchor.CENTER : anchor;
    }

    /** This area, staying with {@code anchor} when the game shows another shape than the bot's screen. */
    public Zone anchored(Anchor anchor) {
        return new Zone(x, y, width, height, anchor);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The part of the screen it stays with. */
    public Anchor anchor() {
        return anchor;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Zone other && x == other.x && y == other.y && width == other.width
                && height == other.height && anchor == other.anchor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height, anchor);
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + ", " + width + "x" + height
                + (anchor == Anchor.CENTER ? "" : " from " + anchor) + "}";
    }
}
