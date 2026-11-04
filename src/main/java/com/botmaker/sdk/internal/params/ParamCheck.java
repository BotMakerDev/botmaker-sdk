package com.botmaker.sdk.internal.params;

import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;

import java.util.Objects;
import java.util.Optional;

/**
 * What {@code Params.when(Progress::gold).atLeast(5)} answers: one test of one parameter's value, read each time it
 * is asked. A value that is unknown, stale, {@code null}, cannot be read or cannot be compared answers unknown, which
 * never closes a way.
 *
 * <p>A value, equal by what it tests, so the editor reads the chain into one and writes it back by the link that
 * rebuilds it equal ({@code ScreenTypes}).
 *
 * @param when  the parameter tested
 * @param test  how its value is tested
 * @param value the limit, or the value wanted; {@code null} for {@link Test#IS_TRUE}
 */
public record ParamCheck(Params.When<?> when, Test test, Object value) implements Condition {

    /** How a parameter's value is tested: one per link of {@code Params.When}. */
    public enum Test { AT_LEAST, AT_MOST, IS, IS_TRUE }

    public ParamCheck {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(test, "test");
    }

    @Override
    public Answer answer() {
        return Condition.of(this::holds).answer();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean holds() {
        Object known = known().orElseThrow(() -> new IllegalStateException("the parameter has no value"));
        return switch (test) {
            case AT_LEAST -> ((Comparable) known).compareTo(value) >= 0;
            case AT_MOST -> ((Comparable) known).compareTo(value) <= 0;
            case IS -> known.equals(value);
            case IS_TRUE -> (Boolean) known;
        };
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> known() {
        return Params.known((Params.Of<Object, Object>) when.parameter());
    }
}
