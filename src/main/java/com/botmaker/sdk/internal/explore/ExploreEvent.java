// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;

import java.util.List;
import java.util.Map;

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
            case ReplayLost ignored -> "replay_lost";
            case Parked ignored -> "parked";
            case Reloaded ignored -> "reloaded";
            case Began ignored -> "began";
            case Ended ignored -> "ended";
            case Claude ignored -> "claude";
            case Settled ignored -> "settled";
            case Pictured ignored -> "pictured";
            case Faulted ignored -> "faulted";
            case Restarted ignored -> "restarted";
            case Built ignored -> "built";
            case Building ignored -> "building";
            case SetAside ignored -> "set_aside";
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
            case "replay_lost" -> ReplayLost.class;
            case "parked" -> Parked.class;
            case "reloaded" -> Reloaded.class;
            case "began" -> Began.class;
            case "ended" -> Ended.class;
            case "claude" -> Claude.class;
            case "settled" -> Settled.class;
            case "pictured" -> Pictured.class;
            case "faulted" -> Faulted.class;
            case "restarted" -> Restarted.class;
            case "built" -> Built.class;
            case "building" -> Building.class;
            case "set_aside" -> SetAside.class;
            default -> null;
        };
    }

    /**
     * A run started: what the journal held before it is an earlier run's. {@code dev}: it plays on a {@code -SNAPSHOT}
     * SDK, so it raises and holds Discover faults (false when an older SDK wrote the line).
     */
    record Began(boolean dev, long at) implements ExploreEvent {}

    /**
     * The stop {@code stopId} is a Discover fault, held until the SDK is fixed and reloaded (2026-11-19): {@code source}
     * what found it ({@code claude} for one Claude called), {@code why} one line, {@code evidence} what proves it,
     * {@code sdkVersion} the SDK the run plays on (blank when unknown). Written right after its {@link Stopped}, or
     * later, when Claude calls a plain open stop a fault.
     */
    record Faulted(String stopId, String source, String why, String evidence, String sdkVersion, long at)
            implements ExploreEvent {
        public Faulted {
            source = source == null ? "" : source;
            why = why == null ? "" : why;
            evidence = evidence == null ? "" : evidence;
            sdkVersion = sdkVersion == null ? "" : sdkVersion;
        }
    }

    /**
     * The run, ended while it held the fault {@code faultId}, goes on in a new JVM on the rebuilt SDK with the memory it
     * wrote ({@link ResumeFile}), and replays the fault: the same run, so its history before stays its own (2026-11-19).
     * {@code dev} as {@link Began}'s: the rebuilt SDK's, which says from now on.
     */
    record Restarted(String faultId, boolean dev, long at) implements ExploreEvent {}

    /** The run ended, {@code end} why (null: an end this SDK does not know). */
    record Ended(ExploreLoop.End end, long at) implements ExploreEvent {}

    /**
     * Claude called the {@code explore} tool: {@code call} its action and argument ({@code wait},
     * {@code resume NO_VERDICT-1}). Written by the plugin side; the Explore tab shows the last one.
     */
    record Claude(String call, long at) implements ExploreEvent {}

    /** A screen this run reached for the first time, from {@code from} by {@code by} (both null at the start). */
    record NewScreen(String screen, String from, String by, long at) implements ExploreEvent {}

    /**
     * Nothing about {@code screen} is open any more: every element has its picture, every input is tried and has its
     * verdict. {@code ms} is the screen's own clock: the time the run spent on it since it first reached it.
     */
    record Built(String screen, long ms, long at) implements ExploreEvent {}

    /**
     * The run left {@code screen} with {@code open} still to do on it ({@code "3 untried"}), {@code ms} on its clock.
     */
    record Building(String screen, long ms, List<String> open, long at) implements ExploreEvent {
        public Building {
            open = open == null ? List.of() : List.copyOf(open);
        }
    }

    /**
     * {@code screen}'s clock ran past the screen budget at {@code ms} with {@code open} still to do: the run works the
     * other open screens first, and comes back to it once none is.
     */
    record SetAside(String screen, long ms, List<String> open, long at) implements ExploreEvent {
        public SetAside {
            open = open == null ? List.of() : List.copyOf(open);
        }
    }

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

    /**
     * The run settled the stop {@code stopId} itself, off the screen's tree: {@code answers} say what the bot is to
     * hold. Written just before its {@link Stopped}; the plugin side writes them and resumes the stop, and nobody
     * doing so in time parks it as any other.
     */
    record Settled(String stopId, List<TreeAnswer> answers, long at) implements ExploreEvent {
        public Settled {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }

    /**
     * The pictures a website's screen {@code screen} the map holds lacked, taken as the run reached it: {@code frame}
     * the page as its shot (blank when it has one), {@code inputs} each input's rendered picture by its field, for an
     * element that shows none. The plugin side copies them in; what already has a picture keeps it.
     */
    record Pictured(String screen, String frame, Map<String, String> inputs, long at) implements ExploreEvent {
        public Pictured {
            frame = frame == null ? "" : frame;
            inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        }
    }

    /** The stop {@code stopId} is gone: after a reload, or the game back in front. */
    record Fixed(String stopId, String why, long at) implements ExploreEvent {}

    /**
     * The replay of the fault {@code faultId}, restarted on the rebuilt SDK, can no longer happen ({@code why}): another
     * stop set its place aside first. Its memory is deleted; whether the fix took is not known.
     */
    record ReplayLost(String faultId, String why, long at) implements ExploreEvent {}

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
