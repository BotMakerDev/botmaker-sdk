package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
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
import java.time.ZonedDateTime;
import java.util.Optional;

/** Whether a {@link Do} may be done now, from what its traits say and its done-record. An unknown never holds one back. */
public final class Readiness {

    private Readiness() {}

    /**
     * It is {@link #due}, {@code input} can be pressed, and nothing it needs says no; {@code input} is its input as a
     * press takes it, with what its strip or tree says of it.
     */
    public static boolean ready(Do action, Input input, Optional<Done> last, Clock clock) {
        if (input.locked() != null || input.never() != null || !Conditions.holds(input.when())) return false;
        for (Goal goal : action.goalNeeds()) {
            if (goal.answer() == Answer.NO) return false;
        }
        for (Condition condition : action.conditionNeeds()) {
            if (!Conditions.holds(condition)) return false;
        }
        return due(action, last, clock);
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
        return Answer.of(goal.within() == null || records.clock().instant().isBefore(last.get().at().plus(goal.within())));
    }

    /** Its {@link Do#until} has not come, and its cadence, or its retry when its check last said no, lets it. */
    public static boolean due(Do action, Optional<Done> last, Clock clock) {
        if (action.until() != null && Conditions.answer(action.until()) == Answer.YES) return false;
        if (last.isEmpty()) return true;
        Done done = last.get();
        Instant now = clock.instant();
        if (done.held()) return cadence(action, done, clock);
        return switch (action.onFail()) {
            case Retry.Never ignored -> false;
            case Retry.Later later -> !now.isBefore(done.at().plus(later.after()));
            // Its tries spent, only its cadence brings it back; one with no cadence is given up.
            case Retry.Again again -> done.fails() <= again.times()
                    || (action.every() != null || action.daily() != null) && cadence(action, done, clock);
        };
    }

    private static boolean cadence(Do action, Done done, Clock clock) {
        Instant now = clock.instant();
        if (action.isOnce()) return false;
        if (action.every() != null) return !now.isBefore(done.at().plus(action.every()));
        if (action.daily() != null) {
            ZonedDateTime today = now.atZone(clock.getZone()).with(action.daily());
            Instant since = (today.toInstant().isAfter(now) ? today.minusDays(1) : today).toInstant();
            return done.at().isBefore(since);
        }
        return true;
    }
}
