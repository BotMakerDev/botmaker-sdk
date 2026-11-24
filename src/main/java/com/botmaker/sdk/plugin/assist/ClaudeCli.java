// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    /**
     * A restricted session: its file tools see the lane only, whatever a command asks outside it is refused, not asked
     * ({@code dontAsk}), and its commands run in Claude's sandbox or it does not start. No MCP server.
     */
    @Override
    public List<String> laneCommand(String prompt, WorkerRole role, Path lane, List<Path> readable,
                                    List<Path> hidden) {
        List<Path> kept = new ArrayList<>(hidden);
        // Its sign-in, where it is not in the home.
        String config = System.getenv("CLAUDE_CONFIG_DIR");
        if (config != null && !config.isBlank()) kept.add(Path.of(config));
        return List.of(executable, "-p", prompt,
                "--output-format", "json",
                "--model", role.model(),
                "--setting-sources", "",
                "--disable-slash-commands",
                "--no-session-persistence",
                "--restricted",
                "--permission-mode", "dontAsk",
                "--tools", "Read,Edit,Write,Glob,Grep,Bash",
                "--allowedTools", "Bash,Read(./**),Edit(./**)",
                "--strict-mcp-config",
                "--settings", sandbox(lane, readable, kept));
    }

    /**
     * The sandbox's settings: no network, none of the home but {@code lane} and {@code readable}, none of
     * {@code hidden}, no way out of it.
     */
    private static String sandbox(Path lane, List<Path> readable, List<Path> hidden) {
        ObjectNode settings = JSON.createObjectNode();
        ObjectNode sandbox = settings.putObject("sandbox");
        sandbox.put("enabled", true).put("failIfUnavailable", true).put("autoAllowBashIfSandboxed", true)
                .put("allowUnsandboxedCommands", false);
        ObjectNode network = sandbox.putObject("network");
        network.putArray("allowedDomains");
        network.put("strictAllowlist", true);
        ObjectNode files = sandbox.putObject("filesystem");
        ArrayNode unread = files.putArray("denyRead").add("~/");
        ArrayNode unwritten = files.putArray("denyWrite");
        hidden.forEach(path -> {
            unread.add(path.toString());
            unwritten.add(path.toString());
        });
        ArrayNode allowed = files.putArray("allowRead").add(lane.toString());
        readable.forEach(path -> allowed.add(path.toString()));
        return settings.toString();
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
