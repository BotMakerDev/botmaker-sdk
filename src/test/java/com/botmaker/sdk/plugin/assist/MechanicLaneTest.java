// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.FindingKind;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.StopRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Mechanic: one bounded try per fault, in a lane of its own, its branch left and never merged. */
@DisabledOnOs(OS.WINDOWS)
class MechanicLaneTest {

    private static final Coordinator.Crew MECHANICS = new Coordinator.Crew(Set.of(StopRole.MECHANIC), Set.of());
    private static final long LEASE = Coordinator.MECHANIC_LEASE.toMillis();

    @TempDir
    Path folder;

    // --- the Coordinator's side

    @Test
    void aFoundFaultIsTheMechanicsOnceWithItsOwnCap() {
        Journal journal = began();
        fault(journal, "the press never lands", 2);
        fault(journal, "the press never lands", 3);

        assertEquals(List.of(new Coordinator.Action.Lease("finding-1", StopRole.MECHANIC, 1, 50 + LEASE)),
                step(journal, 50));
        journal.append(new ExploreEvent.Leased("finding-1", StopRole.MECHANIC, 1, 50 + LEASE, 50));
        fault(journal, "a second fault", 51);
        assertEquals(List.of(), step(journal, 60), "one Mechanic at a time");

        // The run goes on meanwhile: a quiet one would end.
        fault(journal, "a second fault", 40 + LEASE);
        assertEquals(List.of(new Coordinator.Action.Requeue("finding-1", 1)), step(journal, 50 + LEASE)
                .stream().filter(a -> a instanceof Coordinator.Action.Requeue).toList());
        journal.append(new ExploreEvent.Released("finding-1", 1, false, "timeout", 50 + LEASE));
        assertEquals(List.of(new Coordinator.Action.Lease("finding-2", StopRole.MECHANIC, 1, 51 + 2 * LEASE)),
                step(journal, 51 + LEASE), "the first is not tried again");
    }

    @Test
    void aCrashIsParkedOnceTheMechanicHasItAndAHeldFaultIsNot() {
        Journal journal = began();
        journal.append(new ExploreEvent.Stopped(stop("crash-1", StopKind.CRASH), false, 2));
        assertEquals(List.of(new Coordinator.Action.Lease("crash-1", StopRole.MECHANIC, 1, 50 + LEASE)),
                step(journal, 50));
        journal.append(new ExploreEvent.Leased("crash-1", StopRole.MECHANIC, 1, 50 + LEASE, 50));
        assertEquals(List.of(new Coordinator.Action.Park("crash-1", "the Mechanic has it")), step(journal, 60));

        journal.append(new ExploreEvent.Parked("crash-1", 61));
        journal.append(new ExploreEvent.Released("crash-1", 1, true, "", 62));
        journal.append(new ExploreEvent.Stopped(stop("fault-2", StopKind.FAULT), false, 63));
        journal.append(new ExploreEvent.Leased("fault-2", StopRole.MECHANIC, 1, 70 + LEASE, 70));
        long late = 70 + Coordinator.Settings.DEFAULT.quiet().toMillis() + 1;
        assertEquals(List.of(), step(journal, late), "the run waits while the Mechanic works on what holds it");
        journal.append(new ExploreEvent.Released("fault-2", 1, true, "", late));
        assertEquals(List.of(), step(journal, late + 1), "a held fault never parks");
    }

    @Test
    void theUsersQueueListsAFoundFaultWithWhatTheMechanicLeft() {
        Journal journal = began();
        fault(journal, "the press never lands", 2);
        assertEquals("the press never lands (evidence: Home.shop)", queued(journal));

        journal.append(new ExploreEvent.Leased("finding-1", StopRole.MECHANIC, 1, 900, 3));
        assertTrue(queued(journal).endsWith("the Mechanic is on it"), queued(journal));
        journal.append(new ExploreEvent.Branched("finding-1", "explore/r/finding-1", "fixed the press", 4));
        assertTrue(queued(journal).endsWith("on branch explore/r/finding-1, yours to merge: fixed the press"),
                queued(journal));
        assertFalse(JournalView.of(journal).forUser().getFirst().resumable());
        assertTrue(JournalView.of(journal).open().isEmpty(), "the run does not wait on it");
    }

    // --- the lane's side

    @Test
    void itsWorkIsLeftOnABranchAndNeverMerged() throws Exception {
        Path checkout = checkout();
        String head = git(checkout, "rev-parse", "HEAD");
        Journal journal = began();

        WorkerRunner.Result result = lane(checkout).run(client("test -f .mechanic/fault.md || exit 9\n"
                        + "echo fixed > Code.java\nmkdir -p .claude; echo x > .claude/x\n"
                        + "echo 'fixed the press' > .mechanic/note.md"),
                journal, stop("finding-1", StopKind.FAULT), Duration.ofSeconds(20), () -> 7);

        assertEquals(new WorkerRunner.Result(true, ""), result);
        String branch = "explore/" + journal.runId() + "/finding-1";
        assertEquals(new ExploreEvent.Branched("finding-1", branch, "fixed the press", 7), left(journal));
        assertEquals("Code.java", git(checkout, "diff", "--name-only", "HEAD", branch), "its desk is not committed");
        assertEquals(head, git(checkout, "rev-parse", "HEAD"));
        assertEquals("code", Files.readString(checkout.resolve("Code.java")).strip());
        assertEquals("", git(checkout, "status", "--porcelain"));
        assertEquals(0, children(folder.resolve("lanes")), "the worktree is gone, its branch stays");
    }

    @Test
    void oneThatRanOutOfTimeLeavesWhatItHad() throws Exception {
        Path checkout = checkout();
        Journal journal = began();

        WorkerRunner.Result result = lane(checkout).run(client("echo half > Code.java\nsleep 30"), journal,
                stop("finding-1", StopKind.FAULT), Duration.ofSeconds(1), () -> 7);

        assertEquals("timeout", result.why());
        assertEquals("it ended early: timeout", left(journal).note());
        assertEquals("half", git(checkout, "show", left(journal).branch() + ":Code.java"));
    }

    @Test
    void oneThatChangedNothingLeavesItsNoteAndNoBranch() throws Exception {
        Path checkout = checkout();
        Journal journal = began();

        lane(checkout).run(client("echo 'the bot is at fault' > .mechanic/note.md"), journal,
                stop("finding-1", StopKind.FAULT), Duration.ofSeconds(20), () -> 7);

        assertEquals(new ExploreEvent.Branched("finding-1", "", "the bot is at fault", 7), left(journal));
        assertEquals("", git(checkout, "branch", "--list", "explore/*"));
    }

    @Test
    void aTestsPictureIsKeptAndWhatTheCheckoutIgnoresDoesNotFailIt() throws Exception {
        Path checkout = checkout();
        Files.writeString(checkout.resolve(".git/info/exclude"), "*.png\n.claude\n.mechanic\n");
        Journal journal = began();

        lane(checkout).run(client("mkdir -p src .claude; echo p > src/frame.png; echo p > other.png; echo x > .claude/x"),
                journal, stop("finding-1", StopKind.FAULT), Duration.ofSeconds(20), () -> 7);

        assertEquals("src/frame.png", git(checkout, "diff", "--name-only", "HEAD", left(journal).branch()));
    }

    @Test
    void aGitignoreOfTheLanesCannotCommitItsDesk() throws Exception {
        Path checkout = checkout();
        Journal journal = began();

        lane(checkout).run(client("printf '!/.mechanic/\\n!/.claude/\\n' > .gitignore; mkdir -p .claude; "
                        + "echo x > .claude/x; echo fixed > Code.java"),
                journal, stop("finding-1", StopKind.FAULT), Duration.ofSeconds(20), () -> 7);

        assertEquals(".gitignore\nCode.java", git(checkout, "diff", "--name-only", "HEAD", left(journal).branch()));
    }

    @Test
    void aRepositoryMadeInsideTheLaneIsNeverCommitted() throws Exception {
        Path checkout = checkout();
        String head = git(checkout, "rev-parse", "HEAD");
        Journal journal = began();

        lane(checkout).run(client("echo fixed > Code.java; mkdir -p src/x; git init -q src/x"),
                journal, stop("finding-1", StopKind.FAULT), Duration.ofSeconds(20), () -> 7);

        assertTrue(left(journal).note().startsWith("not committed"), left(journal).note());
        assertTrue(left(journal).note().contains("src/x/.git"), left(journal).note());
        assertEquals(head, git(checkout, "rev-parse", left(journal).branch()));
    }

    @Test
    void aClientThatWorksInNoLaneLeavesNothingBehind() throws Exception {
        Path checkout = checkout();

        WorkerRunner.Result result = lane(checkout).run(client(""), began(), stop("finding-1", StopKind.FAULT),
                Duration.ofSeconds(20), () -> 7);

        assertEquals("no lane: fake works in no lane", result.why());
        assertEquals("", git(checkout, "branch", "--list", "explore/*"));
        assertEquals(0, children(folder.resolve("lanes")));
    }

    @Test
    void aTryWhoseStopIsOverIsClosedAndHoldsUpNobody() {
        Journal journal = began();
        journal.append(new ExploreEvent.Leased("fault-9", StopRole.MECHANIC, 1, 50 + LEASE, 50));
        fault(journal, "a fault", 60);
        assertEquals(List.of(), step(journal, 70), "one Mechanic at a time");
        fault(journal, "a fault", 40 + LEASE);

        assertEquals(List.of(new Coordinator.Action.Requeue("fault-9", 1),
                new Coordinator.Action.Lease("finding-1", StopRole.MECHANIC, 1, 50 + 2 * LEASE)),
                step(journal, 50 + LEASE));
    }

    @Test
    void aStopBehindABusyMechanicHoldsUpNoRun() {
        Journal journal = began();
        fault(journal, "a fault", 2);
        journal.append(new ExploreEvent.Leased("finding-1", StopRole.MECHANIC, 1, 50 + LEASE, 50));
        journal.append(new ExploreEvent.Stopped(stop("crash-1", StopKind.CRASH), false, 60));
        assertEquals(List.of(new Coordinator.Action.Park("crash-1", "the Mechanic is busy")), step(journal, 70));

        journal.append(new ExploreEvent.Parked("crash-1", 71));
        journal.append(new ExploreEvent.Stopped(stop("fault-2", StopKind.FAULT), false, 72));
        long late = 72 + Coordinator.Settings.DEFAULT.quiet().toMillis() + 1;
        assertFalse(new Coordinator(Coordinator.Settings.DEFAULT, MECHANICS).step(JournalView.of(journal), late, false)
                .stream().anyMatch(a -> a instanceof Coordinator.Action.End), "the held fault waits its turn");
    }

    @Test
    void aNoteThatIsALinkIsNotRead() throws Exception {
        Path secret = Files.writeString(folder.resolve("secret.txt"), "hidden");
        Path desk = Files.createDirectories(folder.resolve("lane").resolve(MechanicLane.DESK));
        Files.createSymbolicLink(desk.resolve("note.md"), secret);

        assertEquals(Optional.empty(), MechanicLane.note(folder.resolve("lane")));
    }

    @Test
    void itsEnvironmentHoldsNoToken() {
        Map<String, String> environment = new HashMap<>(Map.of("PATH", "/bin", "JAVA_HOME", "/", "GITHUB_TOKEN", "x",
                "ANTHROPIC_API_KEY", "x", "AWS_SECRET_ACCESS_KEY", "x", "SSH_AUTH_SOCK", "/s", "MAVEN_OPTS", "-Dp=x"));

        MechanicLane.keep(environment);

        assertEquals(Map.of("PATH", "/bin", "JAVA_HOME", "/"), environment);
    }

    @Test
    void itsSessionIsSandboxedOrDoesNotStart() {
        List<String> command = new ClaudeCli().laneCommand("fix it", WorkerRole.MECHANIC, Path.of("/lane"),
                List.of(Path.of("/jdk")), List.of(Path.of("/sdk/.git")));

        String settings = command.get(command.indexOf("--settings") + 1);
        for (String part : List.of("\"enabled\":true", "\"failIfUnavailable\":true", "\"allowUnsandboxedCommands\":false",
                "\"allowedDomains\":[]", "\"strictAllowlist\":true", "\"denyRead\":[\"~/\",\"/sdk/.git\"",
                "\"denyWrite\":[\"/sdk/.git\"",
                "\"allowRead\":[\"/lane\",\"/jdk\"]")) {
            assertTrue(settings.contains(part), settings);
        }
        assertTrue(command.containsAll(List.of("--restricted", "dontAsk", "--strict-mcp-config")), command.toString());
        assertFalse(command.contains("--mcp-config"), "no tool of the bot's");
        assertFalse(command.contains("--bare"));
    }

    @Test
    void thereIsNoLaneWithoutANamedCheckout() throws Exception {
        assertTrue(MechanicLane.of(null, folder).isEmpty());
        assertTrue(MechanicLane.of(" ", folder).isEmpty());
        assertTrue(MechanicLane.of(folder.resolve("none").toString(), folder).isEmpty());
        assertTrue(MechanicLane.of(checkout().toString(), folder).isPresent());
    }

    // --- helpers

    private Journal began() {
        Journal journal = new Journal(folder.resolve("run-1"));
        journal.append(new ExploreEvent.Began(true, "sign in", 1));
        return journal;
    }

    private static void fault(Journal journal, String why, long at) {
        journal.append(new ExploreEvent.Finding(FindingKind.SDK_FAULT, "Home.shop", why, at));
    }

    private static List<Coordinator.Action> step(Journal journal, long now) {
        return new Coordinator(Coordinator.Settings.DEFAULT, MECHANICS).step(JournalView.of(journal), now, false)
                .stream().filter(a -> !(a instanceof Coordinator.Action.Beat) && !(a instanceof Coordinator.Action.End))
                .toList();
    }

    private static String queued(Journal journal) {
        return JournalView.of(journal).forUser().getFirst().question();
    }

    private static ExploreEvent.Branched left(Journal journal) {
        return JournalView.of(journal).branchOf("finding-1").orElseThrow();
    }

    private static Stop stop(String id, StopKind kind) {
        return new Stop(id, kind, "HOME", "HOME", "", List.of(), List.of(), Map.of(), List.of(), "broken", 1);
    }

    private MechanicLane lane(Path checkout) {
        return MechanicLane.of(checkout.toString(), folder.resolve("lanes")).orElseThrow();
    }

    private static AgentClient client(String body) {
        return new AgentClient() {
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
                throw new AssertionError("a Mechanic works in its lane");
            }

            @Override
            public List<String> laneCommand(String prompt, WorkerRole role, Path lane, List<Path> readable,
                                            List<Path> hidden) {
                assertTrue(hidden.getFirst().endsWith(".git"), hidden.toString());
                return body.isEmpty() ? List.of() : List.of("sh", "-c", body);
            }
        };
    }

    private Path checkout() throws Exception {
        Path checkout = Files.createDirectories(folder.resolve("sdk"));
        Files.writeString(checkout.resolve("pom.xml"), "<project/>");
        Files.writeString(checkout.resolve("Code.java"), "code");
        git(checkout, "init", "-q");
        git(checkout, "add", "-A");
        git(checkout, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", "commit", "-q", "-m", "a");
        return checkout;
    }

    private static String git(Path folder, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, process.waitFor(), out);
        return out;
    }

    private static long children(Path folder) throws IOException {
        try (var paths = Files.list(folder)) {
            return paths.count();
        }
    }
}
