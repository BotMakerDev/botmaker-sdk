package com.botmaker.sdk.api.geometry;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

/**
 * Represents a direction for sorting and selecting matches.
 * Used when multiple templates are found and you need to pick based on position.
 */
// The generated Activities declares a field of this type per direction variable, and parses one back from
// the stored name with Enum.valueOf — so the type is scaffolding, and so is whichever constant is declared
// first, which is the fallback that helper falls back to.
@Palette(category = "geometry", categoryLabel = "Geometry", order = 87)
@Hidden("a value type: an enum constant a bot picks, never a menu entry of its own")
public enum Direction {
    /**
     * Top to bottom (smallest Y to largest Y).
     * First match will be the topmost one.
     */
    NORTH,

    /**
     * Bottom to top (largest Y to smallest Y).
     * First match will be the bottommost one.
     */
    SOUTH,

    /**
     * Left to right (smallest X to largest X).
     * First match will be the leftmost one.
     */
    EAST,

    /**
     * Right to left (largest X to smallest X).
     * First match will be the rightmost one.
     */
    WEST,

    /** Top-right first: the match furthest up and to the right (largest x − y). */
    NORTH_EAST,

    /** Top-left first: the match furthest up and to the left (smallest x + y). */
    NORTH_WEST,

    /** Bottom-right first: the match furthest down and to the right (largest x + y). */
    SOUTH_EAST,

    /** Bottom-left first: the match furthest down and to the left (smallest x − y). */
    SOUTH_WEST,

    /**
     * Centre first: the match closest to the centre of the frame (smallest distance from its middle).
     * Added last, since 2.0.0, so every earlier constant keeps its place.
     */
    CENTER
}
