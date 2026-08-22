package com.botmaker.sdk.api.core;

import com.botmaker.sdk.api.ApiId;

/**
 * Represents a direction for sorting and selecting matches.
 * Used when multiple templates are found and you need to pick based on position.
 */
@ApiId("direction")
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
    WEST
}
