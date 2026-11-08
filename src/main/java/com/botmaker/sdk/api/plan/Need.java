package com.botmaker.sdk.api.plan;

import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.params.ParamCheck.Test;
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
        return test(fact, Test.AT_LEAST, Objects.requireNonNull(limit, "limit"));
    }

    /** Yes when the fact is {@code limit} or less. */
    public static <G, T extends Number & Comparable<T>> Condition atMost(Params.Of<G, T> fact, T limit) {
        return test(fact, Test.AT_MOST, Objects.requireNonNull(limit, "limit"));
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
        return test(fact, Test.IS_TRUE, null);
    }

    /** Yes when the fact is false. */
    public static <G> Condition not(Params.Of<G, Boolean> fact) {
        return Condition.not(is(fact));
    }

    /** Yes when the fact equals {@code wanted}: a number, a text, an enum constant or a time. */
    public static <G, T extends Comparable<? super T>> Condition equalTo(Params.Of<G, T> fact, T wanted) {
        return test(fact, Test.IS, Objects.requireNonNull(wanted, "wanted"));
    }

    /** The test, marked as written by {@code Need} so Studio writes it back so; a fact no parameter names is refused. */
    private static <G, T> Condition test(Params.Of<G, T> fact, Test test, Object value) {
        RunParams.parameterOf(fact);
        return new ParamCheck(Params.when(fact), test, value, true);
    }
}
