package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.plugin.run.SdkRuns;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The assistant's half of <em>Keep exploring</em> (spec §6): it waits on the run, reads its stops, resumes one once
 * it is fixed, and stops the run, through the run's explore {@link Journal} in the SDK's state folder of the bot. The
 * run is started by core's {@code run_bot} with the entry {@value SdkRuns#KEEP_EXPLORING}. Every call is written to
 * the journal as a {@link ExploreEvent.Claude} event, for the Explore tab.
 */
final class ExploreTools {

    /** How long a {@code wait} blocks when it is not told. */
    static final int WAIT_SECONDS = 600;
    /** How long a {@code resume} waits for the run to load the bot again. */
    static final Duration RESUME_WITHIN = Duration.ofMinutes(2);
    /** How long a {@code stop} waits for the run to end. */
    static final Duration STOP_WITHIN = Duration.ofSeconds(30);
    /** How many lines read on a stop's frame an answer gives. */
    private static final int TEXTS = 40;
    /** The longest one look at the journal sleeps, so a change a watch misses is seen soon. */
    private static final long NAP_NANOS = TimeUnit.SECONDS.toNanos(1);
    /** One resume at a time: a second waits for the first's answer and finds its stop resumed. */
    private static final Object RESUMING = new Object();

    /** One action of the {@code explore} tool. */
    sealed interface Action {
    }

    @Describe("block until the run stops, finds a screen or ends; answers the open stop whole, else the oldest parked")
    record Wait(@Describe(value = "the longest to wait; blank: 600", optional = true) Integer seconds)
            implements Action {
    }

    @Describe("the run's counts, where it is, and its open stop")
    record Status() implements Action {
    }

    @Describe("the parked stops, oldest first")
    record Queue() implements Action {
    }

    @Describe("load the fixed bot and go on from a stop; answers once the run reloaded, or why it refused")
    record Resume(@Describe("the stop's id, as wait names it") String stop,
                  @Describe(value = "true: what it stopped on is fine as it is, never raised again", optional = true)
                  Boolean ignore) implements Action {
    }

    @Describe("end the run")
    record Stop() implements Action {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(AssistantTool.named("explore")
            .describedAs("Keep exploring, the run that plays the game on its own and stops on what the bot does not "
                    + "know: wait on it, fix the stop with the map tools, resume. run_bot entry \""
                    + SdkRuns.KEEP_EXPLORING + "\" starts it")
            .takesOneOf(Action.class).handledBy(ExploreTools::handle));

    private ExploreTools() {}

    static AgentReply handle(Action action, AgentContext context) {
        Path project = context == null || context.services() == null ? null : context.services().projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
        return handle(action, Journal.of(project));
    }

    static AgentReply handle(Action action, Journal journal) {
        return switch (action) {
            case Wait wait -> waitFor(journal, Duration.ofSeconds(wait.seconds() == null || wait.seconds() <= 0
                    ? WAIT_SECONDS : wait.seconds()));
            case Status ignored -> status(journal);
            case Queue ignored -> queue(journal);
            case Resume resume -> resume(journal, resume.stop(), Boolean.TRUE.equals(resume.ignore()), RESUME_WITHIN);
            case Stop ignored -> stop(journal, STOP_WITHIN);
        };
    }

    /**
     * Answers at once with the open stop, else the oldest parked one, else the screens found since the last wait;
     * otherwise blocks until the run stops, finds a screen or ends, or {@code within} passes ("still running").
     */
    static AgentReply waitFor(Journal journal, Duration within) {
        JournalView view = JournalView.of(journal);
        if (!view.began()) return AgentReply.refused(noRun());
        Optional<AgentReply> now = answer(view);
        claude(journal, JournalView.WAIT);
        if (now.isPresent()) return now.get();
        long deadline = System.nanoTime() + within.toNanos();
        int seen = view.all().size();
        while (true) {
            if (!changed(journal, seen, deadline)) {
                return AgentReply.text("Still running after " + within.toSeconds() + " s: "
                        + counts(JournalView.of(journal)));
            }
            view = JournalView.of(journal);
            seen = view.all().size();
            now = answer(view);
            if (now.isPresent()) return now.get();
        }
    }

    /** What a wait answers with now, if anything: the open stop, the oldest parked, new screens, or the run's end. */
    private static Optional<AgentReply> answer(JournalView view) {
        List<String> screens = view.newScreensSinceWait();
        String found = screens.isEmpty() ? "" : "\nNew screens since the last wait: " + String.join(", ", screens) + ".";
        if (view.open().isPresent()) {
            return Optional.of(AgentReply.text("The run waits on this stop:\n" + whole(view.open().get()) + found));
        }
        if (view.ended().isPresent()) {
            return Optional.of(AgentReply.text("The run ended (" + view.ended().get().displayName() + "): "
                    + counts(view) + found));
        }
        // Said once each time one is parked, so a stop left for later never keeps a wait from blocking.
        if (!view.queue().isEmpty() && view.parkedSinceWait()) {
            return Optional.of(AgentReply.text("No stop open; the run goes on. The oldest of " + view.queue().size()
                    + " parked (queue lists them):\n" + whole(view.queue().getFirst()) + found));
        }
        if (!screens.isEmpty()) return Optional.of(AgentReply.text(found.strip() + " " + counts(view)));
        return Optional.empty();
    }

    static AgentReply status(Journal journal) {
        claude(journal, "status");
        JournalView view = JournalView.of(journal);
        if (!view.began()) return AgentReply.refused(noRun());
        String state = view.ended().map(end -> "Ended (" + end.displayName() + "). ").orElse(
                view.open().map(s -> "Waits on " + s.id() + ": " + s.missing() + ". ").orElse("Running. "));
        return AgentReply.text(state + counts(view));
    }

    static AgentReply queue(Journal journal) {
        claude(journal, "queue");
        List<com.botmaker.sdk.internal.explore.Stop> parked = journal.queue();
        if (parked.isEmpty()) return AgentReply.text("No stop is parked.");
        return AgentReply.text(parked.stream().map(s -> s.id() + " — " + s.kind().displayName() + " on " + s.screen()
                + (s.place().isEmpty() ? "" : " (" + s.place() + ")") + ": " + s.missing())
                .collect(Collectors.joining("\n")));
    }

    /**
     * Has the run load the bot again and go on from {@code stopId}, the open stop or a parked one, and answers the
     * run's reload: its generation, or the compile's errors with the stop still open. One resume at a time; a stop
     * already resumed answers that reload again rather than loading a second generation.
     */
    static AgentReply resume(Journal journal, String stopId, boolean ignore, Duration within) {
        if (stopId == null || stopId.isBlank()) return AgentReply.refused("Name the stop to resume, as wait names it.");
        String id = stopId.strip();
        synchronized (RESUMING) {
            JournalView view = JournalView.of(journal);
            if (!view.running()) return AgentReply.refused(view.began() ? "The run has ended; run_bot entry \""
                    + SdkRuns.KEEP_EXPLORING + "\" starts another." : noRun());
            boolean open = view.open().filter(s -> s.id().equals(id)).isPresent();
            if (!open && view.parked(id).isEmpty()) {
                Optional<ExploreEvent.Reloaded> done = view.reloadOf(id).filter(r -> !r.refused());
                if (done.isPresent()) return AgentReply.text("Already resumed: " + took(done.get()));
                return AgentReply.refused("No stop " + id + " is open or parked. " + ids(view));
            }
            int seen = view.all().size();
            claude(journal, "resume " + id + (ignore ? " ignore" : ""));
            journal.command("resume " + id + (ignore ? " ignore" : ""));
            long deadline = System.nanoTime() + within.toNanos();
            while (true) {
                List<ExploreEvent> all = journal.events();
                for (ExploreEvent event : all.subList(Math.min(seen, all.size()), all.size())) {
                    if (event instanceof ExploreEvent.Reloaded reloaded && reloaded.stopId().equals(id)) {
                        return reloaded(reloaded, JournalView.of(journal));
                    }
                }
                if (!JournalView.of(journal).running()) {
                    return AgentReply.refused("The run ended before it took the resume of " + id + ".");
                }
                if (!changed(journal, all.size(), deadline)) {
                    return AgentReply.text("The run has not taken the resume of " + id + " in " + within.toSeconds()
                            + " s; it takes it at its next round, and wait says how it went.");
                }
            }
        }
    }

    /** Asks the run to end, after any resume under way: one command file, so a stop never overwrites a resume. */
    static AgentReply stop(Journal journal, Duration within) {
        synchronized (RESUMING) {
            JournalView view = JournalView.of(journal);
            if (!view.running()) return AgentReply.refused(view.began() ? "The run has already ended." : noRun());
            claude(journal, "stop");
            journal.command("stop");
            long deadline = System.nanoTime() + within.toNanos();
            int seen = view.all().size();
            while (JournalView.of(journal).running()) {
                if (!changed(journal, seen, deadline)) return AgentReply.text("Asked the run to stop; it ends at its "
                        + "next round.");
                seen = journal.events().size();
            }
            return AgentReply.text("The run ended: " + counts(JournalView.of(journal)));
        }
    }

    private static AgentReply reloaded(ExploreEvent.Reloaded reloaded, JournalView now) {
        String id = reloaded.stopId();
        if (reloaded.refused() && now.open().filter(s -> s.id().equals(id)).isEmpty() && now.parked(id).isEmpty()) {
            return AgentReply.refused("The run no longer holds " + id + ", so nothing was loaded: "
                    + String.join("; ", reloaded.errors()));
        }
        if (reloaded.refused()) {
            return AgentReply.refused("Resume of " + reloaded.stopId() + " refused; generation " + reloaded.generation()
                    + " kept and the stop stays open:\n- " + String.join("\n- ", reloaded.errors()));
        }
        return AgentReply.text("Resumed: " + took(reloaded));
    }

    private static String took(ExploreEvent.Reloaded reloaded) {
        return "reloaded as generation " + reloaded.generation() + "; the run tries " + reloaded.stopId()
                + "'s place again, and wait says whether the fix took.";
    }

    /** {@code stop} whole, as Claude fixes it from: what is missing, the frame, what was read and pressed. */
    static String whole(com.botmaker.sdk.internal.explore.Stop stop) {
        List<String> lines = new ArrayList<>();
        lines.add(stop.id() + ": " + stop.kind().displayName() + " on " + (stop.screen() == null ? "a screen the map "
                + "lacks" : stop.screen()) + (stop.place().isEmpty() ? "" : " (" + stop.place() + ")"));
        lines.add("Missing: " + stop.missing());
        if (!stop.framePath().isEmpty()) lines.add("Frame: " + stop.framePath());
        if (!stop.texts().isEmpty()) {
            lines.add("Read on it: " + stop.texts().stream().limit(TEXTS).map(t -> "\"" + t.text() + "\""
                    + (t.box() == null ? "" : " (" + t.box().x() + "," + t.box().y() + " " + t.box().width() + "x"
                    + t.box().height() + ")")).collect(Collectors.joining("; ")));
        }
        if (!stop.elements().isEmpty()) lines.add("Elements: " + String.join("; ", stop.elements()));
        if (!stop.facts().isEmpty()) {
            lines.add("Facts: " + stop.facts().entrySet().stream().map(e -> e.getKey() + " = " + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (!stop.lastPresses().isEmpty()) lines.add("Last presses: " + String.join(", ", stop.lastPresses()));
        lines.add("Fix it with the map tools, then explore resume stop " + stop.id()
                + " (ignore true: it is fine as it is).");
        return String.join("\n", lines);
    }

    /** The latest run's counts and where it is. */
    static String counts(JournalView view) {
        long minutes = view.ranFor(System.currentTimeMillis()).toMinutes();
        return view.count(ExploreEvent.NewScreen.class) + " screens found, "
                + view.count(ExploreEvent.Tried.class) + " presses, "
                + view.count(ExploreEvent.Refused.class) + " refused, "
                + view.count(ExploreEvent.Stopped.class) + " stops ("
                + view.count(ExploreEvent.Fixed.class) + " fixed, " + view.queue().size() + " parked), generation "
                + view.generation() + view.where().map(w -> ", on " + w).orElse("") + ", " + minutes + " min in.";
    }

    private static String ids(JournalView view) {
        List<String> ids = new ArrayList<>();
        view.open().ifPresent(s -> ids.add(s.id() + " (open)"));
        view.queue().forEach(s -> ids.add(s.id() + " (parked)"));
        return ids.isEmpty() ? "None is." : "There are " + String.join(", ", ids) + ".";
    }

    private static String noRun() {
        return "No explore run yet: run_bot entry \"" + SdkRuns.KEEP_EXPLORING + "\" starts one.";
    }

    private static void claude(Journal journal, String call) {
        journal.append(new ExploreEvent.Claude(call, System.currentTimeMillis()));
    }

    /**
     * Blocks until the journal holds an event past its first {@code seen} that is the run's (not a call of Claude's),
     * or {@code deadlineNanos}; false when the time ran out. A watch on the journal's folder wakes it, and it looks
     * again at least every second in case the watch missed a write.
     */
    private static boolean changed(Journal journal, int seen, long deadlineNanos) {
        Path folder = journal.folder();
        WatchService watch = null;
        try {
            Files.createDirectories(folder);
            watch = FileSystems.getDefault().newWatchService();
            folder.register(watch, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException | RuntimeException unwatched) {
            watch = close(watch);
        }
        long size = -1;
        try {
            while (true) {
                // The journal is read again only once it grew: a long one is not parsed every second.
                long now = size(journal);
                if (now != size) {
                    size = now;
                    List<ExploreEvent> all = journal.events();
                    if (all.subList(Math.min(seen, all.size()), all.size()).stream()
                            .anyMatch(e -> !(e instanceof ExploreEvent.Claude))) return true;
                }
                long left = deadlineNanos - System.nanoTime();
                if (left <= 0) return false;
                long nap = Math.min(left, NAP_NANOS);
                if (watch == null) {
                    TimeUnit.NANOSECONDS.sleep(nap);
                } else {
                    WatchKey key = watch.poll(nap, TimeUnit.NANOSECONDS);
                    if (key != null) {
                        key.pollEvents();
                        key.reset();
                    }
                }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            close(watch);
        }
    }

    /** The events file's size in bytes; -2 when it cannot be read. */
    private static long size(Journal journal) {
        try {
            return Files.size(journal.folder().resolve(Journal.EVENTS));
        } catch (IOException | RuntimeException unread) {
            return -2;
        }
    }

    private static WatchService close(WatchService watch) {
        try {
            if (watch != null) watch.close();
        } catch (IOException ignored) {
            // nothing held
        }
        return null;
    }
}
