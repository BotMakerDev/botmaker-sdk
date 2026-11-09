package com.botmaker.sdk.api.plan;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * When the game starts a new day: a time of day on the game server's clock. What a daily is counted from, rather than
 * from its last press, so a press at 06:00 and one at 04:59 the next day are two days when the game resets at 05:00.
 *
 * <pre>{@code
 * public static final Goal DAILY_CLAIMED = Goal.done(() -> Screens.MARKET.claimDaily).since(Reset.daily(LocalTime.of(5, 0)));
 * public final Do claimChest = Do.press(chest).daily(Reset.daily(LocalTime.of(5, 0)).in(ZoneId.of("Europe/Paris")));
 * }</pre>
 *
 * @param at   the time of day the game resets at
 * @param zone the zone that time is read in; {@code null} for the game's, the SDK's {@code gameZone} parameter, or this
 *             computer's when that is blank
 */
public record Reset(LocalTime at, ZoneId zone) {

    public Reset {
        Objects.requireNonNull(at, "A reset is a time of day: Reset.daily(LocalTime.of(5, 0)).");
    }

    /** Every day at {@code at}, in the game's zone. */
    public static Reset daily(LocalTime at) {
        return new Reset(at, null);
    }

    /** The same time of day, read in {@code zone} rather than the game's; Studio reads it beside {@link #zone()}. */
    public Reset in(ZoneId zone) {
        return new Reset(at, Objects.requireNonNull(zone, "zone"));
    }
}
