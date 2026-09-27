package com.botmaker.sdk.api.interaction;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Combos pressed one after another, each followed by a wait — {@code Ctrl+A → 100 ms → Ctrl+C} — as one
 * value, for {@link Keyboard#sequence(KeySequence)}.
 *
 * <p>Written {@code KeySequence.of(KeySequence.step(Combo.of(Key.CTRL, Key.A), Duration.ofMillis(100)), …)}.
 * Each {@link Step} is a combo, pressed as {@link Keyboard#combo(Combo)} presses it (its hold included), then
 * the step's {@code after} wait. A zero wait goes straight to the next step. An empty sequence is refused.
 */
@Palette(category = "interaction", categoryLabel = "Interaction", order = 108)
@Hidden("a value type: a sequence a bot passes to Keyboard.sequence, never a menu entry of its own")
public record KeySequence(List<Step> steps) {

    /** One combo, then the time to wait before the next step. */
    public record Step(Combo combo, Duration after) {

        public Step {
            Objects.requireNonNull(combo, "combo");
            Objects.requireNonNull(after, "after");
            if (after.isNegative()) throw new IllegalArgumentException("a step cannot wait " + after);
        }

        /** The combo, then the wait when there is one: {@code Ctrl+A → 100 ms}. */
        @Override
        public String toString() {
            return after.isZero() ? combo.toString() : combo + " → " + after.toMillis() + " ms";
        }
    }

    public KeySequence {
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));   // copyOf refuses a null step
        if (steps.isEmpty()) throw new IllegalArgumentException("a key sequence needs at least one step");
    }

    /** {@code KeySequence.of(step(…), step(…))}. */
    public static KeySequence of(Step... steps) {
        return new KeySequence(List.of(Objects.requireNonNull(steps, "steps")));
    }

    /** One step: {@code combo}, then {@code after}. */
    public static Step step(Combo combo, Duration after) {
        return new Step(combo, after);
    }

    /** The steps joined with arrows: {@code Ctrl+A → 100 ms → Ctrl+C}. */
    @Override
    public String toString() {
        return String.join(" → ", steps.stream().map(Step::toString).toList());
    }
}
