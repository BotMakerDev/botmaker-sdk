package com.botmaker.sdk.api.plan;

import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.screens.Conditions;

import java.util.Objects;

/**
 * What is wanted: a constant holding a check and nothing else, met when its check says yes, whoever made it so.
 * {@code Goal.of(Condition.of(Progress::rich))}.
 */
public sealed interface Goal permits Goal.Of {

    /** What its check says now. */
    Answer answer();

    /** The goal met when {@code check} says yes. */
    static Goal of(Condition check) {
        return new Of(Objects.requireNonNull(check, "check"));
    }

    /** {@link #of}. */
    record Of(Condition check) implements Goal {
        @Override
        public Answer answer() {
            return Conditions.answer(check);
        }
    }
}
