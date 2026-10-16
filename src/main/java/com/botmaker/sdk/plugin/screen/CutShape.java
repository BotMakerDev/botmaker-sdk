package com.botmaker.sdk.plugin.screen;

/**
 * The shape a picture is cut in: a plain box, the oval inscribed in the box, or a free polygon. What lies outside an
 * oval or a polygon is transparent, and the matcher skips a transparent pixel, so a round button or an odd-shaped icon
 * matches over any background. Links no JavaFX: the assistant's {@code crop_pictures} takes it too.
 */
public enum CutShape {
    /** The box as it is. */
    RECT,
    /** The oval inscribed in the box; a square box is a circle. */
    ELLIPSE,
    /** The polygon through points, cut to its bounding box. */
    POLYGON
}
