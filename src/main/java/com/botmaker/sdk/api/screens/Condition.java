package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.internal.screens.Conditions;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Why a way, an input or a variant is there now, answered in three: {@link Answer#YES}, {@link Answer#NO} or
 * {@link Answer#UNKNOWN}. It is a {@link BooleanSupplier}, so every {@code .when(…)} takes one, and holds unless it
 * says no: an unknown never closes a way.
 *
 * <pre>{@code
 * .when(Condition.of(GameLogic::eventOn))                       // true yes, false no, a throw unknown
 * .when(Condition.all(Condition.of(GameLogic::eventOn), Condition.not(Condition.of(GameLogic::bagFull))))
 * }</pre>
 *
 * <p>All, any and not follow three-valued logic: all says no when one says no, else unknown when one is unknown;
 * any says yes when one says yes, else unknown when one is unknown; not of unknown is unknown. Nor and nand are not of
 * any and of all; xor and xnor answer only once every one has, since one unknown could change the count.
 */
@FunctionalInterface
public interface Condition extends BooleanSupplier {

    /** What it says now. */
    Answer answer();

    /** Whether it holds: anything but {@link Answer#NO}. */
    @Override
    default boolean getAsBoolean() {
        return answer() != Answer.NO;
    }

    /** {@code method} asked: true {@link Answer#YES}, false {@link Answer#NO}, a throw {@link Answer#UNKNOWN}. */
    static Condition of(BooleanSupplier method) {
        return new Of(Objects.requireNonNull(method, "method"));
    }

    /** Yes when every one says yes; no when one says no; else unknown. */
    static Condition all(Condition... conditions) {
        return new All(List.of(conditions));
    }

    /** Yes when one says yes; no when every one says no; else unknown. */
    static Condition any(Condition... conditions) {
        return new Any(List.of(conditions));
    }

    /** Yes when every one says no: {@code not(any(…))}. */
    static Condition nor(Condition... conditions) {
        return new Not(new Any(List.of(conditions)));
    }

    /** No when every one says yes: {@code not(all(…))}. */
    static Condition nand(Condition... conditions) {
        return new Not(new All(List.of(conditions)));
    }

    /** Yes when an odd number say yes, two given: one or the other, not both; unknown when one is unknown. */
    static Condition xor(Condition... conditions) {
        return new Xor(List.of(conditions));
    }

    /** Yes when an even number say yes, two given: both or neither: {@code not(xor(…))}. */
    static Condition xnor(Condition... conditions) {
        return new Not(new Xor(List.of(conditions)));
    }

    /** The other answer; unknown stays unknown. */
    static Condition not(Condition condition) {
        return new Not(condition);
    }

    /** {@link #of}: a bot method asked; a condition given keeps its own three answers. A stop still ends the run. */
    record Of(BooleanSupplier method) implements Condition {
        @Override
        public Answer answer() {
            return Conditions.answer(method);
        }
    }

    /** {@link #all}. */
    record All(List<Condition> conditions) implements Condition {
        public All {
            conditions = List.copyOf(conditions);
        }

        @Override
        public Answer answer() {
            Answer said = Answer.YES;
            for (Condition c : conditions) {
                Answer a = Conditions.answer(c);   // one that throws is unknown, whatever its place
                if (a == Answer.NO) return Answer.NO;
                if (a == Answer.UNKNOWN) said = Answer.UNKNOWN;
            }
            return said;
        }
    }

    /** {@link #any}. */
    record Any(List<Condition> conditions) implements Condition {
        public Any {
            conditions = List.copyOf(conditions);
        }

        @Override
        public Answer answer() {
            Answer said = Answer.NO;
            for (Condition c : conditions) {
                Answer a = Conditions.answer(c);
                if (a == Answer.YES) return Answer.YES;
                if (a == Answer.UNKNOWN) said = Answer.UNKNOWN;
            }
            return said;
        }
    }

    /** {@link #xor}: only every answer known tells how many say yes. */
    record Xor(List<Condition> conditions) implements Condition {
        public Xor {
            conditions = List.copyOf(conditions);
        }

        @Override
        public Answer answer() {
            boolean odd = false;
            for (Condition c : conditions) {
                Answer a = Conditions.answer(c);
                if (a == Answer.UNKNOWN) return Answer.UNKNOWN;
                if (a == Answer.YES) odd = !odd;
            }
            return Answer.of(odd);
        }
    }

    /** {@link #not}. */
    record Not(Condition condition) implements Condition {
        @Override
        public Answer answer() {
            return switch (Conditions.answer(condition)) {
                case YES -> Answer.NO;
                case NO -> Answer.YES;
                case UNKNOWN -> Answer.UNKNOWN;
            };
        }
    }
}
