// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * What one {@link Agent} of the latest run is doing, read off the journal alone ({@link JournalView#agentSummary}):
 * the Explore page's card for it, which works for a run of any client, live or finished.
 *
 * @param agent the agent
 * @param state what it is doing now
 * @param stopId the stop it is on, or {@code ""} for none
 * @param frame  the frame it is looking at, a file as its record names it ({@link Stop#framePath}), or {@code ""}
 * @param last   its latest record that says something; empty before it wrote one
 * @param calls  how many times it acted: presses for the Explorer, leases handed for the Coordinator, sessions for a
 *               worker
 * @param time   how long it worked: the run's time for the Explorer and the Coordinator, its sessions' for a worker
 */
public record AgentSummary(Agent agent, State state, String stopId, String frame, Optional<ExploreEvent> last,
                           int calls, Duration time) {

    public AgentSummary {
        agent = agent == null ? Agent.UNKNOWN : agent;
        state = state == null ? State.UNKNOWN : state;
        stopId = stopId == null ? "" : stopId;
        frame = frame == null ? "" : frame;
        last = Objects.requireNonNullElse(last, Optional.empty());
        time = time == null ? Duration.ZERO : time;
    }

    /**
     * This card as the run's end leaves it: a run killed before it wrote its end still reads as running, and the
     * page that knows its process is gone says it ended. An agent not in the run stays so.
     */
    public AgentSummary ended() {
        return state == State.ABSENT || state == State.ENDED ? this
                : new AgentSummary(agent, State.ENDED, "", frame, last, calls, time);
    }

    /** What an agent is doing now. */
    public enum State {
        /** On a stop or a press. */
        WORKING("working", "Working"),
        /** The run waits on another: the Explorer on a stop. */
        WAITING("waiting", "Waiting"),
        /** Nothing is its to do just now. */
        IDLE("idle", "Idle"),
        /** The Coordinator's heartbeat is older than its limit. */
        SILENT("silent", "Silent"),
        /** Not in this run: a run without a goal has no crew. */
        ABSENT("absent", "Not in this run"),
        /** The run ended. */
        ENDED("ended", "Ended"),
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        State(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** The state whose {@link #id()} is {@code id}, case aside; {@link #UNKNOWN} for any other. */
        public static State fromId(String id) {
            for (State state : values()) {
                if (state.id.equalsIgnoreCase(id == null ? "" : id.strip())) return state;
            }
            return UNKNOWN;
        }
    }
}
