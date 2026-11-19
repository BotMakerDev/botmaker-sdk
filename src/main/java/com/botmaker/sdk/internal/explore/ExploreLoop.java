// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Verdict;
import com.botmaker.sdk.internal.bot.BotConstants.InputName;
import com.botmaker.sdk.internal.bot.InstalledRun;
import com.botmaker.sdk.internal.explore.tree.Effect;
import com.botmaker.sdk.internal.explore.tree.FormRules;
import com.botmaker.sdk.internal.explore.tree.Locator;
import com.botmaker.sdk.internal.explore.tree.Overlay;
import com.botmaker.sdk.internal.explore.tree.RouteGuess;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.Conditions;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.MapWalk;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.FormProbe;
import com.botmaker.shared.web.FormRule;
import com.botmaker.shared.web.Guard;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.awt.Rectangle;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * <em>Keep exploring</em>'s loop (spec §1–3): each round, where the game is, everything read on it, one press chosen,
 * guarded, pressed and judged. What the bot does not know stops the run and hands the game to Claude through the
 * {@link Journal}; a resume reloads the bot and checks the stop is gone, and a stop nobody takes up in time is parked
 * and its place avoided.
 *
 * <p>Pure over a {@link Play} and a clock, as {@link MapWalk} is over its moves: tests script both. Only a stop of the
 * run ({@code Bot.stop()}) leaves it by an exception.
 */
public final class ExploreLoop {

    /** Where a press came from: the feed shows it. */
    public enum Mode {
        /** An input of the screen not tried yet. */
        UNTRIED("untried", "Untried input"),
        /** A round of the bot's plan. */
        PLAN("plan", "Plan"),
        /** A walk to the nearest screen with an input not tried yet. */
        WALK("walk", "Walk to untried"),
        /** A mode this SDK does not know, read from a journal a newer one wrote. */
        @JsonEnumDefaultValue
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        Mode(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** The mode whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
        public static Mode fromId(String id) {
            for (Mode mode : values()) {
                if (mode.id.equals(id)) return mode;
            }
            return UNKNOWN;
        }
    }

    /** Why the loop ended. */
    public enum End {
        /** The plugin side asked it to stop. */
        STOPPED("stopped", "Stopped"),
        /** Its budget ({@link Settings#hours}, {@link Settings#maxScreens}) ran out. */
        BUDGET("budget", "Budget spent"),
        /** It threw: the bot crashed, or the run was stopped from Studio. Only the journal says it. */
        BROKE("broke", "Crashed or stopped from Studio"),
        /** An end this SDK does not know, read from a journal a newer one wrote. */
        @JsonEnumDefaultValue
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        End(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** The end whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
        public static End fromId(String id) {
            for (End end : values()) {
                if (end.id.equals(id)) return end;
            }
            return UNKNOWN;
        }
    }

    /**
     * How long a stop waits before it is parked, how long with no press is stuck, how many plan rounds with no new
     * screen before a walk to an untried input, one press's time, the whole run's (null: no end), how often a
     * paused run looks for a command, and the screens it is on before it ends, each counted once (0: no end).
     */
    public record Settings(Duration parkAfter, Duration stuckAfter, int planRoundsWithoutNew, Duration edge,
                           Duration hours, Duration poll, int maxScreens) {

        public static final Settings DEFAULT = new Settings(Duration.ofMinutes(10), Duration.ofMinutes(15), 5,
                Duration.ofSeconds(30), null, Duration.ofSeconds(1), 0);

        /** These settings with a budget of {@code hours}; null for none. */
        public Settings within(Duration hours) {
            return new Settings(parkAfter, stuckAfter, planRoundsWithoutNew, edge, hours, poll, maxScreens);
        }

        /** These settings ending once the run has been on {@code screens} screens; 0 for no end. */
        public Settings upTo(int screens) {
            return new Settings(parkAfter, stuckAfter, planRoundsWithoutNew, edge, hours, poll, screens);
        }
    }

    /**
     * What was read on the game: the frame's every line, the tree's elements, the facts' values, and the places on
     * the frame that the screen's readouts cover.
     */
    public record Look(List<Stop.Text> texts, List<String> elements, Map<String, String> facts, List<Rect> covered) {

        public static final Look NOTHING = new Look(List.of(), List.of(), Map.of(), List.of());

        public Look {
            texts = texts == null ? List.of() : List.copyOf(texts);
            elements = elements == null ? List.of() : List.copyOf(elements);
            facts = facts == null ? Map.of() : Map.copyOf(facts);
            covered = covered == null ? List.of() : List.copyOf(covered);
        }
    }

    /** The game, as the loop plays it: {@link MapWalk}'s moves, and what reading, guarding and a plan need. */
    public interface Play extends MapWalk.Moves {

        /** Reads the game on {@code screen} (null: none of the map): its readouts into the facts, the frame's text. */
        Look look(String screen);

        /** What is around {@code edge}'s press now, for the guards. */
        Guards.Around around(Edge edge);

        /** What is in front of the bot now. */
        Foreground front();

        /** Saves the frame the game shows now as {@code name}; where it is, or {@code ""} when it was not saved. */
        String keep(String name);

        /** Runs one round of the bot's plan; false when it has nothing to do. */
        boolean planRound();

        /** Waits {@code time}; a stop of the run ends it by its throw. */
        void idle(Duration time);

        /** Says {@code event} in the run's log. */
        default void say(ExploreEvent event) {}

        /**
         * The screen's tree as it reads now, with its place: a website's path, an app's activity. Empty for a game
         * with no tree, whose stops are all Claude's.
         */
        default Optional<Effect.State> state() {
            return Optional.empty();
        }

        /** What the guard stopped since the last call: exits, writes, pages robots.txt keeps out. */
        default List<Guard.Stopped> stopped() {
            return List.of();
        }

        /** Why the guard would refuse pressing {@code input}, as the tree read it; empty when it is free. */
        default Optional<String> refusal(Locator.Located input) {
            return Optional.empty();
        }

        /**
         * Presses {@code field} of {@code screen}, an input that is no way, waiting at most {@code withinMs}: the
         * screen of the map the game shows then, empty for none.
         */
        default Optional<String> press(String screen, String field, long withinMs) {
            return Optional.empty();
        }

        /**
         * Tries each form the page shows now, never submitting, and leaves its fields empty: what the tries showed.
         * None for a game or an app, whose fields no probe tries.
         */
        default List<FormProbe.Result> forms() {
            return List.of();
        }

        /**
         * The part of the page inside {@code box}, a tree's box, rendered and saved in the run's folder as
         * {@code name}: the file, so the input gets a picture in 🗺 Screens. Empty for a game with no renderer.
         */
        default Optional<String> picture(String name, Rectangle box) {
            return Optional.empty();
        }

        /** Whether the run plays on an SDK under development, so it raises and holds Discover faults. */
        default boolean dev() {
            return DevBuild.on();
        }

        /** The SDK's version, for a fault's evidence; blank when unknown. */
        default String sdkVersion() {
            return DevBuild.version();
        }
    }

    /** Compiles the bot again and installs it: what came of it. */
    public interface Reload {

        /** The new map, guards and generation, or the compile's errors with both left as they were. */
        Reloaded reload();

        record Reloaded(int generation, ScreenGraph map, Guards guards, List<String> errors) {
            public Reloaded {
                errors = errors == null ? List.of() : List.copyOf(errors);
            }
        }
    }

    /**
     * Text that reads as a value: a count with {@code k/m/b}, {@code x/y}, a timer {@code hh:mm(:ss)}, a percentage, or
     * a number of two digits or more.
     */
    static final Pattern VALUE = Pattern.compile(
            "\\d+(?:[.,]\\d+)?\\s*[kmbKMB]\\b|\\d+\\s*/\\s*\\d+|\\d{1,2}:\\d{2}(?::\\d{2})?|\\d+(?:[.,]\\d+)?\\s*%|\\d{2,}");

    /** The place of the stop listing every input with no verdict. */
    static final String MAP = "map";

    /** How a stop the loop paused on ended. */
    enum Paused { RESUMED, BACK, PARKED, END }

    /** A resume of a fault without ignore is answered with this, and the fault stays held. */
    public static final String FAULT_RESUMES = "a fault resumes through reload_sdk; resume ignore drops it as a misreading";

    private final Journal journal;
    private final Play play;
    /** Whether faults are raised and held: the SDK is a {@code -SNAPSHOT} build ({@link Play#dev}). */
    private final boolean dev;
    private final Reload reload;
    private Guards guards;
    private final Settings settings;
    private final LongSupplier nowMs;
    private ScreenGraph map;

    private final Set<String> seen = new HashSet<>();
    private final Set<String> tried = new HashSet<>();
    private final Set<String> worked = new HashSet<>();
    /** The parked stops, by kind and place ({@link #key(Stop)}): never pressed, never stopped on again until fixed. */
    private final Map<String, Stop> parked = new LinkedHashMap<>();
    /** The text places a missing-fact stop was raised on: once each. */
    private final Set<String> answered = new HashSet<>();
    private final Deque<String> presses = new ArrayDeque<>();
    private long began;
    private long lastPress;
    private int planRounds;
    private int stops;
    /** The edge last pressed, by its key: where an unknown screen came from. */
    private String lastEdge;
    private String from;
    private String by;
    /** The stop resumed last, until the next round says whether the fix took. */
    private Stop resumed;
    private int generation;
    /** Whether the map's verdicts are looked at at the next round's start: at the start and after a reload. */
    private boolean verdictsDue = true;
    /** Whether the game reads a tree, so the run settles what it shows itself; true once a read did. */
    private boolean readsTrees;
    /** Whether the game is a website, so page text raises no missing fact; null until a tree says. */
    private Boolean website;
    /** A website's page each screen was first met on, by screen: a sibling page of the same skeleton widens its route. */
    private final Map<String, RouteGuess.Page> pages = new LinkedHashMap<>();
    /** What presses of inputs that are no way did, not yet handed on: they go with the next settled stop. */
    private final List<TreeAnswer> notes = new ArrayList<>();
    private int settledNotes;
    /** The screens whose forms were tried: once each. */
    private final Set<String> probed = new HashSet<>();
    /** What the run settled at each stop, by its id: the evidence when the plugin side's write of it falls short. */
    private final Map<String, List<TreeAnswer>> settledAt = new HashMap<>();
    /**
     * What the plugin side left out of each settled stop, by its id, kept until a resume of it takes: a refused reload
     * loses none of it, and a resume with ignore drops it.
     */
    private final Map<String, List<String>> leftOut = new HashMap<>();
    /** Settled stops resumed with answers the plugin side left out, raised again at the next round's start. */
    private final Deque<Skip> skips = new ArrayDeque<>();

    /** The settled stop {@code stop}, resumed with {@code lines} left out of what it settled. */
    private record Skip(Stop stop, List<String> lines) {}

    /**
     * The fault a run restarted on the rebuilt SDK replays, until it passes the fault's place or meets it again; null
     * for a run started fresh, or once the replay said.
     */
    private ResumeFile replay;

    public ExploreLoop(ScreenGraph map, Play play, Reload reload, Guards guards, Journal journal, Settings settings,
                       LongSupplier nowMs) {
        this.map = map;
        this.play = play;
        this.reload = reload;
        this.guards = guards;
        this.journal = journal;
        this.settings = settings;
        this.nowMs = nowMs;
        this.dev = play.dev();
        // A queue a run before left is avoided too; stop numbers go on from the journal's.
        for (Stop stop : journal.queue()) parked.put(key(stop), stop);
        stops = (int) journal.events().stream().filter(ExploreEvent.Stopped.class::isInstance).count();
    }

    /**
     * Hands {@code answers} on with the first stop the run settles, as a note is: what the run's start already settled
     * (<em>Discover</em>'s launch target). Called before {@link #run}.
     */
    public ExploreLoop handOn(List<TreeAnswer> answers) {
        notes.addAll(answers);
        return this;
    }

    /**
     * Goes on from {@code file}, the memory a run holding a Discover fault wrote ({@link ResumeFile#pending}): the
     * run is that run restarted, and replays the fault before it explores on. None: it starts fresh. Called before
     * {@link #run}.
     */
    public ExploreLoop resuming(Optional<ResumeFile> file) {
        replay = file.orElse(null);
        return this;
    }

    /** Runs rounds until a command stops it or its budget runs out. */
    public End run() {
        began = nowMs.getAsLong();
        lastPress = began;
        // A command a run before left is not this run's.
        journal.takeCommand();
        End end = End.BROKE;
        try {
            if (replay != null) {
                emit(new ExploreEvent.Restarted(replay.fault().id(), dev, began));
                restore();
            } else {
                emit(new ExploreEvent.Began(dev, began));
            }
            end = rounds();
            return end;
        } finally {
            // Said however it ends, so the plugin side never waits on a run that is gone.
            emit(new ExploreEvent.Ended(end, nowMs.getAsLong()));
        }
    }

    /**
     * Takes back the memory of {@link #replay} and sets up its replay: the fault's place free to be met again, and the
     * game walked to the fault's screen. Passing the place says it is fixed and meeting it says the fix did not take;
     * other stops met meanwhile leave the replay waiting. A screen the map no longer has (the fix renamed it) is said,
     * and the run explores on. The file stays until the replay says, so a run restarted again before still replays.
     */
    private void restore() {
        ResumeFile file = replay;
        seen.addAll(file.seen());
        tried.addAll(file.tried());
        worked.addAll(file.worked());
        answered.addAll(file.answered());
        Stop fault = file.fault();
        tried.remove(fault.place());
        answered.remove(fault.place());
        String screen = fault.screen();
        if (screen != null && map.screen(screen).isEmpty()) {
            replayed("its screen " + screen + " is no longer in the map: nothing to replay, the run explores on");
            return;
        }
        if (screen == null) return;
        try {
            play.goTo(screen);
        } catch (RuntimeException e) {
            // The first round finds where the game is, as after any walk that fell short.
            rethrowStop(e);
        }
    }

    /** Whether {@code kind} on {@code place} is the stop the fault being replayed was. */
    private boolean replays(StopKind kind, String place) {
        return replay != null && replay.replays() == kind && replay.fault().place().equals(place);
    }

    /** Closes the fault being replayed, fixed for {@code why}: its memory is no longer needed. */
    private void replayed(String why) {
        emit(new ExploreEvent.Fixed(replay.fault().id(), why, nowMs.getAsLong()));
        ResumeFile.delete(journal);
        replay = null;
    }

    /** The run's memory, for a restart on the rebuilt SDK to go on from the fault {@code fault}. */
    private ResumeFile memory(Stop fault, StopKind replays, String source) {
        return new ResumeFile(fault, replays, source, seen, tried, worked, answered);
    }

    private End rounds() {
        while (true) {
            if (spent()) return End.BUDGET;
            Journal.Command command = journal.takeCommand().orElse(null);
            if (command instanceof Journal.Command.End) return End.STOPPED;
            // Between stops only a parked stop can be resumed: its fix is loaded and its place tried again.
            if (command instanceof Journal.Command.Resume(String stopId, boolean ignore, List<String> skipped)) {
                // The fault being replayed is still held: only ignore lets it go, as while the run paused on it.
                if (replay != null && replay.fault().id().equals(stopId)) {
                    if (ignore) {
                        replayed("ignored");
                    } else {
                        emit(new ExploreEvent.Reloaded(stopId, generation, List.of(FAULT_RESUMES), nowMs.getAsLong()));
                    }
                } else if (parked(stopId)) {
                    keepLeftOut(stopId, skipped);
                    resume(null, stopId, ignore);
                } else {
                    stale(stopId);
                }
            }
            if (!round()) return spent() ? End.BUDGET : End.STOPPED;
        }
    }

    /** Whether the run's budget, of time or of screens, is spent; never, without one. */
    private boolean spent() {
        return settings.hours() != null && nowMs.getAsLong() - began >= settings.hours().toMillis()
                || settings.maxScreens() > 0 && seen.size() >= settings.maxScreens();
    }

    /** One round; false when a command, or the budget, ended the run while it was paused. */
    boolean round() {
        if (!skips.isEmpty()) return skipped(skips.poll()) != Paused.END;
        if (verdictsDue) {
            verdictsDue = false;
            List<InputName> untagged = map.untagged();
            if (!untagged.isEmpty()) {
                String names = String.join(", ", untagged.stream().map(n -> n.screen() + "." + n.field()).toList());
                Paused paused = stop(StopKind.NO_VERDICT, null, MAP, Look.NOTHING, untagged.size()
                        + " inputs have no verdict; say free, spends or never for each: " + names, true);
                return paused != Paused.END;
            }
            passed(StopKind.NO_VERDICT, MAP);
        }
        if (nowMs.getAsLong() - lastPress >= settings.stuckAfter().toMillis()) {
            lastPress = nowMs.getAsLong();
            String at = map.screens().isEmpty() ? null : whereAfterCrash();
            return stop(StopKind.STUCK, at, at == null ? "unknown" : at, Look.NOTHING, "nothing pressed for "
                    + settings.stuckAfter().toMinutes() + " min: no input left to try, no goal the plan can reach",
                    false) != Paused.END;
        }
        try {
            boolean goesOn = play();
            if (resumed != null && resumed.kind() == StopKind.CRASH) passed(StopKind.CRASH, resumed.place());
            if (replay != null) passed(StopKind.CRASH, replay.fault().place());
            return goesOn;
        } catch (RuntimeException e) {
            rethrowStop(e);
            String at = whereAfterCrash();
            String place = (at == null ? "unknown" : at) + ":" + e.getClass().getSimpleName();
            // Thrown by Discover itself, not by the bot's code it ran: the SDK's to fix, in dev mode.
            if (dev && discovers(e)) {
                return fault(StopKind.CRASH, at, place, "exception", "Discover's own code threw: " + e,
                        evidence(e)) != Paused.END;
            }
            return stop(StopKind.CRASH, at, place, Look.NOTHING, "the bot's code threw: " + e, false) != Paused.END;
        }
    }

    /**
     * The packages of Discover's own code, as a frame's class names them. Only the bot's half: the run plays in the
     * bot's JVM, where the plugin half's assistant tools are never on the stack.
     */
    private static final List<String> DISCOVER = List.of("com.botmaker.sdk.internal.explore.");

    /** Whether {@code e} was thrown in Discover's own code: its top frame outside the JDK is one of {@link #DISCOVER}. */
    static boolean discovers(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            String type = frame.getClassName();
            if (type.startsWith("java.") || type.startsWith("javax.") || type.startsWith("jdk.")
                    || type.startsWith("sun.")) continue;
            return DISCOVER.stream().anyMatch(type::startsWith);
        }
        return false;
    }

    /** What proves a fault {@code e} is: its class, its message and its first five frames. */
    private static String evidence(Throwable e) {
        StringBuilder text = new StringBuilder(e.getClass().getName()).append(": ").append(e.getMessage());
        StackTraceElement[] frames = e.getStackTrace();
        for (int i = 0; i < Math.min(5, frames.length); i++) text.append("\n  at ").append(frames[i]);
        return text.toString();
    }

    /**
     * Raises again what the plugin side left out of the settled stop {@code skip.stop()}: a fault in dev mode, else a
     * plain stop of its kind that says the lines, Claude's to write. Never let go unsaid.
     */
    private Paused skipped(Skip skip) {
        Stop stop = skip.stop();
        // What follows is this stop's own word on it: the resume is not checked again.
        if (resumed != null && resumed.id().equals(stop.id())) resumed = null;
        return fault(stop.kind(), stop.screen(), stop.place(), "skipped", "what the run settled at " + stop.id()
                + " was not all written: " + String.join("; ", skip.lines()), String.join("\n", skip.lines())
                + settledEvidence(stop.id()));
    }

    /** The answers the run settled at {@code stopId}, as a fault's evidence; blank when none are known. */
    private String settledEvidence(String stopId) {
        List<TreeAnswer> answers = settledAt.getOrDefault(stopId, List.of());
        return answers.isEmpty() ? "" : "\nsettled: " + answers;
    }

    private boolean play() {
        Optional<String> where = play.where();
        // A page the tree reads is added by the run itself; a frame alone is Claude's to cut.
        Optional<Effect.State> unknown = where.isEmpty() && readsTrees() ? play.state() : Optional.empty();
        if (unknown.isPresent()) return settleUnknown(unknown.get(), null) != Paused.END;
        if (where.isEmpty()) {
            String place = lastEdge == null ? "unknown" : lastEdge;
            Paused paused = stop(StopKind.UNKNOWN_SCREEN, null, place, play.look(null),
                    "no screen of the map matches the frame" + (lastEdge == null ? "" : ", after " + lastEdge)
                            + ": cut its markers and add it", false);
            if (paused == Paused.PARKED || paused == null) {
                if (!play.recover()) play.restart();
            }
            return paused != Paused.END;
        }
        String screen = where.get();
        if (resumed != null) passed(StopKind.UNKNOWN_SCREEN, resumed.place());
        // A page settled as unknown before is passed once a screen of the map matches it.
        if (replay != null && readsTrees()) play.state().map(ExploreLoop::pagePlace)
                .ifPresent(page -> passed(StopKind.UNKNOWN_SCREEN, page));
        if (seen.add(screen)) {
            planRounds = 0;
            emit(new ExploreEvent.NewScreen(screen, from, by, nowMs.getAsLong()));
            if (readsTrees()) play.state().ifPresent(state -> page(screen, state));
            if (website()) pictured(screen);
        }
        Look look = play.look(screen);
        // A website's numbers are its content (a price, a count of devices), never a game's state to keep.
        Optional<Stop.Text> value = website() ? Optional.empty() : missingFact(screen, look);
        if (value.isPresent()) {
            Stop.Text text = value.get();
            return stop(StopKind.MISSING_FACT, screen, textPlace(screen, text.box()), look, "\"" + text.text().strip()
                    + "\" reads as a value no readout covers: add a readout into a fact, or resume with ignore",
                    false) != Paused.END;
        }
        if (resumed != null && screen.equals(resumed.screen())) passed(StopKind.MISSING_FACT, resumed.place());
        if (replay != null && screen.equals(replay.fault().screen())) passed(StopKind.MISSING_FACT, replay.fault().place());
        Optional<Edge> untried = untried(screen);
        if (untried.isPresent()) return press(screen, untried.get(), Mode.UNTRIED, look);
        if (readsTrees()) {
            // Once per screen the map holds: its inputs name the fields the tries found.
            if (!probed.contains(screen) && map.screen(screen).isPresent()) {
                probed.add(screen);
                notes.addAll(rules(screen));
            }
            Optional<String> input = untriedInput(screen);
            if (input.isPresent()) return pressInput(screen, input.get());
            if (!notes.isEmpty()) {
                return settle(StopKind.SETTLED, screen, "notes " + screen + "#" + ++settledNotes, List.of(),
                        settledWhy(screen)) != Paused.END;
            }
        }
        if (planRounds < settings.planRoundsWithoutNew()) {
            planRounds++;
            if (play.planRound()) {
                // A screen met after a plan round or a walk is not blamed on the last way pressed.
                lastEdge = null;
                pressed("plan round on " + screen);
                return true;
            }
        }
        planRounds = 0;
        Optional<String> nearest = nearest(screen);
        if (nearest.isPresent()) {
            lastEdge = null;
            from = screen;
            by = Mode.WALK.id();
            play.goTo(nearest.get());
            return true;
        }
        play.idle(settings.poll());
        return true;
    }

    /**
     * The run went past {@code place} without a stop of {@code kind} coming back: when that is the stop resumed
     * last, its fix took. A place is passed where its stop would be raised: a screen recognised after an unknown one,
     * a screen read with no value outside its readouts, a way pressed that arrived, a press after a stuck stop.
     */
    private void passed(StopKind kind, String place) {
        if (replays(kind, place)) replayed("replayed");
        if (resumed == null || resumed.kind() != kind || !resumed.place().equals(place)) return;
        emit(new ExploreEvent.Fixed(resumed.id(), "reloaded as generation " + generation, nowMs.getAsLong()));
        // Its fix took: what it settled is evidence of nothing any more.
        settledAt.remove(resumed.id());
        resumed = null;
    }

    private boolean press(String screen, Edge edge, Mode mode, Look look) {
        String key = key(edge);
        Guards.Refusal refusal = edge.via() == null ? guards.front(play.around(edge)) : guards.check(edge.via(), play.around(edge));
        if (refusal.refused()) {
            emit(new ExploreEvent.Refused(screen, edge.label(), refusal.kind(),refusal.why(), nowMs.getAsLong()));
            // A window in front refuses nothing of the way itself: it is pressed once the game is back.
            if (refusal.kind() == Guards.Kind.FOREIGN_WINDOW || refusal.kind() == Guards.Kind.SYSTEM_DIALOG) {
                return stop(StopKind.GUARD, screen, screen, look, refusal.why(), true) != Paused.END;
            }
            tried.add(key);
            return refusal.kind() != Guards.Kind.NO_VERDICT
                    || stop(StopKind.NO_VERDICT, screen, key, look, edge.label() + ": " + refusal.why(), false) != Paused.END;
        }
        tried.add(key);
        lastEdge = key;
        pressed(screen + ": " + edge.label());
        long before = nowMs.getAsLong();
        Took took = play.take(edge, settings.edge().toMillis());
        Arrival arrival = took == null || took.arrival() == null || nowMs.getAsLong() - before > settings.edge().toMillis()
                ? Arrival.TIMED_OUT : took.arrival();
        emit(new ExploreEvent.Tried(screen, edge.label(), mode, arrival,nowMs.getAsLong()));
        return switch (arrival) {
            case AT_TARGET, UNKNOWN, NOT_NOW -> {
                if (arrival == Arrival.AT_TARGET) {
                    worked.add(key);
                    lastEdge = null;
                    // A way that led to a screen no map matched now arrives.
                    passed(StopKind.UNKNOWN_SCREEN, key);
                }
                from = screen;
                by = edge.label();
                for (StopKind kind : List.of(StopKind.WRONG_ARRIVAL, StopKind.MISSING_CONDITION, StopKind.NO_VERDICT)) {
                    passed(kind, key);
                }
                yield true;
            }
            case ELSEWHERE -> stop(StopKind.WRONG_ARRIVAL, screen, key, look, edge.label() + " arrived at "
                    + (took.at() == null ? "another screen" : map.shown(took.at())) + ", not " + map.shown(edge.to()),
                    false) != Paused.END;
            case UNCHANGED -> worked.contains(key) && unsaid(edge)
                    ? stop(StopKind.MISSING_CONDITION, screen, key, look, edge.label() + " worked before and did "
                    + "nothing now; nothing says when it leads: give its way a when, or its input locked", false) != Paused.END
                    : stop(StopKind.WRONG_ARRIVAL, screen, key, look, edge.label() + " led nowhere new"
                    + (took.why() == null ? "" : ": " + took.why()), false) != Paused.END;
            case TIMED_OUT -> stop(StopKind.WRONG_ARRIVAL, screen, key, look, edge.label() + " showed no screen after "
                    + settings.edge().toSeconds() + " s", false) != Paused.END;
        };
    }

    private void pressed(String what) {
        if (resumed != null) passed(StopKind.STUCK, resumed.place());
        if (replay != null) passed(StopKind.STUCK, replay.fault().place());
        lastPress = nowMs.getAsLong();
        presses.addLast(what);
        while (presses.size() > Stop.PRESSES) presses.removeFirst();
    }

    /** Whether the game reads a tree: kept once it did, asked again while it has not (a first read may fail). */
    private boolean readsTrees() {
        if (!readsTrees) readsTrees = play.state().isPresent();
        return readsTrees;
    }

    /** Whether the game is a website: its tree's place is a path. Kept once known, asked again while unread. */
    private boolean website() {
        if (website == null && readsTrees()) {
            website = play.state().map(state -> state.place().startsWith("/")).orElse(null);
        }
        return Boolean.TRUE.equals(website);
    }

    /** Keeps the website's page {@code screen} was first met on, for a sibling page to widen its route; none in an app. */
    private void page(String screen, Effect.State state) {
        if (!state.place().startsWith("/") || !Overlay.of(state.tree()).none()) return;
        pages.putIfAbsent(screen, new RouteGuess.Page(RouteGuess.bare(state.place()), RouteGuess.skeleton(state.tree())));
    }

    /**
     * The pictures the map's website screen {@code screen} lacks, taken now it is reached: the page as its shot, and
     * each input whose element shows none rendered where the tree finds it, once. A map made before these existed
     * fills itself in as the run passes.
     */
    private void pictured(String screen) {
        MapScreen on = map.screen(screen).orElse(null);
        Optional<Effect.State> state = on == null ? Optional.empty() : play.state();
        if (state.isEmpty()) return;
        Map<String, String> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, Input> input : on.inputs().entrySet()) {
            if (!(input.getValue() instanceof Button button) || button.element() == null
                    || button.element().shot() != null) continue;
            List<UiNode> found = ElementMatch.matches(button.element(), state.get().tree());
            if (found.size() != 1) continue;
            String field = input.getKey();
            play.picture(screen + "." + field, found.getFirst().bounds()).ifPresent(file -> inputs.put(field, file));
        }
        String frame = on.setup().shot() == null ? play.keep("shot-" + screen) : "";
        if (!frame.isEmpty() || !inputs.isEmpty()) {
            emit(new ExploreEvent.Pictured(screen, frame, inputs, nowMs.getAsLong()));
        }
    }

    /**
     * The first input of {@code screen} that is no way, free, found in its tree, never pressed: an input no note
     * says anything of yet. One the bot's code presses ({@code Custom}) or one found by a picture is Claude's. One a
     * note says fails is pressed again, once a run: what failed may have been the page's, or an older run's judging.
     */
    private Optional<String> untriedInput(String screen) {
        MapScreen on = map.screen(screen).orElse(null);
        if (on == null) return Optional.empty();
        Set<String> ways = new HashSet<>();
        for (Edge edge : map.edges()) if (edge.from().equals(screen) && edge.input() != null) ways.add(edge.input());
        String notes = on.setup().notes() == null ? "" : "\n" + on.setup().notes();
        for (Map.Entry<String, Input> input : on.inputs().entrySet()) {
            String field = input.getKey();
            if (!(input.getValue() instanceof Button button) || button.element() == null) continue;
            boolean failed = notes.contains("\n" + field + ": " + Effect.FAILS);
            if (!(button.verdict() instanceof Verdict.Free) || button.isTried() && !failed || button.isInfo()) continue;
            String key = screen + "." + field;
            if (ways.contains(field) || tried.contains(key) || avoided(key)) continue;
            return Optional.of(field);
        }
        return Optional.empty();
    }

    /** What the notes handed on with {@code screen}'s settled stop are: its forms' tries, presses, or earlier answers. */
    private String settledWhy(String screen) {
        if (notes.stream().anyMatch(TreeAnswer.InputRule.class::isInstance)) {
            return "what " + screen + "'s forms showed when tried, never submitted";
        }
        return notes.stream().allMatch(TreeAnswer.InputNote.class::isInstance)
                ? "what pressing " + notes.size() + " inputs did, read off the page"
                : "what the run settled before it met this screen";
    }

    /**
     * The rules {@code screen}'s forms showed when tried, as answers handed on with the next settled stop: each field
     * named by the screen's input whose element finds, alone, the part of the label the tries gave it.
     */
    private List<TreeAnswer> rules(String screen) {
        MapScreen on = map.screen(screen).orElseThrow();
        List<FormProbe.Result> tried = play.forms();
        if (tried.isEmpty()) return List.of();
        UiTree tree = play.state().map(Effect.State::tree).orElse(null);
        if (tree == null) return List.of();
        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, Input> input : on.inputs().entrySet()) {
            if (!(input.getValue() instanceof Button button) || button.element() == null) continue;
            List<UiNode> found = ElementMatch.matches(button.element(), tree);
            if (found.size() == 1 && !found.getFirst().label().isBlank()) {
                fields.putIfAbsent(found.getFirst().label().strip().toLowerCase(Locale.ROOT), input.getKey());
            }
        }
        List<TreeAnswer> answers = new ArrayList<>();
        Set<String> taken = new HashSet<>(on.inputs().keySet());
        for (FormProbe.Result result : tried) {
            // A submit disabled until its rule holds is no input the page's read made: added here, the rule's to hold.
            for (FormRule rule : result.rules()) {
                if (!(rule instanceof FormRule.Enables enables)) continue;
                String label = enables.input().strip().toLowerCase(Locale.ROOT);
                if (fields.containsKey(label)) continue;
                TreeAnswer.disabledInput(screen, tree, enables.input(), taken).ifPresent(input -> {
                    answers.add(input);
                    taken.add(input.field());
                    fields.put(label, input.field());
                });
            }
            answers.addAll(FormRules.answers(screen, result.rules(),
                    label -> Optional.ofNullable(fields.get(label.strip().toLowerCase(Locale.ROOT)))));
        }
        return answers;
    }

    /**
     * Presses {@code field} of {@code screen}, an input that is no way, and judges what it did off the tree before and
     * after: a way to a screen the bot has is settled at once, a page or an overlay it has not is added with its way,
     * and anything else is a note handed on with the next settled stop.
     */
    private boolean pressInput(String screen, String field) {
        String key = screen + "." + field;
        tried.add(key);
        // What the guard stopped before this press is not its doing.
        play.stopped();
        Effect.State before = play.state().orElse(Effect.State.of(null, ""));
        pressed(screen + ": " + field);
        lastEdge = null;
        Optional<String> at = play.press(screen, field, settings.edge().toMillis());
        Effect.State after = play.state().orElse(Effect.State.of(null, ""));
        Effect effect = Effect.judge(before, after, play.stopped());
        String to = at.orElse(null);
        Arrival arrival = to == null ? Arrival.UNKNOWN : to.equals(screen) ? Arrival.UNCHANGED : Arrival.AT_TARGET;
        emit(new ExploreEvent.Tried(screen, field, Mode.UNTRIED, arrival, nowMs.getAsLong()));
        if (effect.way() && to != null && !to.equals(screen)) {
            from = screen;
            by = field;
            return settle(StopKind.SETTLED, screen, key, List.of(new TreeAnswer.NewWay(screen, field, to)),
                    field + " leads to " + map.shown(to)) != Paused.END;
        }
        // An overlay over the same page, or a page no screen matches: a screen to add, reached by this input.
        if (effect instanceof Effect.Opens || effect instanceof Effect.Navigates && to == null) {
            from = screen;
            by = field;
            return settleUnknown(after, new InputName(screen, field)) != Paused.END;
        }
        notes.add(new TreeAnswer.InputNote(screen, field, effect.line()));
        return true;
    }

    /**
     * Settles a screen the map lacks off its tree: a sibling page of one met before widens that screen's route, else a
     * new screen with its inputs, each with the guard's verdict; reached by {@code by}'s press (null: not by a press
     * of this run). Parked, the game is brought back as for an unknown screen Claude never took up.
     */
    private Paused settleUnknown(Effect.State state, InputName by) {
        String path = state.place().startsWith("/") ? RouteGuess.bare(state.place()) : null;
        List<TreeAnswer> answers = new ArrayList<>();
        String screen = widened(state, path, answers).orElse(null);
        if (screen == null) {
            Set<String> taken = new HashSet<>();
            for (String constant : map.screens().keySet()) taken.add(map.shown(constant));
            TreeAnswer.NewScreen made = TreeAnswer.screen(state.tree(), path, path == null ? List.of() : List.of(path),
                    taken);
            answers.add(made);
            String named = made.name();
            answers.addAll(TreeAnswer.inputs(named, state.tree(), Set.of(), Set.of(), play::refusal,
                    (field, located) -> play.picture(named + "." + field, located.node().bounds()).orElse(null)));
            screen = made.name();
        }
        // A press that brought a sibling page of its own screen (a next page) widened its route: no way to itself.
        if (by != null && !by.screen().equals(screen)) answers.add(new TreeAnswer.NewWay(by.screen(), by.field(), screen));
        String place = path != null ? pagePlace(state) : "page " + (by != null ? by.screen() + "." + by.field()
                : "unknown");
        Paused paused = settle(StopKind.UNKNOWN_SCREEN, null, place, answers, "no screen of the map matches the page: "
                + screen + " is written from its tree");
        if (paused == Paused.PARKED || paused == null) {
            if (!play.recover()) play.restart();
        }
        return paused;
    }

    /** The place of a website's page as a stop on it names it: its bare path and overlay; null off a website. */
    private static String pagePlace(Effect.State state) {
        if (!state.place().startsWith("/")) return null;
        return "page " + RouteGuess.bare(state.place()) + Overlay.of(state.tree()).line();
    }

    /**
     * The screen whose route {@code path}'s page widens, its new route added to {@code answers}: a page met before of
     * the same skeleton, its path differing in one segment. Empty in an app, over an overlay, or with none alike.
     */
    private Optional<String> widened(Effect.State state, String path, List<TreeAnswer> answers) {
        if (path == null || !Overlay.of(state.tree()).none()) return Optional.empty();
        RouteGuess.Page page = new RouteGuess.Page(path, RouteGuess.skeleton(state.tree()));
        for (Map.Entry<String, RouteGuess.Page> met : pages.entrySet()) {
            Optional<RouteGuess.Route> route = RouteGuess.of(List.of(met.getValue(), page));
            if (route.isEmpty()) continue;
            answers.add(new TreeAnswer.NewRoute(met.getKey(), route.get().pattern(), route.get().examples()));
            return Optional.of(met.getKey());
        }
        return Optional.empty();
    }

    /**
     * Stops on what the run settled itself, {@code answers} and the notes not yet handed on, for the plugin side to
     * write and resume: the {@link ExploreEvent.Settled} first, then the stop, waited on and parked as any. The same
     * place settled again right after its resume did not take: a Discover fault in dev mode, else Claude's as a plain
     * stop.
     */
    private Paused settle(StopKind kind, String screen, String place, List<TreeAnswer> answers, String missing) {
        if (parked.containsKey(parkedKey(kind, place))) return null;
        boolean replayed = replays(kind, place);
        if (replayed || resumed != null && resumed.kind() == kind && resumed.place().equals(place)) {
            return fault(kind, screen, place, "did-not-take", missing + "; what the tree settled was written and did "
                    + "not take", "settled again: " + answers
                    + settledEvidence(replayed ? replay.fault().id() : resumed.id()));
        }
        List<TreeAnswer> all = new ArrayList<>(answers);
        all.addAll(notes);
        notes.clear();
        String id = kind.id() + "-" + (stops + 1);
        settledAt.put(id, List.copyOf(all));
        emit(new ExploreEvent.Settled(id, all, nowMs.getAsLong()));
        return stop(kind, screen, place, Look.NOTHING, missing, false);
    }

    /** Whether nothing in the bot says when {@code edge} leads: no way condition, no lock, no look it needs. */
    private static boolean unsaid(Edge edge) {
        return edge.when() == null && edge.state() == null && (edge.via() == null || edge.via().locked() == null);
    }

    /**
     * The first edge out of {@code screen} not tried, not avoided, open now. One the moves cannot press here (a custom
     * input, a way back with no close button seen) counts as tried, so no walk comes back for it.
     */
    private Optional<Edge> untried(String screen) {
        for (Edge edge : map.edges()) {
            // One taken by waiting is not pressed: the game takes it by itself (untriedEdge leaves it out).
            if (!edge.from().equals(screen) || !untriedEdge(edge)) continue;
            if (open(edge)) return Optional.of(edge);
            tried.add(key(edge));
        }
        return Optional.empty();
    }

    private boolean untriedEdge(Edge edge) {
        String key = key(edge);
        return !edge.onItsOwn() && !tried.contains(key) && !avoided(key) && Conditions.answer(edge.when()) == Answer.YES;
    }

    private boolean open(Edge edge) {
        try {
            return play.open(edge);
        } catch (RuntimeException e) {
            rethrowStop(e);
            return true;
        }
    }

    /** The nearest screen by the map's edges, from {@code screen}, with an edge not tried yet; not itself. */
    private Optional<String> nearest(String screen) {
        Set<String> reached = new LinkedHashSet<>(List.of(screen));
        Deque<String> queue = new ArrayDeque<>(reached);
        while (!queue.isEmpty()) {
            String at = queue.poll();
            for (Edge edge : map.edges()) {
                if (!edge.from().equals(at) || !reached.add(edge.to())) continue;
                if (map.edges().stream().anyMatch(e -> e.from().equals(edge.to()) && untriedEdge(e))) {
                    return Optional.of(edge.to());
                }
                queue.add(edge.to());
            }
        }
        return Optional.empty();
    }

    /** The first text on {@code look} that reads as a value, outside every readout, not raised before. */
    private Optional<Stop.Text> missingFact(String screen, Look look) {
        for (Stop.Text text : look.texts()) {
            if (!VALUE.matcher(text.text()).find()) continue;
            if (text.box() != null && look.covered().stream().anyMatch(area -> area.overlaps(text.box()))) continue;
            String place = textPlace(screen, text.box());
            if (answered.add(place)) return Optional.of(text);
        }
        return Optional.empty();
    }

    /** A text box's place on {@code screen}, rounded to 20 px so a box read a few pixels off is the same. */
    static String textPlace(String screen, Rect box) {
        return box == null ? screen + "#?" : screen + "#" + Math.round(box.x() / 20.0) * 20 + "," + Math.round(box.y() / 20.0) * 20;
    }

    /** An edge's key: {@code "Home.shop→Shop"}. */
    static String key(Edge edge) {
        return edge.from() + "." + edge.label() + "→" + edge.to();
    }

    /** A parked stop's key: its kind and place, so two kinds parked on one place are both kept. */
    private static String key(Stop stop) {
        return parkedKey(stop.kind(), stop.place());
    }

    private static String parkedKey(StopKind kind, String place) {
        return kind.id() + " " + place;
    }

    /** Whether a stop of any kind is parked on {@code place}: it is not pressed until one is fixed. */
    private boolean avoided(String place) {
        return parked.values().stream().anyMatch(stop -> stop.place().equals(place));
    }

    /**
     * Stops the run on what it met and waits for Claude: a resume reloads the bot, nobody answering within
     * {@link Settings#parkAfter} parks the stop, unless {@code neverParks}. A stop already parked is not raised
     * again: null.
     */
    private Paused stop(StopKind kind, String screen, String place, Look look, String missing, boolean neverParks) {
        return stop(kind, screen, place, look, missing, neverParks, null);
    }

    /**
     * A Discover fault (2026-11-19): in dev mode a stop of kind {@link StopKind#FAULT} on {@code place}, {@code why}
     * its missing line, held until the SDK is reloaded or the run ends; {@code source} says what found it and
     * {@code evidence} what proves it. Out of dev mode the stop of kind {@code plain} is raised instead, as the source
     * raised it before faults existed: a bot's author is never told to edit the SDK. Null when that stop is parked.
     */
    Paused fault(StopKind plain, String screen, String place, String source, String why, String evidence) {
        if (!dev) return stop(plain, screen, place, Look.NOTHING, why, false);
        return stop(StopKind.FAULT, screen, place, Look.NOTHING, why, true, new Fault(plain, source, evidence));
    }

    /** What found a fault the run raised, what proves it, and the kind of stop its place raised before it was one. */
    private record Fault(StopKind plain, String source, String evidence) {}

    private Paused stop(StopKind kind, String screen, String place, Look look, String missing, boolean neverParks,
                        Fault fault) {
        if (parked.containsKey(parkedKey(kind, place))) return null;
        // The stop a replayed fault was came back on the rebuilt SDK: the fault is still there, held again.
        if (fault == null && dev && replays(kind, place)) {
            return stop(StopKind.FAULT, screen, place, look, missing, true, new Fault(kind, replay.source(),
                    "the stop came back as " + kind.id() + " after the SDK's reload"));
        }
        String id = kind.id() + "-" + ++stops;
        Stop stop = new Stop(id, kind, screen, place, play.keep(id), look.texts(), look.elements(), look.facts(),
                List.copyOf(presses), missing, nowMs.getAsLong());
        boolean again = resumed != null && resumed.same(stop)
                || replay != null && kind == StopKind.FAULT && place.equals(replay.fault().place());
        if (again) stop = new Stop(id, kind, screen, place, stop.framePath(), stop.texts(), stop.elements(),
                stop.facts(), stop.lastPresses(), "the fix did not take: " + missing, stop.at());
        resumed = null;
        // Another stop met on the way leaves the replay waiting for the fault's place.
        if (replay != null && place.equals(replay.fault().place())) replay = null;
        emit(new ExploreEvent.Stopped(stop, again, nowMs.getAsLong()));
        if (fault != null) {
            emit(new ExploreEvent.Faulted(id, fault.source(), stop.missing(), fault.evidence(), play.sdkVersion(),
                    nowMs.getAsLong()));
            // Written before the run holds, so the SDK's reload restarts it where it is.
            memory(stop, fault.plain(), fault.source()).write(journal);
        }
        Paused paused = pause(stop, neverParks);
        // The time paused is Claude's, not the run's: it does not count as nothing pressed.
        lastPress = nowMs.getAsLong();
        return paused;
    }

    /**
     * Waits on {@code stop} for a command, the game back in front, or its parking time. A fault is held instead: only
     * the SDK's reload, a resume with ignore, or the run's end lets it go.
     */
    private Paused pause(Stop stop, boolean neverParks) {
        String id = stop.id();
        boolean held = stop.kind() == StopKind.FAULT;
        long deadline = nowMs.getAsLong() + settings.parkAfter().toMillis();
        while (true) {
            Optional<Journal.Command> command = journal.takeCommand();
            if (command.orElse(null) instanceof Journal.Command.End || spent()) return Paused.END;
            // Claude calls this stop a fault: held from now on. Out of dev mode, or for another stop, it is dropped.
            if (command.orElse(null) instanceof Journal.Command.Fault(String stopId, String why)
                    && dev && !held && id.equals(stopId)) {
                held = true;
                emit(new ExploreEvent.Faulted(id, "claude", why, "", play.sdkVersion(), nowMs.getAsLong()));
                memory(stop, stop.kind(), "claude").write(journal);
            }
            // A resume of a stop that is neither this one nor parked is stale: it changes nothing.
            if (command.orElse(null) instanceof Journal.Command.Resume(String stopId, boolean ignore,
                                                                        List<String> skipped)) {
                if (held && !ignore && id.equals(stopId)) {
                    emit(new ExploreEvent.Reloaded(stopId, generation, List.of(FAULT_RESUMES), nowMs.getAsLong()));
                } else if (stop.id().equals(stopId) || parked(stopId)) {
                    keepLeftOut(stopId, skipped);
                    Optional<Paused> resumedNow = resume(stop, stopId, ignore);
                    if (resumedNow.isPresent()) {
                        // A fault dropped as a misreading: no restart goes on from it.
                        if (held) ResumeFile.delete(journal);
                        return resumedNow.get();
                    }
                    // Claude is on this stop: its parking time starts again. A parked one resumed leaves it be.
                    if (stop.id().equals(stopId)) deadline = nowMs.getAsLong() + settings.parkAfter().toMillis();
                } else {
                    stale(stopId);
                }
            }
            // A fault waits for its fix: never parked, never let go by the game coming back.
            if (held) {
                play.idle(settings.poll());
                continue;
            }
            if (stop.kind() == StopKind.GUARD && neverParks) {
                Foreground front = play.front();
                if (!front.foreign() && front.dialog().isEmpty()) {
                    emit(new ExploreEvent.Fixed(id, "the game is in front again", nowMs.getAsLong()));
                    return Paused.BACK;
                }
            } else if (!neverParks && nowMs.getAsLong() >= deadline) {
                parked.put(key(stop), stop);
                journal.queue(List.copyOf(parked.values()));
                emit(new ExploreEvent.Parked(id, nowMs.getAsLong()));
                return Paused.PARKED;
            }
            play.idle(settings.poll());
        }
    }

    /** Keeps {@code lines}, left out of the stop {@code stopId}, until a resume of it takes. */
    private void keepLeftOut(String stopId, List<String> lines) {
        if (!lines.isEmpty()) leftOut.merge(stopId, lines, (kept, more) -> {
            List<String> all = new ArrayList<>(kept);
            all.addAll(more);
            return List.copyOf(all);
        });
    }

    /** Whether the queue holds the stop {@code stopId}. */
    private boolean parked(String stopId) {
        return parked.values().stream().anyMatch(s -> s.id().equals(stopId));
    }

    /** A resume of {@code stopId}, neither open nor parked, answered as refused so the tool waiting on it hears. */
    private void stale(String stopId) {
        emit(new ExploreEvent.Reloaded(stopId, generation, List.of("No stop " + stopId + " is open or parked."),
                nowMs.getAsLong()));
    }

    /**
     * A resume of {@code stopId} while paused on {@code open} (null: between stops, a parked one): the bot reloaded.
     * Refused, the run stays paused (empty).
     * A parked stop resumed leaves the queue and its place is tried again; the open stop stays open (empty). The open
     * stop resumed: what the next round meets says whether the fix took.
     */
    private Optional<Paused> resume(Stop open, String stopId, boolean ignore) {
        Stop parkedStop = open != null && open.id().equals(stopId) ? null
                : parked.values().stream().filter(s -> s.id().equals(stopId)).findFirst().orElse(null);
        Reload.Reloaded done = reload.reload();
        if (!done.errors().isEmpty()) {
            emit(new ExploreEvent.Reloaded(stopId, generation, done.errors(), nowMs.getAsLong()));
            return Optional.empty();
        }
        generation = done.generation();
        map = done.map();
        if (done.guards() != null) guards = done.guards();
        verdictsDue = true;
        emit(new ExploreEvent.Reloaded(stopId, generation, List.of(), nowMs.getAsLong()));
        Stop fixed = parkedStop != null ? parkedStop : open;
        List<String> lines = leftOut.remove(fixed.id());
        if (lines != null && !ignore) skips.add(new Skip(fixed, lines));
        if (parkedStop != null) {
            parked.remove(key(parkedStop));
            journal.queue(List.copyOf(parked.values()));
        }
        // Its place is tried again, so a fix that did not take shows; an ignored value is never raised again.
        tried.remove(fixed.place());
        if (!(ignore && fixed.kind() == StopKind.MISSING_FACT)) answered.remove(fixed.place());
        if (parkedStop != null) {
            emit(new ExploreEvent.Fixed(parkedStop.id(), "reloaded as generation " + generation, nowMs.getAsLong()));
            return Optional.empty();
        }
        resumed = ignore ? null : open;
        if (ignore) emit(new ExploreEvent.Fixed(open.id(), "ignored", nowMs.getAsLong()));
        return Optional.of(Paused.RESUMED);
    }

    private void emit(ExploreEvent event) {
        journal.append(event);
        play.say(event);
    }

    /** Rethrows {@code e} when it is a stop of the run, or wraps one. */
    private static void rethrowStop(RuntimeException e) {
        for (Throwable at = e; at != null; at = at.getCause() == at ? null : at.getCause()) {
            if (InstalledRun.isStop(at)) throw (RuntimeException) at;
        }
    }

    /** Where the game is, for a stop after a crash; null when that throws too. */
    private String whereAfterCrash() {
        try {
            return play.where().orElse(null);
        } catch (RuntimeException e) {
            rethrowStop(e);
            return null;
        }
    }
}
