package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;

/**
 * How the game as it shows compares with the bot's screen ({@link BotScreen#size()}): at another aspect ratio the
 * game's art scales by the smaller ratio, so a picture scales by that one (2026-10-11). An area the bot reads is
 * measured from a picture it shows ({@code api.screens.Area}, 2026-11-09), never placed from the bot's pixels.
 */
public final class Places {

    /** Ratios within this share of each other are one aspect ratio: a window border's rounding is not a shape. */
    private static final double SAME_SHAPE = 0.01;

    private Places() {}

    /**
     * {@code rect} of a frame of size {@code from} on a frame of size {@code to}, keeping its distance, scaled, from the
     * centre: on each axis by its ratio at one shape, by the smaller ratio on both at another. As it is when either
     * size is unknown or they are equal.
     */
    public static Rect scaled(Rect rect, Size from, Size to) {
        if (from == null || to == null || from.empty() || to.empty() || from.equals(to)) return rect;
        double sx = to.width() / (double) from.width();
        double sy = to.height() / (double) from.height();
        if (otherShape(from, to)) sx = sy = Math.min(sx, sy);
        return new Rect((int) Math.round(to.width() / 2.0 + (rect.x() - from.width() / 2.0) * sx),
                (int) Math.round(to.height() / 2.0 + (rect.y() - from.height() / 2.0) * sy),
                (int) Math.round(rect.width() * sx), (int) Math.round(rect.height() * sy));
    }

    /** Whether the two sizes differ in shape: the game's art then scales by the smaller ratio. */
    public static boolean otherShape(Size bot, Size live) {
        if (bot == null || live == null || bot.empty() || live.empty() || bot.equals(live)) return false;
        double sx = live.width() / (double) bot.width();
        double sy = live.height() / (double) bot.height();
        return Math.abs(sx - sy) > SAME_SHAPE * Math.max(sx, sy);
    }
}
