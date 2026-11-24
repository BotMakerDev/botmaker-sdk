// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.runlog.Scrubber;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.shared.tools.UserDirs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Mechanic's lane (spec 2026-11-24-explore-agents-design § Findings and hand-off): a git worktree of the SDK's
 * checkout on a branch of its own, {@code explore/<run>/<stop>}, in the user's cache. The Mechanic edits and tests
 * there, its commands in the client's sandbox with no network and no view of the user's home; the host, not the
 * Mechanic, commits what it left, and nothing ever merges it.
 *
 * <p>There is a lane only where the SDK's developer named their checkout in {@value #CHECKOUT}: everybody else's crew
 * has no Mechanic.
 */
final class MechanicLane {

    /** The environment variable naming the SDK's checkout. */
    static final String CHECKOUT = "BOTMAKER_SDK_CHECKOUT";

    static final String RESOURCE = "mechanic-prompt.md";

    /** The lane's folder for what is said to and by the Mechanic; never committed. */
    static final String DESK = ".mechanic";

    /** The longest note kept. */
    static final int NOTE = 2000;

    /** How many of the journal's last lines the Mechanic is given. */
    private static final int LINES = 60;

    /** How long one git command may take. */
    private static final Duration GIT = Duration.ofSeconds(30);

    /** How much of its lease, a tenth at most, is kept for the commit of what it left. */
    private static final Duration COMMIT = Duration.ofMinutes(1);

    /** All of the environment a Mechanic, its git and its build get: where things are, never what opens them. */
    private static final Set<String> KEPT = Set.of("PATH", "HOME", "USER", "LOGNAME", "SHELL", "LANG", "LC_ALL",
            "LC_CTYPE", "TZ", "TMPDIR", "TERM", "JAVA_HOME", "CLAUDE_CONFIG_DIR");

    private final Path checkout;
    private final Path lanes;

    MechanicLane(Path checkout, Path lanes) {
        this.checkout = checkout;
        this.lanes = lanes;
    }

    /** The lane of the checkout {@value #CHECKOUT} names; empty when it names none. */
    static Optional<MechanicLane> ofThisMachine() {
        return of(System.getenv(CHECKOUT), UserDirs.cache().resolve("explore-lanes"));
    }

    static Optional<MechanicLane> of(String checkout, Path lanes) {
        if (checkout == null || checkout.isBlank()) return Optional.empty();
        Path folder = Path.of(checkout.strip()).toAbsolutePath().normalize();
        return Files.isRegularFile(folder.resolve("pom.xml")) && Files.exists(folder.resolve(".git"))
                ? Optional.of(new MechanicLane(folder, lanes)) : Optional.empty();
    }

    /** Leaves in {@code environment} only {@link #KEPT}. */
    static void keep(Map<String, String> environment) {
        environment.keySet().retainAll(KEPT);
        String java = environment.get("JAVA_HOME");
        if (java == null || !Files.isDirectory(Path.of(java))) environment.put("JAVA_HOME", System.getProperty("java.home"));
    }

    /**
     * The Mechanic's one try at {@code stop} of {@code journal}'s run, on {@code client}, killed at {@code limit}. What
     * it left is committed on the lane's branch and said as an {@link ExploreEvent.Branched}, whether it finished, ran
     * out of time or was stopped with its run.
     */
    WorkerRunner.Result run(AgentClient client, Journal journal, Stop stop, Duration limit, LongSupplier clock) {
        String name = journal.runId() + "-" + stop.id();
        String branch = "explore/" + journal.runId() + "/" + stop.id();
        Path lane = lanes.resolve(name).toAbsolutePath().normalize();
        String gitDir;
        List<String> command;
        long started = System.nanoTime();
        boolean added = false;
        try {
            Files.createDirectories(lanes);
            git(checkout, "worktree", "add", "-q", "-b", branch, lane.toString(), "HEAD");
            added = true;
            gitDir = git(lane, "rev-parse", "--absolute-git-dir");
            // The checkout's own repository, which the lane shares: its branches are not the Mechanic's to move.
            Path shared = lane.resolve(git(lane, "rev-parse", "--git-common-dir")).normalize();
            desk(journal, stop, lane);
            command = client.laneCommand(prompt(stop), WorkerRole.MECHANIC, lane, readable(), List.of(shared));
            if (command.isEmpty()) throw new IOException(client.id() + " works in no lane");
        } catch (IOException | UncheckedIOException failed) {
            if (added) drop(lane, branch);
            return new WorkerRunner.Result(false, "no lane: " + failed.getMessage());
        } catch (InterruptedException stopped) {
            if (added) drop(lane, branch);
            Thread.currentThread().interrupt();
            return new WorkerRunner.Result(false, "stopped");
        }
        // The lane's own work before and after is within the lease too.
        Duration left = limit.minusNanos(System.nanoTime() - started).minus(
                COMMIT.compareTo(limit.dividedBy(10)) < 0 ? COMMIT : limit.dividedBy(10));
        WorkerRunner.Result result = new WorkerRunner(journal.folder()).run(command, stop.id(),
                left.isNegative() ? Duration.ZERO : left, lane,
                environment -> {
                    keep(environment);
                    // The sandbox writes in the lane only, and a JVM's temporary files ignore TMPDIR.
                    environment.put("JAVA_TOOL_OPTIONS", "-Djava.io.tmpdir=" + lane.resolve(DESK).resolve("tmp"));
                });
        // A stopped Mechanic's work is kept too: the interrupt waits for the commit.
        boolean interrupted = Thread.interrupted();
        try {
            leave(journal, stop, lane, gitDir, branch, result, clock);
        } catch (IOException failed) {
            journal.append(new ExploreEvent.Branched(stop.id(), branch, "not committed, left in " + lane + ": "
                    + failed.getMessage(), clock.getAsLong()));
        } catch (InterruptedException stopped) {
            interrupted = true;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        return result;
    }

    /** Commits what the Mechanic left in {@code lane} on {@code branch}, says it, and removes the worktree. */
    private void leave(Journal journal, Stop stop, Path lane, String gitDir, String branch, WorkerRunner.Result result,
                       LongSupplier clock) throws IOException, InterruptedException {
        String note = note(lane).orElse(result.ok() ? "it left no note" : "it ended early: " + result.why());
        String[] own = {"--git-dir=" + gitDir, "--work-tree=" + lane};
        // A repository the Mechanic made inside the lane has a config of its own, which git run here would read.
        Optional<Path> nested = nestedRepository(lane);
        if (nested.isPresent()) throw new IOException("a git repository inside the lane, " + nested.get());
        git(lane, own, "add", "-A");
        // A test's picture too: the SDK ignores *.png.
        if (Files.isDirectory(lane.resolve("src"))) git(lane, own, "add", "-A", "-f", "--", "src");
        // Its desk and Claude's folder taken out again, whatever a .gitignore of the lane's says or a name's case.
        git(lane, own, "rm", "-r", "-q", "--cached", "--ignore-unmatch", "--", ":(top,icase)" + DESK,
                ":(top,icase).claude");
        // One made while the lane was read: a repository staged is a gitlink, and nothing of it is committed.
        nested = nestedRepository(lane);
        boolean gitlink = git(lane, own, "ls-files", "-s").lines().anyMatch(line -> line.startsWith("160000 "));
        if (nested.isPresent() || gitlink) {
            git(lane, own, "reset", "-q");
            throw new IOException("a git repository inside the lane, " + nested.map(Path::toString).orElse("staged"));
        }
        boolean changed = !git(lane, own, "diff", "--cached", "--name-only").isBlank();
        if (changed) {
            git(lane, own, "-c", "user.name=BotMaker Mechanic", "-c", "user.email=mechanic@botmaker.invalid",
                    "-c", "commit.gpgsign=false", "commit", "-q", "-m",
                    "Explore: " + stop.id() + "\n\n" + note);
        }
        journal.append(new ExploreEvent.Branched(stop.id(), changed ? branch : "", note, clock.getAsLong()));
        drop(lane, changed ? null : branch);
    }

    /**
     * The first {@code .git} below {@code lane}'s own, link or not, its case aside (a Mac's disk ignores it), links never
     * followed; empty for none.
     */
    static Optional<Path> nestedRepository(Path lane) throws IOException {
        Path own = lane.resolve(".git");
        try (Stream<Path> walk = Files.walk(lane)) {
            return walk.filter(path -> path.getFileName() != null
                            && path.getFileName().toString().equalsIgnoreCase(".git"))
                    .filter(path -> !path.equals(own)).findFirst().map(lane::relativize);
        } catch (UncheckedIOException unread) {
            throw unread.getCause();
        }
    }

    /** Removes the worktree {@code lane}, and {@code branch} (null: it holds work, and stays). */
    private void drop(Path lane, String branch) {
        try {
            git(checkout, "worktree", "remove", "--force", lane.toString());
            if (branch != null) git(checkout, "branch", "-D", branch);
        } catch (IOException left) {
            // A folder that could not go is the cache's.
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    /** Writes what the Mechanic reads: the fault, its frame and the journal's last lines. */
    private void desk(Journal journal, Stop stop, Path lane) throws IOException {
        Path desk = Files.createDirectories(lane.resolve(DESK));
        Files.createDirectories(desk.resolve("tmp"));
        JournalView view = JournalView.of(journal);
        Scrubber scrubber = Scrubber.forThisMachine(List.of());
        List<ExploreEvent> last = view.events().stream().filter(e -> !(e instanceof ExploreEvent.Heartbeat)).toList();
        // Not how Claude goes on from it: the Mechanic has none of the bot's tools.
        String text = ExploreTools.whole(stop, List.of()) + "\n\nThe run's last records:\n"
                + last.subList(Math.max(0, last.size() - LINES), last.size()).stream().map(String::valueOf)
                .collect(Collectors.joining("\n"));
        Files.writeString(desk.resolve("fault.md"), scrubber.scrub(text));
        if (!stop.framePath().isBlank()) {
            Path frame = Path.of(stop.framePath());
            if (Files.isRegularFile(frame)) Files.copy(frame, desk.resolve("frame.png"));
        }
    }

    /** The note the Mechanic left, cut at {@link #NOTE}: only a plain file inside the lane is read. */
    static Optional<String> note(Path lane) {
        Path file = lane.resolve(DESK).resolve("note.md");
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().equals(lane.toRealPath().resolve(DESK).resolve("note.md"))) {
                return Optional.empty();
            }
            byte[] bytes;
            try (InputStream in = Files.newInputStream(file)) {
                bytes = in.readNBytes(NOTE);
            }
            String note = new String(bytes, StandardCharsets.UTF_8).strip();
            return note.isEmpty() ? Optional.empty() : Optional.of(note);
        } catch (IOException unread) {
            return Optional.empty();
        }
    }

    /** What the sandbox shows of the user's home beside the lane: the Maven repository, the JDK the build runs on. */
    private static List<Path> readable() {
        String java = System.getenv("JAVA_HOME");
        Path jdk = Path.of(java != null && Files.isDirectory(Path.of(java)) ? java : System.getProperty("java.home"));
        return List.of(Path.of(System.getProperty("user.home"), ".m2", "repository"), jdk);
    }

    private static String prompt(Stop stop) {
        try (InputStream in = MechanicLane.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException(RESOURCE + " is not in the SDK's jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("{stop}", stop.id());
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static String git(Path folder, String... args) throws IOException, InterruptedException {
        return git(folder, new String[0], args);
    }

    /** Runs git in {@code folder} with no hook of anybody's: what it printed, or an exception saying how it failed. */
    private static String git(Path folder, String[] own, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(own));
        command.addAll(List.of("-c", "core.hooksPath=" + File.separator + "nonexistent", "-c", "core.fsmonitor=false",
                "-c", "protocol.file.allow=never", "-c", "submodule.recurse=false"));
        command.addAll(List.of(args));
        Path out = Files.createTempFile("mechanic-git", ".txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(folder.toFile())
                    .redirectErrorStream(true).redirectOutput(out.toFile());
            keep(builder.environment());
            Process process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(GIT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("git " + args[0] + " took over " + GIT.toSeconds() + " s");
            }
            String said = Files.readString(out).strip();
            if (process.exitValue() != 0) throw new IOException("git " + args[0] + ": " + said);
            return said;
        } finally {
            Files.deleteIfExists(out);
        }
    }
}
