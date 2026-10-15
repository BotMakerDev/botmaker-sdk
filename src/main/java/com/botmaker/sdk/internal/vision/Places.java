package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;

import java.util.Optional;

/**
 * Where a {@link Zone}, written in the bot's pixels, is on the game as it shows now, and back
 * (2026-10-11).
 *
 * <p>The bot's screen is {@link BotScreen#size()}; the game is the run's capture source, whole ({@code base()}).
 * At the same aspect ratio a place scales on each axis. At another one the game's art scales by the smaller ratio,
 * and a place keeps its distance, scaled, from its {@link Anchor}'s point of the screen: a corner, an edge's middle
 * or the centre. A bot that names no screen size has nothing to scale against, so its places are the source's
 * pixels as they are.
 */
public final class Places {

    /** Ratios within this share of each other are one aspect ratio: a window border's rounding is not a shape. */
    private static final double SAME_SHAPE = 0.01;

    private Places() {}

    /** {@code zone} in the pixels of {@code source}'s whole surface, as {@code region(Rect)} takes them. */
    public static Rect inSource(Zone zone, CaptureSource source) {
        return inSource(zone, BotScreen.size().orElse(null), liveSize(whole(source)).orElse(null));
    }

    /** {@code zone} in the pixels of a surface showing {@code live}, for a bot whose screen is {@code bot}. */
    public static Rect inSource(Zone zone, Size bot, Size live) {
        if (!scales(bot, live)) return new Rect(zone.x(), zone.y(), zone.width(), zone.height());
        Scale scale = Scale.of(bot, live);
        return new Rect(scale.x(zone.x(), across(zone.anchor())), scale.y(zone.y(), down(zone.anchor())),
                (int) Math.round(zone.width() * scale.sx), (int) Math.round(zone.height() * scale.sy));
    }

    /**
     * The area of the bot's screen that shows as {@code area} of a surface showing {@code live}: what
     * {@code save_region} writes for a box of a screenshot. The way back from {@link #inSource(Zone, Size, Size)}.
     */
    public static Zone inBot(Rect area, Anchor anchor, Size bot, Size live) {
        Zone zone = new Zone(area.x(), area.y(), area.width(), area.height()).anchored(anchor);
        if (!scales(bot, live)) return zone;
        Scale scale = Scale.of(bot, live);
        return new Zone(scale.backX(area.x(), across(zone.anchor())), scale.backY(area.y(), down(zone.anchor())),
                (int) Math.round(area.width() / scale.sx), (int) Math.round(area.height() / scale.sy))
                .anchored(zone.anchor());
    }

    /** Whether the two sizes differ in shape: scaling then keeps a place with its anchor. */
    public static boolean otherShape(Size bot, Size live) {
        if (!scales(bot, live)) return false;
        double sx = live.width() / (double) bot.width();
        double sy = live.height() / (double) bot.height();
        return Math.abs(sx - sy) > SAME_SHAPE * Math.max(sx, sy);
    }

    private static boolean scales(Size bot, Size live) {
        return bot != null && live != null && !bot.empty() && !live.empty() && !bot.equals(live);
    }

    private static CaptureSource whole(CaptureSource source) {
        return source == null ? null : source.base();
    }

    /** The size the surface shows now; measured only when the bot names a size to scale against. */
    private static Optional<Size> liveSize(CaptureSource whole) {
        if (whole == null || BotScreen.size().isEmpty()) return Optional.empty();
        return BotScreen.measure(whole);
    }

    /** How far across the screen {@code anchor}'s point is: 0 the left edge, ½ the middle, 1 the right edge. */
    private static double across(Anchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> 0;
            case TOP, CENTER, BOTTOM -> 0.5;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> 1;
        };
    }

    /** How far down the screen {@code anchor}'s point is: 0 the top, ½ the middle, 1 the bottom. */
    private static double down(Anchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, TOP, TOP_RIGHT -> 0;
            case LEFT, CENTER, RIGHT -> 0.5;
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> 1;
        };
    }

    /** The two sizes and the scale on each axis: each ratio at one shape, the smaller on both at another. */
    private record Scale(Size bot, Size live, double sx, double sy) {

        static Scale of(Size bot, Size live) {
            double sx = live.width() / (double) bot.width();
            double sy = live.height() / (double) bot.height();
            if (otherShape(bot, live)) {
                double s = Math.min(sx, sy);
                return new Scale(bot, live, s, s);
            }
            return new Scale(bot, live, sx, sy);
        }

        int x(int x, double anchor) {
            return (int) Math.round(anchor * live.width() + (x - anchor * bot.width()) * sx);
        }

        int y(int y, double anchor) {
            return (int) Math.round(anchor * live.height() + (y - anchor * bot.height()) * sy);
        }

        int backX(int x, double anchor) {
            return (int) Math.round(anchor * bot.width() + (x - anchor * live.width()) / sx);
        }

        int backY(int y, double anchor) {
            return (int) Math.round(anchor * bot.height() + (y - anchor * live.height()) / sy);
        }
    }
}
