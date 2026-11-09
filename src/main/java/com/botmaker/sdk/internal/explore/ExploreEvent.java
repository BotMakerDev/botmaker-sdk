package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.internal.screens.MapWalk.Arrival;

import java.util.List;

/**
 * One line of a run's explore journal ({@link Journal}): what the run found, pressed, refused, stopped on, and how
 * each stop ended. Every event carries {@code at}, epoch milliseconds.
 */
public sealed interface ExploreEvent {

    long at();

    /** The name an event's line carries as its {@code type}. */
    default String type() {
        return switch (this) {
            case NewScreen ignored -> "new_screen";
            case Tried ignored -> "tried";
            case Refused ignored -> "refused";
            case GoalMet ignored -> "goal_met";
            case Stopped ignored -> "stopped";
            case Fixed ignored -> "fixed";
            case Parked ignored -> "parked";
            case Reloaded ignored -> "reloaded";
        };
    }

    /** The event class whose {@link #type()} is {@code type}, or null for one this SDK does not know. */
    static Class<? extends ExploreEvent> typed(String type) {
        return switch (type == null ? "" : type) {
            case "new_screen" -> NewScreen.class;
            case "tried" -> Tried.class;
            case "refused" -> Refused.class;
            case "goal_met" -> GoalMet.class;
            case "stopped" -> Stopped.class;
            case "fixed" -> Fixed.class;
            case "parked" -> Parked.class;
            case "reloaded" -> Reloaded.class;
            default -> null;
        };
    }

    /** A screen this run reached for the first time, from {@code from} by {@code by} (both null at the start). */
    record NewScreen(String screen, String from, String by, long at) implements ExploreEvent {}

    /**
     * A press: on {@code screen}, by {@code by}, chosen in {@code mode}, arrived as {@code arrival} (null when a newer
     * SDK wrote one this one does not know).
     */
    record Tried(String screen, String by, ExploreLoop.Mode mode, Arrival arrival, long at) implements ExploreEvent {}

    /** A press a guard refused, {@code guard} its kind (null: one this SDK does not know) and {@code why} what it saw. */
    record Refused(String screen, String by, Guards.Kind guard, String why, long at) implements ExploreEvent {}

    /** A goal of the plan that came to be met. */
    record GoalMet(String goal, long at) implements ExploreEvent {}

    /**
     * The run stopped on {@code stop} and waits for Claude. {@code again}: the stop Claude just resumed came back at
     * once, the fix did not take.
     */
    record Stopped(Stop stop, boolean again, long at) implements ExploreEvent {}

    /** The stop {@code stopId} is gone: after a reload, or the game back in front. */
    record Fixed(String stopId, String why, long at) implements ExploreEvent {}

    /** Nobody took up the stop {@code stopId} in time: it is in the queue, its place avoided, the run goes on. */
    record Parked(String stopId, long at) implements ExploreEvent {}

    /**
     * A resume of {@code stopId}: the bot compiled and loaded as {@code generation}, or, {@code errors} not empty,
     * refused and the old generation kept.
     */
    record Reloaded(String stopId, int generation, List<String> errors, long at) implements ExploreEvent {
        public Reloaded {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public boolean refused() {
            return !errors.isEmpty();
        }
    }
}
