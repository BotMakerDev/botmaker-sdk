package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageTemplate;

/**
 * Where a {@link Node} of a {@link Tree} sits from a node it is connected to — {@code Beside.of(Pictures.DAMAGE, 1.2,
 * -0.6)}: its centre is {@code across} of {@code other}'s widths right of {@code other}'s centre and {@code down} of
 * its heights below it, as both show in the frame. No pixel and no resolution enters (the user's call, 2026-10-17):
 * the sizes {@code other} shows at turn it into a place. One per connected pair, read both ways.
 *
 * @param other  the connected node it is measured from
 * @param across how many of {@code other}'s widths right of it; negative is left
 * @param down   how many of {@code other}'s heights below it; negative is above
 */
public record Beside(ImageTemplate other, double across, double down) {

    /** A node's centre {@code across} of {@code other}'s widths right and {@code down} of its heights below. */
    public static Beside of(ImageTemplate other, double across, double down) {
        return new Beside(other, across, down);
    }

    /** Where this offset puts a place of {@code found}'s size, {@code other} shown at {@code found}. */
    public Rect place(Rect found) {
        int x = (int) Math.round(found.x() + across * found.width());
        int y = (int) Math.round(found.y() + down * found.height());
        return new Rect(x, y, found.width(), found.height());
    }
}
