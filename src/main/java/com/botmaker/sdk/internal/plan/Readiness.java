package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Reset;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Retry;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.plan.DoneRecords.Done;
import com.botmaker.sdk.internal.screens.Conditions;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** Whether a {@link Do} may be done now, from what its traits say and its done-record. An unknown never holds one back. */
public final class Readiness {

    private Readiness() {}

    /**
     * It is {@link #due}, {@code input} can be pressed, and nothing it needs says no; {@code input} is its input as a
     * press takes it, with what its strip or tree says of it.
     */
    public static boolean ready(Do action, Input input, Optional<Done> last, Clock clock) {
        if (canPress(input) == Answer.NO) return false;
        for (Goal goal : action.goalNeeds()) {
            if (goal.answer() == Answer.NO) return false;
        }
        for (Condition condition : action.conditionNeeds()) {
            if (!Conditions.holds(condition)) return false;
        }
        return due(action, last, clock);
    }

    /** Whether {@code input} can be pressed: no when locked or marked never, else what its {@code .when} says. */
    public static Answer canPress(Input input) {
        if (input.locked() != null || input.never() != null) return Answer.NO;
        return Conditions.answer(input.when());
    }

    /**
     * What {@link Goal#done} says of {@code map}'s action from {@code records}: yes when its last time held, and is
     * younger than the goal's time if it has one; unknown for an action no screen of {@code map} holds.
     */
    public static Answer met(Goal.Done goal, ScreenGraph map, DoneRecords records) {
        Optional<BotConstants.InputName> name = map.nameOf(goal.action().get());
        if (name.isEmpty()) return Answer.UNKNOWN;
        Optional<Done> last = records.last(DoneRecords.key(name.get().screen(), name.get().field()));
        if (last.isEmpty() || !last.get().held()) return Answer.NO;
        Instant now = records.clock().instant();
        if (goal.since() != null) return Answer.of(!last.get().at().isBefore(lastReset(goal.since(), now, records.clock())));
        return Answer.of(goal.within() == null || now.isBefore(last.get().at().plus(goal.within())));
    }

    /**
     * The latest time {@code reset} came at or before {@code now}: its time of day on the game's calendar, in its own
     * zone or else {@code clock}'s, the game's ({@link #zoneOf}). A time a clock change skips is moved on by the gap's
     * length, as {@code ZonedDateTime} does: 02:30 on a day that jumps from 02:00 to 03:00 is 03:30.
     */
    public static Instant lastReset(Reset reset, Instant now, Clock clock) {
        ZoneId zone = zoneOf(reset, clock);
        LocalDate day = now.atZone(zone).toLocalDate();
        Instant today = day.atTime(reset.at()).atZone(zone).toInstant();
        return today.isAfter(now) ? day.minusDays(1).atTime(reset.at()).atZone(zone).toInstant() : today;
    }

    /** The first time {@code reset} comes after {@code after}: {@link #lastReset}'s next one. */
    public static Instant nextReset(Reset reset, Instant after, Clock clock) {
        ZoneId zone = zoneOf(reset, clock);
        LocalDate day = lastReset(reset, after, clock).atZone(zone).toLocalDate().plusDays(1);
        return day.atTime(reset.at()).atZone(zone).toInstant();
    }

    /** The zone {@code reset} is read in: its own, else {@code clock}'s, which a run sets to the game server's. */
    public static ZoneId zoneOf(Reset reset, Clock clock) {
        return reset.zone() != null ? reset.zone() : clock.getZone();
    }

    /** Its {@link Do#until} has not come, and its cadence, or its retry when its check last said no, lets it. */
    public static boolean due(Do action, Optional<Done> last, Clock clock) {
        if (action.until() != null && Conditions.answer(action.until()) == Answer.YES) return false;
        return dueAt(action, last, clock).filter(at -> !at.isAfter(clock.instant())).isPresent();
    }

    /**
     * When its cadence, or its retry when its check last said no, next lets it: a time passed when it may be done now;
     * empty when it is done for good or given up.
     */
    public static Optional<Instant> dueAt(Do action, Optional<Done> last, Clock clock) {
        if (last.isEmpty()) return Optional.of(clock.instant());
        Done done = last.get();
        if (done.held()) return cadence(action, done, clock);
        return switch (action.onFail()) {
            case Retry.Never ignored -> Optional.empty();
            case Retry.Later later -> Optional.of(done.at().plus(later.after()));
            // Its tries spent, only its cadence brings it back; one with no cadence is given up.
            case Retry.Again again -> done.fails() <= again.times() ? Optional.of(clock.instant())
                    : action.every() != null || action.daily() != null ? cadence(action, done, clock) : Optional.empty();
        };
    }

    private static Optional<Instant> cadence(Do action, Done done, Clock clock) {
        if (action.isOnce()) return Optional.empty();
        if (action.every() != null) return Optional.of(done.at().plus(action.every()));
        if (action.daily() != null) return Optional.of(nextReset(action.daily(), done.at(), clock));
        return Optional.of(clock.instant());
    }
}
