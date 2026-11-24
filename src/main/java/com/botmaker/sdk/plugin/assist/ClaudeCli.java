// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Claude's CLI as the crew's client: the user's own unmodified {@code claude}, signed in by claude.ai, run headless
 * ({@code -p}) as the spike found lean (docs/audits/2026-11-24-headless-claude-spike.md). Never {@code --bare}, which
 * reads only an API key; always {@code --model}, since with no setting sources the user's model is not read.
 */
public final class ClaudeCli implements AgentClient {

    /** The name the worker's MCP config gives the bot's server: its tools are {@code mcp__botmaker__<tool>}. */
    static final String SERVER = "botmaker";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String executable;
    /** Runs a command line with a scrubbed environment: its standard output, empty when it failed. */
    private final Function<List<String>, Optional<String>> run;

    /** The {@code claude} on the PATH. */
    public ClaudeCli() {
        this("claude", ClaudeCli::output);
    }

    ClaudeCli(String executable, Function<List<String>, Optional<String>> run) {
        this.executable = executable;
        this.run = run;
    }

    @Override
    public String id() {
        return "claude";
    }

    /** Whether {@code claude auth status} says the user is signed in by claude.ai: never by a key or a cloud. */
    @Override
    public boolean available() {
        return run.apply(List.of(executable, "auth", "status")).map(ClaudeCli::byClaudeAi).orElse(false);
    }

    /** Whether {@code status}, what {@code claude auth status} printed, is a claude.ai sign-in. */
    static boolean byClaudeAi(String status) {
        try {
            JsonNode node = JSON.readTree(status);
            return node.path("loggedIn").asBoolean(false) && "claude.ai".equals(node.path("authMethod").asText());
        } catch (IOException notJson) {
            return false;
        }
    }

    @Override
    public List<String> command(String prompt, WorkerRole role, Path mcpConfig) {
        List<String> command = new ArrayList<>(List.of(executable, "-p", prompt,
                "--output-format", "json",
                "--model", role.model(),
                // The user's settings, hooks, memory and skills stay out: ten times the tokens, and not the worker's.
                "--setting-sources", "",
                "--disable-slash-commands",
                "--no-session-persistence",
                // No built-in tool: the worker reads and writes only through the bot's explore tools.
                "--tools", "",
                "--strict-mcp-config",
                "--mcp-config", mcpConfig.toString()));
        if (!role.tools().isEmpty()) {
            command.add("--allowedTools");
            command.add(role.tools().stream().map(tool -> "mcp__" + SERVER + "__" + tool)
                    .collect(Collectors.joining(",")));
        }
        return command;
    }

    /** Runs {@code command} as a worker is run, within ten seconds: its standard output, empty when it failed. */
    private static Optional<String> output(List<String> command) {
        try {
            // Its notices (an update, a warning) go to its errors, which are dropped: only its answer is parsed.
            ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            WorkerRunner.scrub(builder.environment());
            Process process = builder.start();
            process.getOutputStream().close();
            // Read while it runs, so a long answer never fills the pipe and holds it until the timeout.
            CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException unread) {
                    return "";
                }
            }, task -> Thread.ofVirtual().start(task));
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return process.exitValue() == 0 ? Optional.of(out.join()) : Optional.empty();
        } catch (IOException notStarted) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
