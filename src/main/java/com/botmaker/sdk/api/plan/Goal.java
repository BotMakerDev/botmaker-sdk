package com.botmaker.sdk.api.plan;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.botmaker.sdk.internal.plan.Readiness;
import com.botmaker.sdk.internal.screens.Conditions;
import com.botmaker.sdk.internal.screens.ScreenMaps;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * What is wanted: a constant holding a check and nothing else, met when its check says yes, whoever made it so.
 *
 * <pre>{@code
 * public static final Goal ENOUGH_GOLD   = Goal.of(Need.atLeast(Progress::gold, 100));
 * public static final Goal EVENT_DONE    = Goal.of(GameLogic::eventFinished);   // true yes, false no, a throw unknown
 * public static final Goal DAILY_CLAIMED = Goal.done(() -> Screens.MARKET.claimDaily).since(Reset.daily(LocalTime.of(5, 0)));
 * public static final Goal FOUGHT_LATELY = Goal.done(() -> Screens.ARENA.fight).within(Duration.ofHours(4));
 * }</pre>
 */
public sealed interface Goal permits Goal.Of, Goal.Done {

    /** What its check says now. */
    Answer answer();

    /**
     * The goal met when {@code check} says yes: a {@link Condition} keeps its three answers; any other method's true is
     * yes, false no, a throw unknown, as {@link Condition#of}. One factory, so an overloaded method can be named.
     */
    static Goal of(BooleanSupplier check) {
        return new Of(check instanceof Condition condition ? condition : Condition.of(check));
    }

    /**
     * The goal met once {@code action} was done and its check held, by whatever did it; {@link Done#within} bounds how
     * long ago, {@link Done#since} from which reset of the game. Named by a supplier, as a screen's field read from
     * outside it is: {@code Goals} and {@code Screens} may name each other.
     */
    static Done done(Supplier<Do> action) {
        return new Done(action, null, null);
    }

    /** {@link #of}. */
    record Of(Condition check) implements Goal {

        public Of {
            Objects.requireNonNull(check, "check");
        }

        @Override
        public Answer answer() {
            return Conditions.answer(check);
        }
    }

    /**
     * {@link #done}: no when its last time did not hold, was {@code within} ago or longer, or came before the last
     * {@code since} reset; unknown for an action no screen of the map holds.
     *
     * @param action the action, read each time it is asked
     * @param within how long its last time counts; {@code null} for always, or until a reset
     * @param since  the reset its last time counts until; {@code null} for none
     */
    record Done(Supplier<Do> action, Duration within, Reset since) implements Goal {

        public Done {
            Objects.requireNonNull(action, "action");
            if (within != null && (within.isNegative() || within.isZero())) {
                throw new IllegalArgumentException("Goal.done(…).within takes a time, not " + within + ".");
            }
            if (within != null && since != null) {
                throw new IllegalArgumentException("Goal.done(…) counts within a time or since a reset, not both.");
            }
        }

        /** Its last time counts for {@code time}, in place of any {@link #since}: a real duration, not a day. */
        @Link
        public Done within(Duration time) {
            return new Done(action, Objects.requireNonNull(time, "time"), null);
        }

        /** Its last time counts until the game's next {@code reset}, in place of any {@link #within}: a daily. */
        @Link
        public Done since(Reset reset) {
            return new Done(action, null, Objects.requireNonNull(reset, "reset"));
        }

        @Override
        public Answer answer() {
            return Conditions.answer((Condition) () -> Readiness.met(this, ScreenMaps.installed(), DoneRecords.run()));
        }
    }
}
