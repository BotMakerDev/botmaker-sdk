// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.run.DevReload;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
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

    @Describe("dev mode: hold the open stop as a Discover fault, Discover's own mistake rather than the site's, until "
            + "the SDK is fixed")
    record Fault(@Describe("the open stop's id, as wait names it") String stop,
                 @Describe("one line: what Discover got wrong") String why) implements Action {
    }

    @Describe("dev mode, once the SDK's fix is installed: stop the run, reload the plugins at the rebuilt -SNAPSHOT "
            + "and start the run again from its memory, which replays the held fault; wait answers the replay")
    record ReloadSdk(@Describe("the held fault's id, as wait names it") String stop) implements Action {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(AssistantTool.named("explore")
            .describedAs("Keep exploring, the run that plays the game on its own and stops on what the bot does not "
                    + "know: wait on it, fix the stop with the map tools, resume. run_bot entry \""
                    + SdkRuns.KEEP_EXPLORING + "\" starts it. Dev mode: Discover's own mistake is held as a "
                    + "fault; fix and install the SDK, then reload_sdk replays it")
            .takesOneOf(Action.class).handledBy(ExploreTools::handle));

    private ExploreTools() {}

    static AgentReply handle(Action action, AgentContext context) {
        Path project = context == null || context.services() == null ? null : context.services().projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
        if (action instanceof ReloadSdk reload) return reloadSdk(Journal.of(project), reload.stop(), context.services());
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
            case Fault fault -> fault(journal, fault.stop(), fault.why(), STOP_WITHIN);
            // Reached only without the host's services: handle(Action, AgentContext) sends a reload to reloadSdk.
            case ReloadSdk ignored -> AgentReply.refused("A reload needs the host's services, which this call has not.");
        };
    }

    /**
     * Has the host restart the run on the rebuilt SDK, held on the Discover fault {@code stopId} (2026-11-19): the
     * host stops the run, reloads the plugins and starts {@value SdkRuns#KEEP_EXPLORING} again, which resumes from its
     * memory and replays the fault. Answers at once; refused, with nothing stopped, unless {@code stopId} is the fault
     * the run holds now, or with the host's words when it will not reload. The host closes this plugin's instance, so
     * nothing is done after its answer.
     */
    static AgentReply reloadSdk(Journal journal, String stopId, StudioServices services) {
        if (stopId == null || stopId.isBlank()) return AgentReply.refused("Name the held fault, as wait names it.");
        String id = stopId.strip();
        synchronized (RESUMING) {
            JournalView view = JournalView.of(journal);
            if (!view.running()) return AgentReply.refused(view.began() ? "The run has ended; run_bot entry \""
                    + SdkRuns.KEEP_EXPLORING + "\" starts another." : noRun());
            if (view.open().filter(s -> s.id().equals(id)).filter(view::held).isEmpty()) {
                return AgentReply.refused("No Discover fault " + id + " is held: reload_sdk replays the fault the run "
                        + "holds; a plain stop is fixed in the bot and resumed. " + ids(view));
            }
            Optional<RunEntry> entry = new SdkRuns().runs().stream()
                    .filter(e -> e.title().equals(SdkRuns.KEEP_EXPLORING)).findFirst();
            if (entry.isEmpty()) return AgentReply.refused("This SDK declares no " + SdkRuns.KEEP_EXPLORING + " run.");
            return switch (services.devReload(entry.get())) {
                case DevReload.Started started -> {
                    claude(journal, JournalView.RELOAD_SDK + " " + id);
                    yield AgentReply.text("Reloading the SDK: the run stops and starts again on the new build, then "
                            + "replays " + id + "; explore wait answers the replay. A wait that finds the run ended "
                            + "means the restart failed: the status line and the core's log say why.");
                }
                case DevReload.Refused(String why) -> AgentReply.refused("The host did not reload: " + why);
            };
        }
    }

    /**
     * Has the run hold its open stop {@code stopId} as a Discover fault, {@code why} one line, and answers once it
     * did (2026-11-19). Refused when the run plays on a released SDK: a fault is for whoever edits the SDK.
     */
    static AgentReply fault(Journal journal, String stopId, String why, Duration within) {
        if (stopId == null || stopId.isBlank()) return AgentReply.refused("Name the stop to hold, as wait names it.");
        if (why == null || why.isBlank()) return AgentReply.refused("Say in one line what Discover got wrong.");
        String id = stopId.strip();
        String line = why.strip().replaceAll("\\s+", " ");
        synchronized (RESUMING) {
            JournalView view = JournalView.of(journal);
            if (!view.running()) return AgentReply.refused(view.began() ? "The run has ended; run_bot entry \""
                    + SdkRuns.KEEP_EXPLORING + "\" starts another." : noRun());
            if (!view.dev()) {
                return AgentReply.refused("This run plays on a released SDK: a Discover fault is for whoever edits the "
                        + "SDK, in a dev-mode project on a local -SNAPSHOT build. Fix the bot, or resume with ignore.");
            }
            Optional<com.botmaker.sdk.internal.explore.Stop> open = view.open().filter(s -> s.id().equals(id));
            if (open.isEmpty()) {
                return AgentReply.refused("No stop " + id + " is open: only the stop the run waits on is held as a "
                        + "fault. " + ids(view));
            }
            if (view.held(open.get())) return AgentReply.text(id + " is held as a Discover fault already.");
            int seen = view.all().size();
            claude(journal, "fault " + id);
            journal.command("fault " + id + " " + line);
            long deadline = System.nanoTime() + within.toNanos();
            while (true) {
                List<ExploreEvent> all = journal.events();
                for (ExploreEvent event : all.subList(Math.min(seen, all.size()), all.size())) {
                    if (event instanceof ExploreEvent.Faulted faulted && faulted.stopId().equals(id)) {
                        return AgentReply.text("Held " + id + " as a Discover fault: " + line + ". " + FIX_THE_SDK);
                    }
                }
                if (!JournalView.of(journal).running()) {
                    return AgentReply.refused("The run ended before it held " + id + ".");
                }
                if (!changed(journal, all.size(), deadline)) {
                    // A fault the run never took may be lost (the stop moved on, or a later command replaced it).
                    return AgentReply.refused("The run did not hold " + id + " in " + within.toSeconds()
                            + " s. Wait, and call fault again on the stop it waits on.");
                }
            }
        }
    }

    /** What Claude does with a held fault. */
    private static final String FIX_THE_SDK = "Fix the SDK with its test and install it; explore reload_sdk then "
            + "replays the stop on the new build.";

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
            if (now.isPresent()) {
                // Said now: the next wait reports only what was found after this answer.
                claude(journal, JournalView.WAIT);
                return now.get();
            }
        }
    }

    /** What a wait answers with now, if anything: the open stop, the oldest parked, new screens, or the run's end. */
    private static Optional<AgentReply> answer(JournalView view) {
        List<String> screens = view.newScreensSinceWait();
        String found = screens.isEmpty() ? "" : "\nNew screens since the last wait: " + String.join(", ", screens) + ".";
        // A stop the run settled off the page's tree is written and resumed by the plugin side, not by Claude; a held
        // fault being replayed is answered by its replay.
        if (view.open().isPresent() && !view.settled(view.open().get()) && !view.replaying(view.open().get())) {
            return Optional.of(AgentReply.text("The run waits on this stop:\n" + whole(view.open().get(), view) + found));
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
                view.open().map(s -> (view.held(s) ? "Holds the Discover fault " : "Waits on ") + s.id() + ": "
                        + s.missing() + ". ").orElse("Running. "));
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
            if (!ignore && view.open().filter(s -> s.id().equals(id)).filter(view::held).isPresent()) {
                return AgentReply.refused(id + " is a Discover fault: " + ExploreLoop.FAULT_RESUMES + ".");
            }
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

    /**
     * {@code stop} whole as {@link #whole(com.botmaker.sdk.internal.explore.Stop)} says it, and, held as a Discover
     * fault in {@code view}, what found it, its evidence, the SDK it plays on, and how it goes on.
     */
    static String whole(com.botmaker.sdk.internal.explore.Stop stop, JournalView view) {
        if (!view.held(stop)) return whole(stop);
        List<String> fault = new ArrayList<>();
        view.fault(stop.id()).ifPresent(faulted -> {
            fault.add("Discover fault (" + (faulted.source().isEmpty() ? "unknown source" : faulted.source()) + "): "
                    + faulted.why());
            if (!faulted.evidence().isEmpty()) fault.add("Evidence: " + faulted.evidence());
            if (!faulted.sdkVersion().isEmpty()) fault.add("SDK: " + faulted.sdkVersion());
        });
        fault.add(FIX_THE_SDK + " Resume ignore true drops it as a misreading.");
        return whole(stop, fault);
    }

    /** {@code stop} whole, as Claude fixes it from: what is missing, the frame, what was read and pressed. */
    static String whole(com.botmaker.sdk.internal.explore.Stop stop) {
        return whole(stop, List.of("Fix it with the map tools, then explore resume stop " + stop.id()
                + " (ignore true: it is fine as it is)."));
    }

    /** {@code stop} whole, ending with {@code end}: how Claude goes on from it. */
    private static String whole(com.botmaker.sdk.internal.explore.Stop stop, List<String> end) {
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
        lines.addAll(end);
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
                + view.generation() + view.where().map(w -> ", on " + w).orElse("") + ", " + minutes + " min in."
                + build(view.build());
    }

    /**
     * How far the map is built, on a line of its own: {@code map: 4 built (median 1m40s), 2 open: PRICING 3m12s set
     * aside (9 untried)}.
     */
    static String build(JournalView.Build build) {
        if (build.built().isEmpty() && build.open().isEmpty()) return "";
        StringBuilder line = new StringBuilder("\nmap: ").append(build.built().size()).append(" built");
        if (!build.built().isEmpty()) line.append(" (median ").append(JournalView.took(build.median().toMillis())).append(")");
        if (!build.open().isEmpty()) {
            line.append(", ").append(build.open().size()).append(" open: ").append(build.open().stream()
                    .map(b -> b.screen() + " " + JournalView.took(b.ms())
                            + (build.aside().contains(b.screen()) ? " set aside" : "")
                            + " (" + String.join(", ", b.open()) + ")")
                    .collect(Collectors.joining("; ")));
        }
        return line.append(".").toString();
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
