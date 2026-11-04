package com.botmaker.sdk.api.params;

import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.params.GameFacts;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.params.RunParams;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * What a run does with a parameter of the bot, named by its method (2026-11-04).
 *
 * <pre>{@code
 * Params.set(Progress::level, 5);                                 // kept in params/, seen at once
 * int level = Params.get(Progress::level);                        // what this run wrote, else the method's value
 * Optional<Integer> known = Params.known(Progress::level);        // a game fact: empty while unknown or stale
 * Shop.buy.when(Params.when(Progress::level).atLeast(5));
 * }</pre>
 *
 * <p>Only a parameter marked {@code @Param.Shown(changes = Param.Changes.DURING_RUN)} can be set or forgotten. Calling
 * the method directly reads the value this run was compiled with, so a run reads what it may have set through
 * {@link #get} or {@link #known}.
 */
public final class Params {

    private Params() {}

    /** A parameter, written as its method reference: {@code Progress::level}. A lambda is refused. */
    @FunctionalInterface
    public interface Of<G, T> extends Function<G, T>, Serializable {}

    /**
     * Makes {@code value} the parameter's value: this run sees it at once, and it is written into the bot's
     * {@code params/} folder for the next run, with the time. A value that cannot be written (no source beside the
     * run, a type the plugins cannot spell) stays in memory for this run, with one warning.
     *
     * @throws IllegalArgumentException for a parameter that changes outside a run, or a lambda
     */
    public static <G, T> void set(Of<G, T> parameter, T value) {
        RunParams.set(parameter, value);
    }

    /** The parameter's value: what this run set, else what its method returns. Unknown or stale reads the same. */
    public static <G, T> T get(Of<G, T> parameter) {
        return RunParams.get(parameter, parameter);
    }

    /**
     * The parameter's value if it is known. A fact of a {@link GameState} group is unknown until a run writes it, unless
     * {@link Assumed}, and again once its time is past ({@link StaleAfter}); any other parameter answers its value.
     */
    public static <G, T> Optional<T> known(Of<G, T> parameter) {
        return RunParams.known(parameter, parameter);
    }

    /**
     * Sets the parameter back to its starting value, without a time. A {@link GameState} fact is unknown again for the
     * rest of this run; {@link #get} answers the value the run was compiled with until the next run.
     *
     * @throws IllegalArgumentException for a parameter that changes outside a run, or a lambda
     */
    public static <G, T> void forget(Of<G, T> parameter) {
        RunParams.forget(parameter);
    }

    /**
     * Goes to the screen a {@link ReadOn} fact is read on and answers it there, read unless it was known and young
     * enough; empty when the way or the read fails. Coming back is the caller's. A parameter with no {@link ReadOn}
     * goes nowhere and answers {@link #known}.
     */
    public static <G, T> Optional<T> refresh(Of<G, T> parameter) {
        return GameFacts.refresh(parameter, parameter);
    }

    /** Starts a condition on the parameter, for a {@code .when(…)}. */
    public static <G, T> When<T> when(Of<G, T> parameter) {
        return new When<>(Objects.requireNonNull(parameter, "parameter"));
    }

    /**
     * A condition on one parameter's value, started: each link finishes one, read each time it is asked. A value that
     * is unknown, stale, {@code null}, cannot be read or cannot be compared answers unknown, which never closes a way.
     * Equal by the parameter it names.
     */
    public static final class When<T> {

        private final Of<?, T> parameter;

        private When(Of<?, T> parameter) {
            this.parameter = parameter;
        }

        /** The parameter this condition tests. */
        public Of<?, T> parameter() {
            return parameter;
        }

        /** Holds when the value is {@code limit} or above. */
        public Condition atLeast(T limit) {
            return new ParamCheck(this, ParamCheck.Test.AT_LEAST, limit);
        }

        /** Holds when the value is {@code limit} or below. */
        public Condition atMost(T limit) {
            return new ParamCheck(this, ParamCheck.Test.AT_MOST, limit);
        }

        /** Holds when the value equals {@code wanted}. */
        public Condition is(T wanted) {
            return new ParamCheck(this, ParamCheck.Test.IS, wanted);
        }

        /** Holds when the value is {@code true}. */
        public Condition isTrue() {
            return new ParamCheck(this, ParamCheck.Test.IS_TRUE, null);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof When<?> when && parameter.equals(when.parameter);
        }

        @Override
        public int hashCode() {
            return parameter.hashCode();
        }
    }
}
