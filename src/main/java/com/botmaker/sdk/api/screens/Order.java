package com.botmaker.sdk.api.screens;

/**
 * Which copy of a {@link Button#repeated repeated button} comes first, for a {@link Pick}: the one furthest toward a
 * side or a corner of the screen, or the one nearest its centre (the old {@code Direction}, 2026-10-16). A side reads
 * row by row (or column by column) as a page does: {@link #NORTH} is reading order, the top row left to right first.
 */
public enum Order {
    /** The top row first, each row left to right: reading order. */
    NORTH,
    /** The bottom row first, each row left to right. */
    SOUTH,
    /** The rightmost column first, each column top to bottom. */
    EAST,
    /** The leftmost column first, each column top to bottom. */
    WEST,
    /** The one furthest up and to the right first (largest x − y). */
    NORTH_EAST,
    /** The one furthest up and to the left first (smallest x + y). */
    NORTH_WEST,
    /** The one furthest down and to the right first (largest x + y). */
    SOUTH_EAST,
    /** The one furthest down and to the left first (smallest x − y). */
    SOUTH_WEST,
    /** The one nearest the centre of the screen first. */
    CENTER
}
