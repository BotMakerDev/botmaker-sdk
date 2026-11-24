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
import com.botmaker.sdk.internal.explore.FindingKind;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.PressTag;
import com.botmaker.sdk.internal.explore.StopRole;
import com.botmaker.sdk.internal.vision.Images;
import com.botmaker.sdk.plugin.run.SdkRuns;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
    record Wait(@Describe(value = "the longest to wait; blank: 600", optional = true) Integer seconds,
                @Describe(value = RUN_ID, optional = true) String run,
                @Describe(value = "cartographer or mechanic: only that role's stops; blank: all but the user's",
                        optional = true) String role)
            implements Action {
    }

    @Describe("hand a stop to the user, for a verdict that can spend real money or a call only they can make; the "
            + "run parks it and goes on")
    record Ask(@Describe("the stop's id, from wait") String stop,
               @Describe("one line: what the user is to decide") String question) implements Action {
    }

    @Describe("the run's id, counts, where it is, and its open stop")
    record Status(@Describe(value = RUN_ID, optional = true) String run) implements Action {
    }

    private static final String RUN_ID = "the run's id, as status names it; blank: the latest run";

    @Describe("the parked stops, oldest first")
    record Queue() implements Action {
    }

    @Describe("load the fixed bot and go on from a stop; answers once the run reloaded, or why it refused")
    record Resume(@Describe("the stop's id, from wait") String stop,
                  @Describe(value = "true: what it stopped on is fine, never raised again", optional = true)
                  Boolean ignore) implements Action {
    }

    @Describe("end the run")
    record Stop() implements Action {
    }

    @Describe("dev mode: hold the open stop as a Discover fault, Discover's own mistake, not the site's, until "
            + "the SDK is fixed")
    record Fault(@Describe("the open stop's id, from wait") String stop,
                 @Describe("one line: what Discover got wrong") String why) implements Action {
    }

    @Describe("dev mode, once the SDK's fix is installed: stop the run, reload the plugins at the rebuilt -SNAPSHOT "
            + "and start the run again from its memory, which replays the held fault; wait answers the replay")
    record ReloadSdk(@Describe("the held fault's id, from wait") String stop) implements Action {
    }

    @Describe("a stop whole by its id, open or parked, with the run's goal and the frame it was met on")
    record Look(@Describe("the stop's id") String stop) implements Action {
    }

    @Describe("rank the presses of a stop's screen by how likely each leads to the goal, and tag how safe each is; "
            + "the run presses the best first, and never presses a risky or destructive one alone")
    record RankPresses(@Describe("the stop's id") String stop,
                       @Describe("the screen's name, only for a stop on a screen the map lacked, once apply_edit named "
                               + "it; leave it out otherwise") String screen,
                       @Describe("each press of the screen, with its rank and tag") List<Rank> presses)
            implements Action {
    }

    /** One press ranked by the Reader. */
    record Rank(@Describe("the press: an input's name, or the label read on it") String press,
                @Describe("0 to 10: how likely it leads to the goal, 10 the likeliest") Integer rank,
                @Describe("safe, risky (an ad, an outside link, a permission) or destructive (sign out, delete, "
                        + "reset, a purchase)") String tag) {
    }

    @Describe("write an edit of the map through the run's one writer, applied once by its id however often it is sent; "
            + "answers once it was written, or why not")
    record ApplyEdit(@Describe("the stop's id the edit is for") String stop,
                     @Describe("an id of your own for this edit, unique in the run: the same id is written once")
                     String editId,
                     @Describe("the edit_map call's arguments as a JSON object, with its action") String edit)
            implements Action {
    }

    @Describe("the run's presses, ranks, next presses and findings")
    record Progress(@Describe(value = RUN_ID, optional = true) String run) implements Action {
    }

    @Describe("say what is wrong: drift lowers the screen's presses, stuck ends the run")
    record RecordFinding(@Describe("drift, stuck, bad_map or sdk_fault") String kind,
                         @Describe("what is wrong") String why,
                         @Describe(value = "a screen, frame or press", optional = true) String evidence,
                         @Describe(value = "drift: the screen lost under", optional = true) String screen,
                         @Describe(value = RUN_ID, optional = true) String run)
            implements Action {
    }

    @Describe("the goal looks reached: the run checks mark and ends done if it holds")
    record ProposeDone(@Describe("a text shown once the goal is reached") String mark) implements Action {
    }

    /** The finding kinds the Evaluator says; a done without a witness is the run's own. */
    private static final Set<FindingKind> SAID = EnumSet.of(FindingKind.DRIFT, FindingKind.STUCK, FindingKind.BAD_MAP,
            FindingKind.SDK_FAULT);
    /** How much a drift lowers each open press under its screen. */
    static final int DRIFT_DROP = 3;
    /** How long a {@code propose_done} waits for the run to check its mark. */
    static final Duration DONE_WITHIN = Duration.ofSeconds(30);

    /** How long an {@code apply_edit} waits for the run's writer to carry it out. */
    static final Duration EDIT_WITHIN = Duration.ofSeconds(60);

    static final List<AssistantTool<?>> TOOLS = List.of(AssistantTool.named("explore")
            .describedAs("Keep exploring, the run that plays the game on its own and stops on what the bot does not "
                    + "know: wait on it, fix the stop with the map tools, resume. run_bot entry \""
                    + SdkRuns.KEEP_EXPLORING + "\" starts it. Leave the user's stops: they answer them")
            .takesOneOf(Action.class).handledBy(ExploreTools::handle));

    private ExploreTools() {}

    static AgentReply handle(Action action, AgentContext context) {
        Path project = context == null || context.services() == null ? null : context.services().projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
        String run = switch (action) {
            case Wait wait -> wait.run();
            case Status status -> status.run();
            case Progress progress -> progress.run();
            case RecordFinding finding -> finding.run();
            default -> null;
        };
        Optional<Journal> journal = run == null || run.isBlank() ? Journal.latest(project)
                : Journal.of(project, run.strip());
        if (journal.isEmpty()) {
            List<String> runs = Journal.runs(project);
            return AgentReply.refused(runs.isEmpty() ? noRun() : "No explore run " + run.strip() + ". The runs, "
                    + "oldest first: " + String.join(", ", runs.subList(Math.max(0, runs.size() - 10), runs.size()))
                    + ".");
        }
        if (action instanceof ReloadSdk reload) return reloadSdk(journal.get(), reload.stop(), context.services());
        return handle(action, journal.get());
    }

    static AgentReply handle(Action action, Journal journal) {
        return switch (action) {
            case Wait wait -> {
                StopRole role = wait.role() == null || wait.role().isBlank() ? null : StopRole.fromId(wait.role());
                if (role == StopRole.UNKNOWN) {
                    yield AgentReply.refused("No role " + wait.role().strip() + ": cartographer, mechanic, or blank.");
                }
                yield waitFor(journal, Duration.ofSeconds(wait.seconds() == null || wait.seconds() <= 0
                        ? WAIT_SECONDS : wait.seconds()), role);
            }
            case Ask ask -> ask(journal, ask.stop(), ask.question());
            case Status ignored -> status(journal);
            case Queue ignored -> queue(journal);
            case Resume resume -> resume(journal, resume.stop(), Boolean.TRUE.equals(resume.ignore()), RESUME_WITHIN);
            case Stop ignored -> stop(journal, STOP_WITHIN);
            case Fault fault -> fault(journal, fault.stop(), fault.why(), STOP_WITHIN);
            case Look look -> look(journal, look.stop());
            case RankPresses rank -> rank(journal, rank.stop(), rank.screen(), rank.presses());
            case ApplyEdit edit -> applyEdit(journal, edit.stop(), edit.editId(), edit.edit(), EDIT_WITHIN);
            case Progress ignored -> progress(journal);
            case RecordFinding finding -> recordFinding(journal, finding.kind(), finding.why(), finding.evidence(),
                    finding.screen());
            case ProposeDone done -> proposeDone(journal, done.mark(), DONE_WITHIN);
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
            if (view.roleOf(open.get()) == StopRole.USER) return AgentReply.refused(id + " is the user's: " + USERS_STOP);
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
                JournalView now = JournalView.of(journal);
                if (!now.running()) {
                    return AgentReply.refused("The run ended before it held " + id + ".");
                }
                // The run went on from the stop before it read the command (a settled stop resumes once written):
                // between stops a fault is dropped, so nothing is left to wait for.
                if (now.open().filter(s -> s.id().equals(id)).isEmpty()) {
                    return AgentReply.refused("The run went on from " + id + " before it held it. " + ids(now)
                            + " Call fault on the stop it waits on, or on the next one its place raises.");
                }
                if (!changed(journal, all.size(), deadline)) {
                    // A fault the run never took may be lost (the stop moved on, or a later command replaced it).
                    return AgentReply.refused("The run did not hold " + id + " in " + within.toSeconds()
                            + " s. Wait, and call fault again on the stop it waits on.");
                }
            }
        }
    }

    /** Why Claude leaves a stop whose role is the user's. */
    private static final String USERS_STOP = "the user decides it from the Explore tab. Leave it.";

    /** What Claude does with a held fault. */
    private static final String FIX_THE_SDK = "Fix the SDK with its test and install it; explore reload_sdk then "
            + "replays the stop on the new build.";

    /**
     * Answers at once with the open stop, else the oldest parked one, else the screens found since the last wait;
     * otherwise blocks until the run stops, finds a screen or ends, or {@code within} passes ("still running").
     */
    static AgentReply waitFor(Journal journal, Duration within) {
        return waitFor(journal, within, null);
    }

    /**
     * Blocks until the run has something to say, within {@code within}; {@code role}, when not null, keeps to the
     * stops that role takes (2026-11-21), so a Cartographer's session never sees a fault or the user's stops.
     */
    static AgentReply waitFor(Journal journal, Duration within, StopRole role) {
        JournalView view = JournalView.of(journal);
        if (!view.began()) return AgentReply.refused(noRun());
        Optional<AgentReply> now = answer(view, role);
        claude(journal, JournalView.waitCall(role));
        if (now.isPresent()) return now.get();
        long deadline = System.nanoTime() + within.toNanos();
        int seen = view.all().size();
        while (true) {
            if (!changed(journal, seen, deadline)) {
                JournalView last = JournalView.of(journal);
                // A run waiting on the user's stop is said, so Claude knows why nothing comes.
                String user = last.open().filter(s -> last.roleOf(s) == StopRole.USER)
                        .map(s -> " It waits on " + s.id() + ", the user's: they answer it from the Explore tab.")
                        .orElse("");
                return AgentReply.text("Still running after " + within.toSeconds() + " s: " + counts(last) + user);
            }
            view = JournalView.of(journal);
            seen = view.all().size();
            now = answer(view, role);
            if (now.isPresent()) {
                // Said now: the next wait reports only what was found after this answer.
                claude(journal, JournalView.waitCall(role));
                return now.get();
            }
        }
    }

    /** What a wait answers with now, if anything: the open stop, the oldest parked, new screens, or the run's end. */
    private static Optional<AgentReply> answer(JournalView view, StopRole role) {
        List<String> screens = view.newScreensSinceWait();
        String found = screens.isEmpty() ? "" : "\nNew screens since the last wait: " + String.join(", ", screens) + ".";
        // A wait with no role takes every stop but the user's, which Claude never answers.
        Predicate<com.botmaker.sdk.internal.explore.Stop> takes = s -> role == null
                ? view.roleOf(s) != StopRole.USER : view.roleOf(s) == role;
        // A stop the run settled off the page's tree is written and resumed by the plugin side, not by Claude; a held
        // fault being replayed is answered by its replay.
        if (view.open().filter(takes).isPresent() && !view.settled(view.open().get())
                && !view.replaying(view.open().get())) {
            return Optional.of(AgentReply.text("The run waits on this stop:\n" + whole(view.open().get(), view) + found));
        }
        if (view.ended().isPresent()) {
            return Optional.of(AgentReply.text("The run ended (" + view.ended().get().displayName() + "): "
                    + counts(view) + found));
        }
        // Said once each time one is parked, so a stop left for later never keeps a wait from blocking.
        List<com.botmaker.sdk.internal.explore.Stop> parked = view.queue().stream().filter(takes).toList();
        if (!parked.isEmpty() && view.parkedSinceWait(role)) {
            return Optional.of(AgentReply.text("No stop open; the run goes on. The oldest of " + parked.size()
                    + " parked (queue lists them):\n" + whole(parked.getFirst()) + found));
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
        return AgentReply.text("Run " + journal.runId() + ". " + state + counts(view));
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
     * Hands the open or a parked stop {@code stopId} to the user with {@code question} (2026-11-21): written to the
     * journal for the Explore tab's queue, and the open stop parked so the run goes on. Answers at once.
     */
    static AgentReply ask(Journal journal, String stopId, String question) {
        if (stopId == null || stopId.isBlank()) return AgentReply.refused("Name the stop to hand over, as wait names it.");
        if (question == null || question.isBlank()) return AgentReply.refused("Say in one line what the user decides.");
        String id = stopId.strip();
        synchronized (RESUMING) {
            JournalView view = JournalView.of(journal);
            if (!view.running()) return AgentReply.refused(view.began() ? "The run has ended." : noRun());
            boolean open = view.open().filter(s -> s.id().equals(id)).isPresent();
            if (open && view.held(view.open().get())) {
                return AgentReply.refused(id + " is a Discover fault, the Mechanic's: it is not the user's.");
            }
            Optional<com.botmaker.sdk.internal.explore.Stop> stop = open ? view.open() : view.parked(id);
            if (stop.isEmpty()) return AgentReply.refused("No stop " + id + " is open or parked. " + ids(view));
            if (stop.get().role() == StopRole.USER) {
                return AgentReply.refused(id + " is the user's already: the Explore tab lists it for them.");
            }
            if (view.settled(stop.get())) return AgentReply.refused(id + " is settled off the page's tree: leave it.");
            claude(journal, "ask " + id);
            journal.append(new ExploreEvent.Asked(id, question.replaceAll("\\s+", " ").strip(),
                    System.currentTimeMillis()));
            if (open) journal.command("park " + id);
            return AgentReply.text("Handed " + id + " to the user; " + (open ? "the run parks it and goes on."
                    : "it stays parked.") + " Leave it: they resume or ignore it from the Explore tab.");
        }
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
            // The user's stop is theirs alone: the Explore tab's buttons write its command, never Claude.
            if (view.open().filter(s -> s.id().equals(id)).or(() -> view.parked(id))
                    .filter(s -> view.roleOf(s) == StopRole.USER).isPresent()) {
                return AgentReply.refused(id + " is the user's: " + USERS_STOP);
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
    static String whole(com.botmaker.sdk.internal.explore.Stop stop, List<String> end) {
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

    /** The stop {@code id} of {@code view}: the open one, or one parked. */
    private static Optional<com.botmaker.sdk.internal.explore.Stop> stopOf(JournalView view, String id) {
        return view.open().filter(s -> s.id().equals(id)).or(() -> view.parked(id));
    }

    /**
     * The stop {@code stopId} whole, open or parked, with the run's goal, and the frame it was met on as a picture: what
     * a Reader works from (spec 2026-11-24-explore-agents-design § The crew).
     */
    static AgentReply look(Journal journal, String stopId) {
        if (stopId == null || stopId.isBlank()) return AgentReply.refused("Name the stop to look at.");
        String id = stopId.strip();
        JournalView view = JournalView.of(journal);
        if (!view.began()) return AgentReply.refused(noRun());
        Optional<com.botmaker.sdk.internal.explore.Stop> stop = stopOf(view, id);
        if (stop.isEmpty()) return AgentReply.refused("No stop " + id + " is open or parked. " + ids(view));
        claude(journal, "look " + id);
        String goal = view.goal().isBlank() ? "Goal: none; map what the screen is." : "Goal: " + view.goal() + ".";
        AgentReply text = AgentReply.text(goal + "\n" + whole(stop.get(), view));
        return frame(stop.get().framePath()).map(image -> text.and(AgentReply.image(Images.scaled(image,
                Frames.fit(image.getWidth(), image.getHeight(), Frames.LOOK_EDGE))))).orElse(text);
    }

    /**
     * What the Evaluator judges the run by: its goal, where it is, its latest presses with their ranks, the presses it
     * may make next, best first, and the findings said so far.
     */
    static AgentReply progress(Journal journal) {
        JournalView view = JournalView.of(journal);
        if (!view.began()) return AgentReply.refused(noRun());
        claude(journal, "progress");
        StringBuilder text = new StringBuilder(view.goal().isBlank() ? "Goal: none." : "Goal: " + view.goal() + ".")
                .append('\n').append(view.ended().map(end -> "Ended (" + end.displayName() + "). ").orElse("Running. "))
                .append(counts(view)).append(view.where().map(w -> " On " + w + ".").orElse(""));
        Map<String, ExploreEvent.Ranked> ranks = new HashMap<>();
        List<ExploreEvent.Tried> presses = new ArrayList<>();
        for (ExploreEvent event : view.events()) {
            if (event instanceof ExploreEvent.Ranked r) ranks.put(r.screen() + "." + r.press(), r);
            if (event instanceof ExploreEvent.Tried t) presses.add(t);
        }
        text.append("\n\nLatest presses, oldest first:");
        presses.subList(Math.max(0, presses.size() - PROGRESS_PRESSES), presses.size()).forEach(t -> text.append("\n- ")
                .append(t.screen()).append(" › ").append(t.by())
                .append(Optional.ofNullable(ranks.get(t.screen() + "." + t.by())).map(r -> " (rank " + r.rank() + ")")
                        .orElse(""))
                .append(t.arrival() == null ? "" : " → " + t.arrival()));
        if (presses.isEmpty()) text.append(" none yet.");
        List<ExploreEvent.Ranked> next = view.openRanks().stream()
                .sorted(Comparator.comparingInt(ExploreEvent.Ranked::rank).reversed()).limit(PROGRESS_PRESSES).toList();
        text.append("\n\nMay press next, best first:");
        next.forEach(r -> text.append("\n- ").append(r.screen()).append(" › ").append(r.press()).append(" (rank ")
                .append(r.rank()).append(", ").append(r.tag().id()).append(')'));
        if (next.isEmpty()) text.append(" nothing ranked.");
        List<ExploreEvent.Finding> findings = view.findings();
        text.append("\n\nFindings:");
        findings.forEach(f -> text.append("\n- ").append(f.kind().id()).append(": ").append(f.why())
                .append(f.evidence().isBlank() ? "" : " (" + f.evidence() + ")"));
        if (findings.isEmpty()) text.append(" none.");
        return AgentReply.text(text.toString());
    }

    /** How many presses {@code progress} lists, of those made and of those to make. */
    private static final int PROGRESS_PRESSES = 15;

    /**
     * Writes the Evaluator's finding of {@code kind}: a {@link FindingKind#DRIFT} lowers by {@value #DRIFT_DROP} each
     * open press of {@code screen} and of the screens the run reached from it, so the run turns elsewhere; a
     * {@link FindingKind#STUCK} ends the run (the Coordinator's). An ended run takes one only from its end's evaluation.
     */
    static AgentReply recordFinding(Journal journal, String kind, String why, String evidence, String screen) {
        FindingKind said = FindingKind.fromId(kind);
        if (!SAID.contains(said)) return AgentReply.refused("A finding is drift, stuck, bad_map or sdk_fault.");
        if (why == null || why.isBlank()) return AgentReply.refused("Say in one line what is wrong.");
        String lost = screen == null ? "" : screen.strip();
        if (said == FindingKind.DRIFT && lost.isEmpty()) {
            return AgentReply.refused("A drift names the screen the run is lost under, as screen.");
        }
        JournalView view = JournalView.of(journal);
        if (!view.began()) return AgentReply.refused(noRun());
        // The Evaluator's alone: only an evaluation under way acts on it, a stuck above all.
        if (EvaluatorTrigger.held(view).isEmpty()) {
            return AgentReply.refused(view.running() ? "record_finding is the Evaluator's, and no evaluation is under "
                    + "way." : "The run has ended.");
        }
        if (said == FindingKind.STUCK && !view.running()) {
            return AgentReply.refused("The run has ended already: a stuck ends nothing now.");
        }
        claude(journal, "record_finding " + said.id());
        long at = System.currentTimeMillis();
        String shown = evidence == null || evidence.isBlank() ? lost : evidence.strip();
        journal.append(new ExploreEvent.Finding(said, shown, why.strip(), at));
        if (said != FindingKind.DRIFT) {
            return AgentReply.text("Recorded " + said.id() + (said == FindingKind.STUCK ? ": the run ends on it." : "."));
        }
        Set<String> under = view.under(lost);
        int lowered = 0;
        for (ExploreEvent.Ranked r : view.openRanks()) {
            if (!under.contains(r.screen())) continue;
            // A press ranked toward the goal stays ranked toward it (1 at least); one that would not move is left.
            int rank = r.rank() > 0 ? Math.max(1, r.rank() - DRIFT_DROP) : r.rank();
            if (rank == r.rank()) continue;
            journal.append(new ExploreEvent.Ranked(r.screen(), r.press(), rank, r.tag(), at));
            lowered++;
        }
        return AgentReply.text("Recorded drift: " + lowered + " press(es) under " + lost + " rank lower; the run "
                + "goes on.");
    }

    /**
     * Asks the run to check {@code mark} on its screen, its goal's witness, and answers what it found within
     * {@code within}: a witness ends the run done, none is a {@link FindingKind#NO_WITNESS} finding and the run goes on.
     */
    static AgentReply proposeDone(Journal journal, String mark, Duration within) {
        JournalView view = JournalView.of(journal);
        if (!view.running()) return AgentReply.refused(view.began() ? "The run has ended." : noRun());
        // A paused run reads no done: it would be dropped, never checked.
        Optional<String> waitsOn = view.open().map(stop -> stop.id());
        if (waitsOn.isPresent()) {
            return AgentReply.refused("The run waits on " + waitsOn.get() + ": propose done once it goes on.");
        }
        String shown = mark == null ? "" : mark.strip();
        long witnessed = view.count(ExploreEvent.Witnessed.class);
        long refused = view.findings().stream().filter(f -> f.kind() == FindingKind.NO_WITNESS).count();
        claude(journal, "propose_done");
        journal.command(shown.isEmpty() ? "done" : "done " + shown);
        long deadline = System.nanoTime() + within.toNanos();
        while (System.nanoTime() < deadline) {
            JournalView now = JournalView.of(journal);
            if (now.count(ExploreEvent.Witnessed.class) > witnessed) {
                return AgentReply.text("Witnessed: the run ends done.");
            }
            List<ExploreEvent.Finding> missed = now.findings().stream()
                    .filter(f -> f.kind() == FindingKind.NO_WITNESS).toList();
            if (missed.size() > refused) {
                return AgentReply.text("Not witnessed: " + missed.getLast().why() + ". The run goes on.");
            }
            try {
                Thread.sleep(Math.min(100, Math.max(1, (deadline - System.nanoTime()) / 1_000_000)));
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return AgentReply.text("Sent; not checked yet: the run checks the mark at its next press.");
    }

    /** The frame saved at {@code path}; empty when there is none or it cannot be read. */
    private static Optional<BufferedImage> frame(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(ImageIO.read(Path.of(path).toFile()));
        } catch (IOException | RuntimeException unread) {
            return Optional.empty();
        }
    }

    /**
     * Writes one {@link ExploreEvent.Ranked} per press of {@code presses}, on the screen of the stop {@code stopId}:
     * the Explorer takes them into its frontier. A stop met on a screen the map lacked takes {@code named}, the name
     * the Reader gave it; a mapped one keeps its own. Nothing is written when one press is not said whole.
     */
    static AgentReply rank(Journal journal, String stopId, String named, List<Rank> presses) {
        if (stopId == null || stopId.isBlank()) return AgentReply.refused("Name the stop whose presses these are.");
        if (presses == null || presses.isEmpty()) return AgentReply.refused("Give at least one press.");
        String id = stopId.strip();
        JournalView view = JournalView.of(journal);
        if (!view.running()) return AgentReply.refused(view.began() ? "The run has ended." : noRun());
        Optional<com.botmaker.sdk.internal.explore.Stop> stop = stopOf(view, id);
        if (stop.isEmpty()) return AgentReply.refused("No stop " + id + " is open or parked. " + ids(view));
        String screen = stop.get().screen();
        if (screen == null || screen.isBlank()) {
            if (named == null || named.isBlank()) {
                return AgentReply.refused(id + " is on a screen the map lacked: name it with apply_edit first, then "
                        + "give that name as screen.");
            }
            screen = named.strip();
        }
        List<String> wrong = new ArrayList<>();
        for (Rank rank : presses) {
            if (rank == null || rank.press() == null || rank.press().isBlank()) wrong.add("a press with no name");
            else if (rank.rank() == null || rank.rank() < 0 || rank.rank() > 10) wrong.add(rank.press() + ": rank 0 to 10");
            else if (PressTag.fromId(rank.tag()) == PressTag.UNKNOWN) wrong.add(rank.press() + ": tag safe, risky or destructive");
        }
        if (!wrong.isEmpty()) return AgentReply.refused("Nothing was written: " + String.join("; ", wrong) + ".");
        claude(journal, "rank_presses " + id);
        long at = System.currentTimeMillis();
        for (Rank rank : presses) {
            journal.append(new ExploreEvent.Ranked(screen, rank.press().strip(), rank.rank(), PressTag.fromId(rank.tag()), at));
        }
        return AgentReply.text("Ranked " + presses.size() + " press(es) of " + screen + "; the run presses the best "
                + "first.");
    }

    /**
     * Queues {@code edit}, the arguments of an {@code edit_map} call, as the edit {@code editId} of the stop
     * {@code stopId}, and answers once the run's one writer ({@link EditWriter}) carried it out, within {@code within}.
     * An id queued already is not queued again: its first answer is given.
     */
    static AgentReply applyEdit(Journal journal, String stopId, String editId, String edit, Duration within) {
        if (editId == null || editId.isBlank()) return AgentReply.refused("Give the edit an id of your own.");
        String id = editId.strip();
        JournalView view = JournalView.of(journal);
        Optional<ExploreEvent.EditQueued> known = view.queuedOf(id);
        if (known.isPresent()) {
            // One id is one edit: another edit sent by it would hear the first one's answer and never be written.
            if (edit != null && !edit.strip().equals(known.get().edit().strip())) {
                return AgentReply.refused("The id " + id + " is another edit's already: give this one an id of its own.");
            }
            if (!view.applied(id) && !view.running()) {
                return AgentReply.refused("The run has ended; " + id + " was never written.");
            }
        } else {
            if (!view.running()) return AgentReply.refused(view.began() ? "The run has ended." : noRun());
            if (stopId == null || stopId.isBlank()) return AgentReply.refused("Name the stop the edit is for.");
            if (stopOf(view, stopId.strip()).isEmpty()) {
                return AgentReply.refused("No stop " + stopId.strip() + " is open or parked. " + ids(view));
            }
            if (edit == null || EditWriter.arguments(edit).filter(a -> a.containsKey(AssistantTool.ACTION)).isEmpty()) {
                return AgentReply.refused("The edit is edit_map's arguments as a JSON object, its action among them.");
            }
            claude(journal, "apply_edit " + id);
            journal.append(new ExploreEvent.EditQueued(id, stopId.strip(), "edit_map", edit, System.currentTimeMillis()));
        }
        long deadline = System.nanoTime() + within.toNanos();
        while (true) {
            // Counted before the look, so a write landing between the two still wakes the wait.
            JournalView now = JournalView.of(journal);
            Optional<ExploreEvent.EditApplied> done = now.appliedOf(id);
            if (done.isPresent()) {
                String said = done.get().said().isBlank() ? "" : ": " + done.get().said();
                return done.get().ok() ? AgentReply.text("Written " + id + said)
                        : AgentReply.refused("Not written " + id + said);
            }
            if (!changed(journal, now.all().size(), deadline)) {
                return AgentReply.text("Queued " + id + "; the run's writer has not written it in " + within.toSeconds()
                        + " s. Send it again by its id to hear how it went.");
            }
        }
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
        JournalView.Reader reader = new JournalView.Reader(journal);
        try {
            while (true) {
                // The journal is read again only once it grew, and then only its new lines.
                long now = size(journal);
                if (now != size) {
                    size = now;
                    List<ExploreEvent> all = reader.view().all();
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
