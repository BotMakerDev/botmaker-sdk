package com.botmaker.sdk.api.geometry;

/**
 * Which part of the screen a {@link Spot} or a {@link Zone} stays with when the game shows another shape than the
 * bot's screen (2026-10-11).
 *
 * <p>At the bot's own aspect ratio every anchor gives the same place: the whole picture scales. At another one — a
 * 16:9 bot on a 16:10 window — the game's art scales by the smaller ratio and the leftover space goes to the
 * edges, so a place on the game's centre stays with the {@link #CENTER} (the default) while a health bar in a
 * corner stays with its corner.
 */
public enum Anchor {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, CENTER, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
}
