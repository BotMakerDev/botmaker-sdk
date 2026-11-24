// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import java.util.List;

/**
 * One member of the explore crew as the Explore page shows it, one card each (spec 2026-11-24-explore-agents-design §
 * The Explore page): the run's own code and the Coordinator beside the LLM workers. {@link #UNKNOWN} is none of them.
 */
public enum Agent {
    /** The run itself: it presses, walks, finds screens and stops. */
    EXPLORER("explorer", "Explorer"),
    /** Reads a stop's frame, ranks its presses and writes the map's edits: the Cartographer's stops. */
    READER("reader", "Reader"),
    /** Judges the run's progress toward its goal. */
    EVALUATOR("evaluator", "Evaluator"),
    /** Hands each stop to its worker, and keeps the run from waiting on nobody. */
    COORDINATOR("coordinator", "Coordinator"),
    /** Fixes Discover's own faults in a lane of the SDK's checkout. */
    MECHANIC("mechanic", "Mechanic"),
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    Agent(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The agents the page shows a card for, in its order. */
    public static final List<Agent> CREW = List.of(EXPLORER, READER, EVALUATOR, COORDINATOR, MECHANIC);

    /** Who works a stop of {@code role}: the run's own side for the Settler, nobody of the crew for the user. */
    public static Agent of(StopRole role) {
        return switch (role == null ? StopRole.UNKNOWN : role) {
            case SETTLER -> EXPLORER;
            case CARTOGRAPHER -> READER;
            case EVALUATOR -> EVALUATOR;
            case MECHANIC -> MECHANIC;
            case USER, UNKNOWN -> UNKNOWN;
        };
    }

    /** The agent whose {@link #id()} is {@code id}, case aside; {@link #UNKNOWN} for any other. */
    public static Agent fromId(String id) {
        for (Agent agent : values()) {
            if (agent.id.equalsIgnoreCase(id == null ? "" : id.strip())) return agent;
        }
        return UNKNOWN;
    }
}
