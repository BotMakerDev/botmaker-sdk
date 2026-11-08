package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.screens.Do;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What {@link Planner#round} chose, and why.
 *
 * @param tier       the tier taken, counted from 0; empty when every goal is met or waiting
 * @param route      the actions to do, in the order to do them, each after what it needs
 * @param onTheWay   ready actions of any tier that the route passes by, in the order it does
 * @param doing      the route with each on-the-way action where the route passes it: what a run does, in order
 * @param crossed    the screens the route shows, from where the game is, in order; empty with no route
 * @param considered every action looked at, for each goal in the order it would be taken
 * @param due        when the soonest goal waiting at or above the tier comes due; empty when none is waiting
 */
public record Round(OptionalInt tier, List<Step> route, List<Step> onTheWay, List<Step> doing, List<String> crossed,
                    List<Considered> considered, Optional<Instant> due) {

    public Round {
        route = List.copyOf(route);
        onTheWay = List.copyOf(onTheWay);
        doing = List.copyOf(doing);
        crossed = List.copyOf(crossed);
        considered = List.copyOf(considered);
    }

    /** An action and the field of a screen holding it. */
    public record Step(Do action, String screen, String field) {

        /** Its key in the {@link DoneRecords}. */
        public String key() {
            return DoneRecords.key(screen, field);
        }
    }

    /** One action looked at for {@code goal}, and what was found. */
    public record Considered(Goal goal, Step step, Why why) {}

    /** Why an action was, or was not, a choice. */
    public sealed interface Why {

        /** Nothing holds it back, and everything it asks is known. */
        record Ready() implements Why {}

        /** Nothing known holds it back, and a {@code .last()} action: taken when nothing else can be. */
        record LastResort() implements Why {}

        /** Possible: something it asks answers unknown, which never holds an action back. */
        record Unknown() implements Why {}

        /** Possible after {@code goal}, first of what it needs, by {@code actions} actions before it. */
        record Needs(Goal goal, int actions) implements Why {}

        /** Its cadence, or its retry, or a goal it needs, or the run having found it could not, lets it at {@code until}. */
        record Waiting(Instant until) implements Why {}

        /** Not possible now, for {@code block}. */
        record Blocked(Block block) implements Why {}

        /** Not possible: it needs {@code goal}, and no action can meet that now. */
        record CannotMeet(Goal goal) implements Why {}

        /** Not possible: it needs {@code goal}, a sub-goal deeper than {@link Planner#DEEPEST}. */
        record TooDeep(Goal goal) implements Why {}

        /** Not possible: it needs {@code goal}, which is already being looked at above it, so needs itself. */
        record Loop(Goal goal) implements Why {}
    }

    /** What rules an action out: for now, which a later round looks at again, or for good. */
    public enum Block {
        /** Its input is locked, or its {@code .when} says no: for now. */
        INPUT,
        /** Its input is marked never: for good. */
        NEVER,
        /** Its screen holds no such input, or one a walk cannot press (a text only a body types): for good. */
        NO_INPUT,
        /** A condition it needs says no: for now. */
        CONDITION,
        /** Its {@code .until} says yes: for good. */
        ENDED,
        /** A {@code .once()} action, done: for good. */
        DONE_FOR_GOOD,
        /** Its check said no, and its retry lets it no more: for good. */
        GIVEN_UP,
        /** No way on the map leads to its screen from where the game is now: for now. */
        NO_WAY_THERE;

        /** Whether what blocks it may change while the run goes on, so a later round looks again. */
        public boolean forNow() {
            return this == INPUT || this == CONDITION || this == NO_WAY_THERE;
        }
    }

    /** Whether {@code why} leaves the action a choice. */
    static boolean possible(Why why) {
        return switch (why) {
            case Why.Ready ignored -> true;
            case Why.LastResort ignored -> true;
            case Why.Unknown ignored -> true;
            case Why.Needs ignored -> true;
            case Why.Waiting ignored -> false;
            case Why.Blocked ignored -> false;
            case Why.CannotMeet ignored -> false;
            case Why.TooDeep ignored -> false;
            case Why.Loop ignored -> false;
        };
    }
}
