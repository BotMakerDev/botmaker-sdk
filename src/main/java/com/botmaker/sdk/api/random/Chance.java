package com.botmaker.sdk.api.random;

import com.botmaker.plugin.api.palette.Palette;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random numbers, as a bot block asks for them: a whole number in a range, a coin flip, one of a list, a length
 * of time.
 *
 * <pre>{@code
 * if (Chance.chance(0.2)) visitShop();
 * Mouse.click(new Point(Chance.between(400, 420), Chance.between(300, 310)));
 * Wait.time(Chance.duration(Duration.ofSeconds(2), Duration.ofSeconds(5)));
 * }</pre>
 *
 * <p>Named {@code Chance} so a bot that also imports {@code java.util.Random} has no clash. Every member rolls
 * on {@link ThreadLocalRandom}, so two bot threads never contend for one generator. A random pause is
 * {@code Wait.between(min, max)}, which already logs the length it rolled; it has no second spelling here.
 */
@Palette(category = "util", categoryLabel = "Utilities", icon = "🎲")
public final class Chance {

    private Chance() {}

    /**
     * A whole number from {@code min} to {@code max}, both included. Order is not significant: the smaller of the
     * two is the floor.
     */
    public static int between(int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return (int) ThreadLocalRandom.current().nextLong(lo, (long) hi + 1);
    }

    /**
     * True with probability {@code probability}: {@code 0.25} is true one call in four. Zero or less is never
     * true, one or more always.
     */
    public static boolean chance(double probability) {
        if (probability <= 0) return false;
        if (probability >= 1) return true;
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    /**
     * One of {@code options}, each equally likely: {@code Chance.pick("farm", "fight")}.
     *
     * <p>Text, not a generic {@code List<T>}: a block's run of arguments is drawn per element, and a {@code T}
     * erases to {@code Object}, which no editor draws.
     *
     * @throws IllegalArgumentException when there is no option: returning {@code null} would fail later, far
     *                                  from the cause
     */
    public static String pick(String... options) {
        if (options == null || options.length == 0) throw new IllegalArgumentException("nothing to pick from");
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    /**
     * A length of time from {@code min} to {@code max}, both included, to the millisecond. Order is not
     * significant; a {@code null} bound is the other bound.
     */
    public static Duration duration(Duration min, Duration max) {
        if (min == null || max == null) return min == null ? (max == null ? Duration.ZERO : max) : min;
        long lo = Math.min(min.toMillis(), max.toMillis());
        long hi = Math.max(min.toMillis(), max.toMillis());
        return Duration.ofMillis(lo == hi ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1));
    }
}
