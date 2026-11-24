// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.StopRole;

import java.util.List;
import java.util.Optional;

/**
 * An LLM worker of the explore crew (spec 2026-11-24-explore-agents-design § Roles): started by the Coordinator for one
 * stop, bounded by its lease, its answer the journal records it writes through the explore tools. Each role names the
 * model it runs on, since a worker started with no setting sources has no model of the user's (spike 2026-11-24 §3),
 * and the MCP tools it may call.
 */
public enum WorkerRole {
    /** Reads a stop's frame and ranks its presses toward the goal; does the Cartographer's map work too. */
    READER("reader", "Reader", "sonnet", List.of("botmaker_sdk_explore", "botmaker_sdk_read_map")),
    /** Judges the run's progress toward the goal, and grades the map at the end. */
    EVALUATOR("evaluator", "Evaluator", "sonnet", List.of("botmaker_sdk_explore", "botmaker_sdk_read_map")),
    /** Fixes Discover's own faults in a worktree lane ({@link MechanicLane}): no tool of the bot's, only the lane. */
    MECHANIC("mechanic", "Mechanic", "opus", List.of()),
    /** A role this SDK does not know; no stop is handed to it. */
    UNKNOWN("unknown", "Unknown", "sonnet", List.of());

    private final String id;
    private final String displayName;
    private final String model;
    private final List<String> tools;

    WorkerRole(String id, String displayName, String model, List<String> tools) {
        this.id = id;
        this.displayName = displayName;
        this.model = model;
        this.tools = tools;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The model the worker runs on, as the client names it: always given. */
    public String model() {
        return model;
    }

    /** The MCP tools of the bot the worker may call, by their names on the server. */
    public List<String> tools() {
        return tools;
    }

    /** The worker that takes a stop of {@code role}; empty for a role no worker takes (the Settler, the user). */
    public static Optional<WorkerRole> of(StopRole role) {
        return switch (role == null ? StopRole.UNKNOWN : role) {
            case CARTOGRAPHER -> Optional.of(READER);
            case MECHANIC -> Optional.of(MECHANIC);
            case EVALUATOR -> Optional.of(EVALUATOR);
            case SETTLER, USER, UNKNOWN -> Optional.empty();
        };
    }

    /** The role whose {@link #id()} is {@code id}, case aside; {@link #UNKNOWN} for any other. */
    public static WorkerRole fromId(String id) {
        for (WorkerRole role : values()) {
            if (role.id.equalsIgnoreCase(id == null ? "" : id.strip())) return role;
        }
        return UNKNOWN;
    }
}
