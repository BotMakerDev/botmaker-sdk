package com.botmaker.sdk.api.plan;

import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.params.RunParams;

import java.util.Objects;

/**
 * What a goal or an action needs of a fact, named by its method: {@code Goal.of(Need.atLeast(Progress::gold, 100))}. A
 * limit of another type than the fact's does not compile: a {@code long} fact takes {@code 100L}. A fact unknown or
 * stale answers unknown, and each call refuses what is no parameter at once, a lambda too, which could never answer.
 *
 * <p>For a goal or an action's needs. An input's {@code .when} takes {@code Params.when}, which Studio's map reads.
 */
public final class Need {

    private Need() {}

    /** Yes when the fact is {@code limit} or more. */
    public static <G, T extends Number & Comparable<T>> Condition atLeast(Params.Of<G, T> fact, T limit) {
        return when(fact).atLeast(Objects.requireNonNull(limit, "limit"));
    }

    /** Yes when the fact is {@code limit} or less. */
    public static <G, T extends Number & Comparable<T>> Condition atMost(Params.Of<G, T> fact, T limit) {
        return when(fact).atMost(Objects.requireNonNull(limit, "limit"));
    }

    /** Yes when the fact is more than {@code limit}. */
    public static <G, T extends Number & Comparable<T>> Condition above(Params.Of<G, T> fact, T limit) {
        return Condition.not(atMost(fact, limit));
    }

    /** Yes when the fact is less than {@code limit}. */
    public static <G, T extends Number & Comparable<T>> Condition below(Params.Of<G, T> fact, T limit) {
        return Condition.not(atLeast(fact, limit));
    }

    /** Yes when the fact is true. */
    public static <G> Condition is(Params.Of<G, Boolean> fact) {
        return when(fact).isTrue();
    }

    /** Yes when the fact is false. */
    public static <G> Condition not(Params.Of<G, Boolean> fact) {
        return Condition.not(is(fact));
    }

    /** Yes when the fact equals {@code wanted}: a number, a text, an enum constant or a time. */
    public static <G, T extends Comparable<? super T>> Condition equalTo(Params.Of<G, T> fact, T wanted) {
        return when(fact).is(Objects.requireNonNull(wanted, "wanted"));
    }

    private static <G, T> Params.When<T> when(Params.Of<G, T> fact) {
        RunParams.parameterOf(fact);
        return Params.when(fact);
    }
}
