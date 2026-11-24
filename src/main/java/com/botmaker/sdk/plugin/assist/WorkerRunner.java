// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopRole;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Starts one worker of the explore crew for one stop (spec 2026-11-24-explore-agents-design § Workers): one process per
 * lease, killed when the lease runs out. What it found is the journal records it wrote through the explore tools; its
 * output is its session streamed line by line into its log, which the Explore page reads live ({@link WorkerLog}). The
 * process gets none of the environment's credentials, so the client runs on the user's
 * own sign-in or not at all; its standard input is closed, its folder the run's.
 */
public final class WorkerRunner {

    /**
     * What is taken out of a worker's environment: a key or a cloud provider would bill apart from the user's
     * subscription, and the crew runs on that subscription only.
     */
    static final List<String> SCRUBBED = List.of("ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN",
            "CLAUDE_CODE_USE_BEDROCK", "CLAUDE_CODE_USE_VERTEX", "CLAUDE_CODE_USE_FOUNDRY");

    /** The worker-only MCP config's resource, copied into the run's folder. */
    static final String MCP_CONFIG = "worker-mcp.json";

    /** How a worker's try ended: {@code ok} when it exited cleanly, else {@code why} in a word or two. */
    public record Result(boolean ok, String why) {}

    /** The run's folder: the worker's working directory, and where its MCP config and its log are. */
    private final Path folder;

    public WorkerRunner(Path folder) {
        this.folder = folder;
    }

    /** Takes {@link #SCRUBBED} out of {@code environment}. */
    static void scrub(Map<String, String> environment) {
        SCRUBBED.forEach(environment::remove);
    }

    /**
     * Runs {@code client}'s worker for {@code role} on {@code stop}, killed at {@code limit}: the Coordinator's lease.
     * A role no worker takes is not started.
     */
    public Result run(AgentClient client, StopRole role, Stop stop, Duration limit) {
        return run(client, role, stop, "", limit);
    }

    /** As {@link #run(AgentClient, StopRole, Stop, Duration)}, for a run exploring toward {@code goal} (blank: none). */
    public Result run(AgentClient client, StopRole role, Stop stop, String goal, Duration limit) {
        Optional<WorkerRole> worker = WorkerRole.of(role);
        if (worker.isEmpty()) return new Result(false, "no worker takes this stop");
        return run(client.command(prompt(worker.get(), stop, goal), worker.get(), mcpConfig()), stop.id(), limit);
    }

    /**
     * The Evaluator's try at the evaluation {@code id} of the run {@code run}, exploring toward {@code goal}, started
     * {@code why}: its result by {@code limit}. What it judged is what it wrote through the explore tools meanwhile.
     */
    Result evaluate(AgentClient client, String id, String run, String goal, EvaluatorTrigger.Why why, Duration limit) {
        return run(client.command(EvaluatorPrompt.of(id, run, goal, why), WorkerRole.EVALUATOR, mcpConfig()), id,
                limit);
    }

    /** Runs {@code command} as a worker of {@code stopId}: its result by {@code limit}. */
    Result run(List<String> command, String stopId, Duration limit) {
        return run(command, stopId, limit, folder, WorkerRunner::scrub);
    }

    /** As {@link #run(List, String, Duration)}, in the folder {@code in}, its environment what {@code scrub} leaves. */
    Result run(List<String> command, String stopId, Duration limit, Path in, Consumer<Map<String, String>> scrub) {
        Process process;
        Thread writer;
        try {
            Path log = new Journal(folder).workerLog(stopId);
            Files.createDirectories(log.getParent());
            endLine(log);
            ProcessBuilder builder = new ProcessBuilder(command).directory(in.toFile()).redirectErrorStream(true);
            scrub.accept(builder.environment());
            process = builder.start();
            // No one answers it: a worker that waits on its input reads its end at once.
            process.getOutputStream().close();
            writer = Thread.ofVirtual().start(() -> copy(process.getInputStream(), log));
        } catch (IOException notStarted) {
            return new Result(false, "not started: " + notStarted.getMessage());
        }
        Result result;
        try {
            if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) {
                kill(process);
                result = new Result(false, "timeout");
            } else {
                int exit = process.exitValue();
                result = exit == 0 ? new Result(true, "") : new Result(false, "exit " + exit);
            }
        } catch (InterruptedException stopped) {
            kill(process);
            Thread.currentThread().interrupt();
            return new Result(false, "stopped");
        }
        try {
            // The pipe closes with the process: what it printed last is in the log before its result is given.
            writer.join(Duration.ofSeconds(5));
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    /**
     * Appends what a worker prints to {@code log} line by line, as it comes: whole lines only, a picture's data left
     * out ({@link WorkerLog#slim}), and a last line cut by a kill ended, so the next session of the stop starts on a
     * line of its own.
     */
    private static void copy(InputStream printed, Path log) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(printed, StandardCharsets.UTF_8));
             Writer out = Files.newBufferedWriter(log, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                     StandardOpenOption.APPEND)) {
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                out.write(WorkerLog.slim(line));
                out.write('\n');
                out.flush();
            }
        } catch (IOException closed) {
            // The process was killed under the read: what was written stays.
        }
    }

    /** Ends {@code log}'s last line when something (a Studio that died mid-write) left it cut. */
    private static void endLine(Path log) throws IOException {
        if (!Files.isRegularFile(log) || Files.size(log) == 0) return;
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "rw")) {
            file.seek(file.length() - 1);
            if (file.read() != '\n') file.write('\n');
        }
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /** The worker-only MCP config in the run's folder, copied there once from the SDK's resource. */
    Path mcpConfig() {
        Path config = folder.resolve(MCP_CONFIG);
        if (Files.exists(config)) return config;
        try (InputStream in = WorkerRunner.class.getResourceAsStream(MCP_CONFIG)) {
            if (in == null) throw new IOException(MCP_CONFIG + " is not in the SDK's jar");
            Files.createDirectories(folder);
            Files.copy(in, config);
            return config;
        } catch (FileAlreadyExistsException copiedMeanwhile) {
            // Another worker started at the same time copied it first: the same bytes.
            return config;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /**
     * What {@code role} is asked about {@code stop} of a run toward {@code goal}: the Reader's own prompt, else which
     * stop, and that its answer goes through the tools.
     */
    static String prompt(WorkerRole role, Stop stop, String goal) {
        if (role == WorkerRole.READER) return ReaderPrompt.of(stop, goal);
        return prompt(role, stop);
    }

    /** What {@code role} is asked about {@code stop}: which stop, and that its answer goes through the tools. */
    static String prompt(WorkerRole role, Stop stop) {
        return "You are the " + role.displayName() + " of a BotMaker explore run. Take stop " + stop.id() + " ("
                + stop.kind().id() + (stop.screen() == null ? "" : " on " + stop.screen()) + "): read it with the "
                + "explore tool, and write your answer only through the explore tools, as they say. Reply with "
                + "nothing else.";
    }
}
