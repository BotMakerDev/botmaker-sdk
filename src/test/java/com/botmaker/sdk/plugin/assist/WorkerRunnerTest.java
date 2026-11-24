// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.StopRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One worker process per lease: killed at its limit, given no credential, its input closed, in the run's folder. */
@DisabledOnOs(OS.WINDOWS)
class WorkerRunnerTest {

    @TempDir
    Path folder;

    /** A fake worker: {@code body} run by {@code sh}. */
    private List<String> worker(String body) throws IOException {
        Path script = Files.writeString(folder.resolve("worker.sh"), body);
        return List.of("sh", script.toString());
    }

    @Test
    void aCleanExitIsOk() throws IOException {
        assertEquals(new WorkerRunner.Result(true, ""),
                new WorkerRunner(folder).run(worker("exit 0"), "s1", Duration.ofSeconds(10)));
    }

    @Test
    void aFailedExitSaysItsCode() throws IOException {
        assertEquals(new WorkerRunner.Result(false, "exit 3"),
                new WorkerRunner(folder).run(worker("exit 3"), "s1", Duration.ofSeconds(10)));
    }

    @Test
    void aHangIsKilledAtItsLimit() throws IOException {
        long start = System.nanoTime();

        WorkerRunner.Result result = new WorkerRunner(folder).run(worker("sleep 30"), "s1", Duration.ofMillis(300));

        assertEquals(new WorkerRunner.Result(false, "timeout"), result);
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void itsInputIsClosedAndItRunsInTheRunsFolder() throws IOException {
        List<String> command = worker("if read line; then echo got > stdin.txt; else echo eof > stdin.txt; fi\n"
                + "pwd > cwd.txt\necho said\n");

        WorkerRunner.Result result = new WorkerRunner(folder).run(command, "s1", Duration.ofSeconds(10));

        assertTrue(result.ok(), result.why());
        assertEquals("eof", Files.readString(folder.resolve("stdin.txt")).strip());
        assertEquals(folder.toRealPath().toString(), Files.readString(folder.resolve("cwd.txt")).strip());
        assertEquals("said", Files.readString(folder.resolve("workers/s1.log")).strip(), "its output is kept aside");
    }

    @Test
    void aSessionCutMidLineLeavesTheNextOneItsOwnLine() throws IOException {
        Path log = folder.resolve("workers/s1.log");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "{\"type\":\"assistant\",\"mess");

        new WorkerRunner(folder).run(worker("printf 'half'; sleep 30"), "s1", Duration.ofMillis(500));
        new WorkerRunner(folder).run(worker("echo second"), "s1", Duration.ofSeconds(10));

        assertEquals(List.of("{\"type\":\"assistant\",\"mess", "half", "second"), Files.readAllLines(log));
    }

    @Test
    void noCredentialReachesTheWorker() throws IOException {
        Map<String, String> environment = new HashMap<>(Map.of("PATH", "/bin", "ANTHROPIC_API_KEY", "x",
                "ANTHROPIC_AUTH_TOKEN", "x", "CLAUDE_CODE_USE_BEDROCK", "1", "CLAUDE_CODE_USE_VERTEX", "1",
                "CLAUDE_CODE_USE_FOUNDRY", "1"));

        WorkerRunner.scrub(environment);

        assertEquals(Map.of("PATH", "/bin"), environment);
        List<String> command = worker("for name in " + String.join(" ", WorkerRunner.SCRUBBED) + "; do\n"
                + "  if env | grep -q \"^$name=\"; then echo $name >> leaked.txt; fi\ndone\n");
        assertTrue(new WorkerRunner(folder).run(command, "s1", Duration.ofSeconds(10)).ok());
        assertFalse(Files.exists(folder.resolve("leaked.txt")));
    }

    @Test
    void aStopIsHandedToItsRolesWorkerWithTheWorkerMcpConfig() {
        List<Object> asked = new ArrayList<>();
        AgentClient client = new AgentClient() {
            @Override
            public String id() {
                return "fake";
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<String> command(String prompt, WorkerRole role, Path mcpConfig) {
                asked.addAll(List.of(prompt, role, mcpConfig));
                return List.of("sh", "-c", "exit 0");
            }
        };
        Stop stop = new Stop("s7", StopKind.UNKNOWN_SCREEN, "HOME", "HOME", null, List.of(), List.of(), Map.of(),
                List.of(), null, 0);

        WorkerRunner runner = new WorkerRunner(folder);
        assertTrue(runner.run(client, StopRole.CARTOGRAPHER, stop, Duration.ofSeconds(10)).ok());
        assertTrue(((String) asked.get(0)).contains("s7"), asked.toString());
        assertEquals(WorkerRole.READER, asked.get(1));
        assertEquals(folder.resolve(WorkerRunner.MCP_CONFIG), asked.get(2));
        assertTrue(Files.exists(folder.resolve(WorkerRunner.MCP_CONFIG)));

        assertFalse(runner.run(client, StopRole.USER, stop, Duration.ofSeconds(10)).ok(), "no worker takes the user's");
    }
}
