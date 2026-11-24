// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import java.nio.file.Path;
import java.util.List;

/**
 * An AI client the explore crew's workers run on: today Claude's own CLI ({@link ClaudeCli}), others later (decided
 * 2026-11-24). The client runs on the user's own login, the binary they installed and signed in to themselves;
 * BotMaker never reads, stores or passes a credential, and never an API key.
 */
public interface AgentClient {

    /** The client's id, {@code "claude"}. */
    String id();

    /** Whether the user is signed in to the client by their own subscription: when not, no worker starts. */
    boolean available();

    /**
     * The command line of one headless session of {@code role} given {@code prompt}, its MCP servers only those of
     * {@code mcpConfig}. {@link WorkerRunner} starts it with a scrubbed environment.
     */
    List<String> command(String prompt, WorkerRole role, Path mcpConfig);

    /**
     * The command line of one headless session of {@code role} given {@code prompt} that edits and builds in the folder
     * {@code lane}, where it is started: its file tools kept to the lane, its commands sandboxed with no network and no
     * view of the user's home but {@code readable}, nor of {@code hidden}, the client's own sign-in or anything outside
     * the lane to write. It refuses to start when it cannot sandbox. Empty for a client that cannot: no such worker
     * starts.
     */
    default List<String> laneCommand(String prompt, WorkerRole role, Path lane, List<Path> readable,
                                     List<Path> hidden) {
        return List.of();
    }
}
