// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Element;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
     * paused run looks for a command, the screens it is on before it ends, each counted once (0: no end), and the
     * time a screen's own clock may run unbuilt before the run sets it aside for another open one (null: never).
     */
    public record Settings(Duration parkAfter, Duration stuckAfter, int planRoundsWithoutNew, Duration edge,
                           Duration hours, Duration poll, int maxScreens, Duration screenBudget) {

        public static final Settings DEFAULT = new Settings(Duration.ofMinutes(10), Duration.ofMinutes(15), 5,
                Duration.ofSeconds(30), null, Duration.ofSeconds(1), 0, Duration.ofMinutes(2));

        /** These settings with a budget of {@code hours}; null for none. */
        public Settings within(Duration hours) {
            return new Settings(parkAfter, stuckAfter, planRoundsWithoutNew, edge, hours, poll, maxScreens, screenBudget);
        }

        /** These settings ending once the run has been on {@code screens} screens; 0 for no end. */
        public Settings upTo(int screens) {
            return new Settings(parkAfter, stuckAfter, planRoundsWithoutNew, edge, hours, poll, screens, screenBudget);
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

        /** What is around a press of {@code input} now, for the guards; null: a press of no input, its front only. */
        Guards.Around around(Input input);

        /** What is in front of the bot now. */
        Foreground front();

        /** Saves the frame the game shows now as {@code name}; where it is, or {@code ""} when it was not saved. */
        String keep(String name);

        /** Runs one round of the bot's plan; false when it has nothing to do. */
        boolean planRound();

        /** The plan {@link #planRound} plays; {@link Plan#NONE} when the run plays none. */
        default Plan plan() {
            return Plan.NONE;
        }

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
         * Brings the page back as it loads, after a press left something open on it (a list, a menu) that would
         * catch the next press: a website loads its address again. False for a game, which has no address.
         */
        default boolean reopen() {
            return false;
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

    /** What a screen's clock went to, as a low-yield stop says it. */
    private enum Spent {
        PRESSING("pressing"), SETTLING("settling"), WALKING("walking"), CLAUDE("waiting on Claude"), IDLE("idle");

        private final String label;

        Spent(String label) {
            this.label = label;
        }
    }

    /** A resume of a fault without ignore is answered with this, and the fault stays held. */
    public static final String FAULT_RESUMES = "a fault resumes through reload_sdk; resume ignore drops it as a misreading";

    /** The source of a fault the plugin side raised by leaving lines of a settled stop unwritten. */
    private static final String SKIPPED = "skipped";

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
    /** The screens whose inputs made twice this process looked for; never restored from a run's memory. */
    private final Set<String> merged = new HashSet<>();
    private final Set<String> tried = new HashSet<>();
    /** The places a guard refused this run, way or input, with the guard's kind: each said once, never restored. */
    private final Set<String> refused = new HashSet<>();
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
    /** Each screen's own clock, in ms: the time the run spent on it, charged at each round. */
    private final Map<String, Long> clock = new HashMap<>();
    /** The screens nothing is open about any more, said once each. */
    private final Set<String> built = new HashSet<>();
    /** What was open on each screen when the run last said it building. */
    private final Map<String, List<String>> leftOpen = new HashMap<>();
    /** Screens over their budget unbuilt: the run works another open screen first, and these once none is. */
    private final Set<String> aside = new HashSet<>();
    /** The last walk, {@code from→to}, until a press: a set-aside screen a walk came back to does not walk again. */
    private String lastWalk;
    /** Each set-aside screen's clock when it was set aside: a second budget past it is a low-yield stop. */
    private final Map<String, Long> asideAt = new HashMap<>();
    /** The screens a low-yield stop was resumed with ignore on: left as they are for the run. */
    private final Set<String> leftAsIs = new HashSet<>();
    /** The low-yield stop resumed with a fresh clock on each screen, by screen: fixed once the screen is built. */
    private final Map<String, String> yieldResumed = new HashMap<>();
    /** What each screen's clock went to, in ms. */
    private final Map<String, EnumMap<Spent, Long>> spentOn = new HashMap<>();
    /** What the last round did, which the time to the next one is charged to, but what it waited on a stop. */
    private Spent doing = Spent.PRESSING;
    /** What the run waited on at stops since the last round's charge, in ms. */
    private final EnumMap<Spent, Long> waited = new EnumMap<>(Spent.class);
    /** Whether the stop raised next hands what the run settled on: its wait is settling, not Claude's. */
    private boolean settling;
    /** The inputs this run took a picture of ({@code SCREEN.field}), before the map it plays holds them. */
    private final Set<String> pictures = new HashSet<>();
    /** The screen the last round was on, null when on none; since when, epoch ms. */
    private String onScreen;
    private long onSince;
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
                restoreClocks();
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
        return fault(stop.kind(), stop.screen(), stop.place(), SKIPPED, "what the run settled at " + stop.id()
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
        if (where.isEmpty()) clock(null);
        // An error page the server answered (a 431) is no screen: the run goes back to one it knows.
        if (unknown.isPresent() && unknown.get().httpError()) {
            if (!play.recover()) play.restart();
            return true;
        }
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
        // Before anything on it is pressed: an input an older read made twice is never pressed again. Once a screen
        // per run, a run restarted on its memory included, so a merge an older build could not write is tried again.
        if (readsTrees() && map.screen(screen).isPresent() && merged.add(screen)) notes.addAll(sameInputs(screen));
        // After the screen is said found and pictured: one with nothing to do is built once it is reached.
        clock(screen);
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
        if (overBudget(screen)) {
            // Its own work waits while another screen has some: that one is walked to first.
            // A walk that came back here does its own work instead of walking again.
            Optional<String> other = nearest(screen, true);
            if (other.isPresent() && !(screen + "→" + other.get()).equals(lastWalk)) return walk(screen, other.get());
            Paused low = lowYield(screen, look);
            if (low != null) return low != Paused.END;
        }
        Optional<Edge> untried = untried(screen);
        if (untried.isPresent()) return press(screen, untried.get(), Mode.UNTRIED, look);
        if (readsTrees()) {
            // Once per screen the map holds: its inputs name the fields the tries found.
            if (!probed.contains(screen) && map.screen(screen).isPresent()) {
                probed.add(screen);
                notes.addAll(rules(screen));
            }
            Optional<String> input = untriedInput(screen);
            if (input.isPresent()) return pressInput(screen, input.get(), look);
            if (!notes.isEmpty()) {
                return settle(StopKind.SETTLED, screen, "notes " + screen + "#" + ++settledNotes, List.of(),
                        settledWhy(screen)) != Paused.END;
            }
        }
        if (planRounds < settings.planRoundsWithoutNew()) {
            planRounds++;
            doing = Spent.PRESSING;
            if (play.planRound()) {
                // A screen met after a plan round or a walk is not blamed on the last way pressed.
                lastEdge = null;
                pressed("plan round on " + screen);
                return true;
            }
        }
        planRounds = 0;
        Optional<String> nearest = nearest(screen, true).or(() -> nearest(screen, false));
        if (nearest.isPresent()) return walk(screen, nearest.get());
        doing = Spent.IDLE;
        play.idle(settings.poll());
        return true;
    }

    private boolean walk(String screen, String to) {
        doing = Spent.WALKING;
        lastWalk = screen + "→" + to;
        lastEdge = null;
        from = screen;
        by = Mode.WALK.id();
        play.goTo(to);
        return true;
    }

    /**
     * Whether {@code screen} is set aside: unbuilt past {@link Settings#screenBudget} on its own clock. Said once, when
     * its clock first runs over, with what is still open on it.
     */
    private boolean overBudget(String screen) {
        Duration budget = settings.screenBudget();
        if (budget == null) return false;
        if (aside.contains(screen)) return true;
        long ms = clock.getOrDefault(screen, 0L);
        if (built.contains(screen) || ms < budget.toMillis()) return false;
        List<String> open = open(screen);
        if (open.isEmpty()) return false;
        aside.add(screen);
        asideAt.put(screen, ms);
        emit(new ExploreEvent.SetAside(screen, ms, open, nowMs.getAsLong()));
        return true;
    }

    /**
     * The low-yield stop on {@code screen}, set aside and back on its own work: raised once a second budget ran on its
     * clock since it was set aside with it still open; null while not, once it was ignored, or while it is parked.
     */
    private Paused lowYield(String screen, Look look) {
        if (leftAsIs.contains(screen)) return null;
        long ms = clock.getOrDefault(screen, 0L);
        // A screen a restarted run found aside gets its second budget from now.
        long since = asideAt.computeIfAbsent(screen, s -> ms);
        if (ms - since < settings.screenBudget().toMillis()) return null;
        List<String> open = open(screen);
        if (open.isEmpty()) return null;
        // A fresh clock that ran out too did not take.
        yieldResumed.remove(screen);
        return stop(StopKind.LOW_YIELD, screen, screen, look, screen + " is still unbuilt after "
                + JournalView.took(ms) + " on its clock, " + JournalView.took(ms - since) + " since it was set aside: "
                + String.join(", ", open) + "; its clock went to " + spent(screen)
                + ". Fix what is open and resume for a fresh clock, or resume with ignore to leave it as it is", false);
    }

    /** Where {@code screen}'s clock went, one part a kind ({@code "pressing 3m2s, walking 18s"}). */
    private String spent(String screen) {
        List<String> parts = new ArrayList<>();
        spentOn.getOrDefault(screen, new EnumMap<>(Spent.class)).forEach((kind, ms) -> {
            if (ms >= 1000) parts.add(kind.label + " " + JournalView.took(ms));
        });
        // Kept in memory only: a restarted run times from its restart.
        return parts.isEmpty() ? "nothing timed since the run started" : String.join(", ", parts);
    }

    /**
     * What a resume of the low-yield stop on {@code screen} does: a fresh clock, the screen no longer aside, or with
     * {@code ignore} the screen left as it is for the run, never stopped on for its yield again.
     */
    private void yielded(String screen, boolean ignore) {
        if (ignore) {
            leftAsIs.add(screen);
            return;
        }
        clock.put(screen, 0L);
        spentOn.remove(screen);
        aside.remove(screen);
        asideAt.remove(screen);
        // The wait on the stop itself is not charged to the fresh clock.
        if (screen.equals(onScreen)) {
            onSince = nowMs.getAsLong();
            waited.clear();
        }
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

    /**
     * What the round returns when {@code refusal} refuses the press of {@code label} on {@code screen}, its place
     * {@code key}; empty when nothing refused it. A window in front refuses nothing of the press itself: it stops, and
     * the press is made once the game is back. Any other refusal sets the place aside, said once a run.
     */
    private Optional<Boolean> refused(String screen, String key, String label, Guards.Refusal refusal, Look look) {
        if (!refusal.refused()) return Optional.empty();
        if (refused.add(key + "#" + refusal.kind())) emit(new ExploreEvent.Refused(screen, label, refusal.kind(), refusal.why(), nowMs.getAsLong()));
        if (refusal.kind() == Guards.Kind.FOREIGN_WINDOW || refusal.kind() == Guards.Kind.SYSTEM_DIALOG) {
            return Optional.of(stop(StopKind.GUARD, screen, screen, look, refusal.why(), true) != Paused.END);
        }
        tried.add(key);
        return Optional.of(refusal.kind() != Guards.Kind.NO_VERDICT
                || stop(StopKind.NO_VERDICT, screen, key, look, label + ": " + refusal.why(), false) != Paused.END);
    }

    private boolean press(String screen, Edge edge, Mode mode, Look look) {
        String key = key(edge);
        Guards.Around around = play.around(edge.via());
        Guards.Refusal refusal = edge.via() == null ? guards.front(around) : guards.check(edge.via(), around);
        Optional<Boolean> refused = refused(screen, key, edge.label(), refusal, look);
        if (refused.isPresent()) return refused.get();
        tried.add(key);
        lastEdge = key;
        doing = Spent.PRESSING;
        pressed(screen + ": " + edge.label());
        long before = nowMs.getAsLong();
        Took took = play.take(edge, settings.edge().toMillis());
        Arrival arrival = took == null || took.arrival() == null || nowMs.getAsLong() - before > settings.edge().toMillis()
                ? Arrival.TIMED_OUT : took.arrival();
        // A way back returns where the game came from: any screen with a way in, not only the one the map names.
        if (arrival == Arrival.ELSEWHERE && edge.back() && took.at() != null && cameFrom(screen, took.at())) {
            arrival = Arrival.AT_TARGET;
        }
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

    /** Whether the game can have come to {@code screen} from {@code at}: the screen it was pressed from, or a way in. */
    private boolean cameFrom(String screen, String at) {
        return at.equals(from) || map.edges().stream()
                .anyMatch(e -> e.to().equals(screen) && e.from().equals(at) && !e.back() && !e.onItsOwn());
    }

    private void pressed(String what) {
        if (resumed != null) passed(StopKind.STUCK, resumed.place());
        if (replay != null) passed(StopKind.STUCK, replay.fault().place());
        lastPress = nowMs.getAsLong();
        lastWalk = null;
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
        inputs.keySet().forEach(field -> pictures.add(screen + "." + field));
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
        return untriedInputs(screen).stream().findFirst();
    }

    /** Every input {@link #untriedInput} would press on {@code screen}, in the screen's order. */
    private List<String> untriedInputs(String screen) {
        MapScreen on = map.screen(screen).orElse(null);
        if (on == null) return List.of();
        List<String> untried = new ArrayList<>();
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
            untried.add(field);
        }
        return untried;
    }

    /**
     * Charges the time since the last round to the screen it was on, and moves the run's clock to {@code screen}
     * (null: on no screen of the map). A screen the run leaves is said built, or building with what is still open on
     * it, a built one too, which a stop parked on it while there opens again; a screen it reaches with nothing open
     * is said built at once.
     */
    private void clock(String screen) {
        long now = nowMs.getAsLong();
        if (onScreen != null) {
            long ms = Math.max(0, now - onSince);
            clock.merge(onScreen, ms, Long::sum);
            charge(onScreen, ms);
            if (!onScreen.equals(screen)) {
                List<String> open = open(onScreen);
                if (open.isEmpty()) {
                    if (!built.contains(onScreen)) built(onScreen, now);
                } else if (built.remove(onScreen) | !open.equals(leftOpen.put(onScreen, open))) {
                    // Said when what is open changed: a hub passed through again and again says it once.
                    emit(new ExploreEvent.Building(onScreen, clock.get(onScreen), open, now));
                }
            }
        }
        waited.clear();
        onScreen = screen;
        onSince = now;
        if (screen != null && !built.contains(screen) && open(screen).isEmpty()) built(screen, now);
    }

    /** Splits {@code ms} charged to {@code screen}: what the run waited on at stops, the rest to what it did. */
    private void charge(String screen, long ms) {
        EnumMap<Spent, Long> on = spentOn.computeIfAbsent(screen, s -> new EnumMap<>(Spent.class));
        long rest = ms;
        for (Map.Entry<Spent, Long> wait : waited.entrySet()) {
            long part = Math.min(rest, wait.getValue());
            on.merge(wait.getKey(), part, Long::sum);
            rest -= part;
        }
        if (rest > 0) on.merge(doing, rest, Long::sum);
    }

    /** A restarted run's clocks go on where the journal left them: its built screens stay built. */
    private void restoreClocks() {
        JournalView.Build build = JournalView.of(journal).build();
        for (ExploreEvent.Built b : build.built()) {
            built.add(b.screen());
            clock.put(b.screen(), b.ms());
        }
        for (ExploreEvent.Building b : build.open()) {
            clock.put(b.screen(), b.ms());
            leftOpen.put(b.screen(), b.open());
        }
        aside.addAll(build.aside());
        // A low-yield stop resumed with ignore leaves its screen as it is across the restart too.
        Map<String, String> lowYieldOn = new HashMap<>();
        for (ExploreEvent event : journal.events()) {
            if (event instanceof ExploreEvent.Stopped s && s.stop().kind() == StopKind.LOW_YIELD
                    && s.stop().screen() != null) lowYieldOn.put(s.stop().id(), s.stop().screen());
            if (event instanceof ExploreEvent.Fixed f && f.why().equals("ignored") && lowYieldOn.containsKey(f.stopId())) {
                leftAsIs.add(lowYieldOn.get(f.stopId()));
            }
        }
        // A built screen a stop of the last run is still parked on is open again.
        long now = nowMs.getAsLong();
        for (String screen : List.copyOf(built)) {
            List<String> open = open(screen);
            if (open.isEmpty()) continue;
            built.remove(screen);
            leftOpen.put(screen, open);
            emit(new ExploreEvent.Building(screen, clock.getOrDefault(screen, 0L), open, now));
        }
    }

    private void built(String screen, long now) {
        built.add(screen);
        aside.remove(screen);
        asideAt.remove(screen);
        emit(new ExploreEvent.Built(screen, clock.getOrDefault(screen, 0L), now));
        // A low-yield stop resumed with a fresh clock took once its screen is built; one parked on it is answered.
        String resumedId = yieldResumed.remove(screen);
        if (resumedId != null) emit(new ExploreEvent.Fixed(resumedId, "built on its fresh clock", now));
        parked.values().stream().filter(s -> s.kind() == StopKind.LOW_YIELD && screen.equals(s.screen()))
                .findFirst().ifPresent(s -> {
                    parked.remove(key(s));
                    journal.queue(List.copyOf(parked.values()));
                    emit(new ExploreEvent.Fixed(s.id(), "its screen was built", now));
                });
    }

    /**
     * What is still to do on {@code screen} before it is built, one line a kind ({@code "3 untried"}): inputs with no
     * picture, ways and inputs not tried yet, inputs with no verdict, ways waiting on their condition, stops parked
     * on it (missing facts, missing conditions, the rest), and, in a run that plays a plan, inputs that spend with no
     * action of its goals pressing them. Empty when nothing is.
     */
    List<String> open(String screen) {
        MapScreen on = map.screen(screen).orElse(null);
        if (on == null) return List.of();
        int uncropped = 0;
        for (Map.Entry<String, Input> input : on.inputs().entrySet()) {
            // An app's element is found by its tree alone, and only a website's is pictured by the run: an app's is
            // not held open for a shot nothing takes.
            if (input.getValue() instanceof Button button && !cropped(button, website())
                    && !pictures.contains(screen + "." + input.getKey())) uncropped++;
        }
        long untried = map.edges().stream().filter(e -> e.from().equals(screen) && untriedEdge(e)).count()
                + (readsTrees() ? untriedInputs(screen).size() : 0);
        long unjudged = map.untagged().stream().filter(name -> name.screen().equals(screen)).count();
        // A way whose condition says no is not tried yet: the screen waits for it to hold. An unknown one is untried.
        long waiting = map.edges().stream().filter(e -> e.from().equals(screen) && !e.onItsOwn()
                && !tried.contains(key(e)) && !avoided(key(e)) && Conditions.answer(e.when()) == Answer.NO).count();
        long facts = parkedOn(screen, StopKind.MISSING_FACT);
        long conditions = parkedOn(screen, StopKind.MISSING_CONDITION);
        // A low-yield stop is about the screen's yield, not something on it: it holds nothing open.
        long others = parked.values().stream().filter(stop -> screen.equals(stop.screen())).count() - facts - conditions
                - parkedOn(screen, StopKind.LOW_YIELD);
        long unplanned = website() ? 0 : unplanned(on);
        List<String> open = new ArrayList<>();
        if (uncropped > 0) open.add(uncropped + " uncropped");
        if (untried > 0) open.add(untried + " untried");
        if (unjudged > 0) open.add(unjudged + " without verdict");
        if (waiting > 0) open.add(waiting + " waiting on a condition");
        if (facts > 0) open.add(facts + (facts == 1 ? " fact missing" : " facts missing"));
        if (conditions > 0) open.add(conditions + (conditions == 1 ? " condition missing" : " conditions missing"));
        if (others > 0) open.add(others + (others == 1 ? " stop parked" : " stops parked"));
        if (unplanned > 0) open.add(unplanned + " spending for no goal");
        return open;
    }

    /** How many stops of {@code kind} on {@code screen} are parked: each one Claude has still to answer. */
    private long parkedOn(String screen, StopKind kind) {
        return parked.values().stream().filter(stop -> stop.kind() == kind && screen.equals(stop.screen())).count();
    }

    /**
     * How many of {@code on}'s inputs spend a fact with no action of the plan's goals pressing them: an input that
     * spends is there to meet a goal. None when the run plays no plan.
     */
    private long unplanned(MapScreen on) {
        Plan plan = play.plan();
        if (plan == null || plan.tiers().isEmpty()) return 0;
        Set<Goal> goals = planned(plan);
        List<Do> done = doneBy(goals);
        return on.inputs().values().stream()
                .filter(input -> input.spends() != null && input.never() == null)
                .filter(input -> on.actions().values().stream().noneMatch(action -> Objects.equals(action.input(), input)
                        && (action.serves().stream().anyMatch(goals::contains) || done.contains(action))))
                .count();
    }

    /** The goals {@code plan} plays, with every goal an action serving one of them needs first. */
    private Set<Goal> planned(Plan plan) {
        Set<Goal> goals = new HashSet<>();
        plan.tiers().forEach(tier -> goals.addAll(tier.goals()));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (MapScreen screen : map.screens().values()) {
                for (Do action : screen.actions().values()) {
                    if (action.serves().stream().anyMatch(goals::contains)) grew |= goals.addAll(action.goalNeeds());
                }
            }
        }
        return goals;
    }

    /** The actions whose being done is one of {@code goals}. */
    private static List<Do> doneBy(Set<Goal> goals) {
        List<Do> done = new ArrayList<>();
        for (Goal goal : goals) {
            if (!(goal instanceof Goal.Done doneGoal)) continue;
            try {
                done.add(doneGoal.action().get());
            } catch (RuntimeException | LinkageError unreadable) {
                // a goal whose action cannot be named uses none; the plan run says why when it plays it
            }
        }
        return done;
    }

    /** Whether {@code button} has a picture the run can find it by: its own, its element's shot, or another's it sits beside. */
    private static boolean cropped(Button button, boolean website) {
        if (button.element() != null) return !website || button.element().shot() != null;
        return button.picture() != null || !button.cycle().isEmpty() || button.beside() != null;
    }

    /** What the notes handed on with {@code screen}'s settled stop are: its forms' tries, presses, or earlier answers. */
    private String settledWhy(String screen) {
        if (notes.stream().anyMatch(TreeAnswer.InputRule.class::isInstance)) {
            return "what " + screen + "'s forms showed when tried, never submitted";
        }
        if (notes.stream().allMatch(TreeAnswer.SameInput.class::isInstance)) {
            return "inputs of " + screen + " an older read made twice, one control and its holder";
        }
        return notes.stream().allMatch(TreeAnswer.InputNote.class::isInstance)
                ? "what pressing " + notes.size() + " inputs did, read off the page"
                : "what the run settled before it met this screen";
    }

    /**
     * The inputs of {@code screen} an older read made twice, a control and another of its name around it, as answers
     * handed on with the next settled stop: the map keeps one ({@link TreeAnswer#sameInputs}).
     */
    private List<TreeAnswer.SameInput> sameInputs(String screen) {
        MapScreen on = map.screen(screen).orElseThrow();
        UiTree tree = play.state().map(Effect.State::tree).orElse(null);
        if (tree == null) return List.of();
        Map<String, Element> elements = new LinkedHashMap<>();
        for (Map.Entry<String, Input> input : on.inputs().entrySet()) {
            if (input.getValue() instanceof Button button && button.element() != null) {
                elements.put(input.getKey(), button.element());
            }
        }
        Map<String, Set<String>> leads = new HashMap<>();
        for (Edge edge : map.edges()) {
            if (edge.from().equals(screen) && edge.input() != null) {
                leads.computeIfAbsent(edge.input(), k -> new HashSet<>()).add(edge.to());
            }
        }
        List<TreeAnswer.SameInput> same = TreeAnswer.sameInputs(screen, tree, elements, leads);
        // Gone from what this run presses too, before the notes are written.
        same.forEach(s -> tried.add(screen + "." + s.field()));
        return same;
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
    private boolean pressInput(String screen, String field, Look look) {
        String key = screen + "." + field;
        Input input = map.screen(screen).map(s -> s.inputs().get(field)).orElse(null);
        Guards.Refusal refusal = input == null ? guards.front(play.around(null)) : guards.check(input, play.around(input));
        Optional<Boolean> refused = refused(screen, key, field, refusal, look);
        if (refused.isPresent()) return refused.get();
        tried.add(key);
        // What the guard stopped before this press is not its doing.
        play.stopped();
        Effect.State before = play.state().orElse(Effect.State.of(null, ""));
        doing = Spent.PRESSING;
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
        // An error page the server answered is left for a screen of the map.
        if (after.httpError()) {
            if (!play.recover()) play.restart();
            return true;
        }
        // A list or menu left open catches every press after it (a select's list holds the page): the page is
        // loaded again so the next input is pressed on the page as it was.
        if (effect instanceof Effect.Changes) play.reopen();
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
        // A fault the plugin side's writing raised replays by settling again: the rebuilt SDK writes it this time.
        if (replayed && SKIPPED.equals(replay.source())) return rewrite(kind, screen, place, answers, missing);
        if (replayed || resumed != null && resumed.kind() == kind && resumed.place().equals(place)) {
            return fault(kind, screen, place, "did-not-take", missing + "; what the tree settled was written and did "
                    + "not take", "settled again: " + answers
                    + settledEvidence(replayed ? replay.fault().id() : resumed.id()));
        }
        return settled(kind, screen, place, answers, missing);
    }

    /**
     * Settles the place of the fault being replayed again, a fault the writing of what it settled raised: resumed with
     * every line written on the rebuilt SDK, the fault is fixed and its memory goes; a line left out again is raised by
     * the next round, as any.
     */
    private Paused rewrite(StopKind kind, String screen, String place, List<TreeAnswer> answers, String missing) {
        // No longer replayed: the stop is a settled one, never turned back into the fault. Its file stays until written.
        String faultId = replay.fault().id();
        replay = null;
        Paused paused = settled(kind, screen, place, answers, missing);
        if (paused == Paused.RESUMED && skips.stream().noneMatch(skip -> skip.stop().place().equals(place))) {
            emit(new ExploreEvent.Fixed(faultId, "written on the rebuilt SDK", nowMs.getAsLong()));
            ResumeFile.delete(journal);
        }
        return paused;
    }

    /** The stop that hands {@code answers} and the notes not yet handed on to the plugin side to write. */
    private Paused settled(StopKind kind, String screen, String place, List<TreeAnswer> answers, String missing) {
        List<TreeAnswer> all = new ArrayList<>(answers);
        all.addAll(notes);
        notes.clear();
        String id = kind.id() + "-" + (stops + 1);
        settledAt.put(id, List.copyOf(all));
        emit(new ExploreEvent.Settled(id, all, nowMs.getAsLong()));
        settling = true;
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
        // An unknown never closes a way: one behind a fact not read yet is pressed, as the screen map asks it.
        return !edge.onItsOwn() && !tried.contains(key) && !avoided(key) && Conditions.holds(edge.when());
    }

    private boolean open(Edge edge) {
        try {
            return play.open(edge);
        } catch (RuntimeException e) {
            rethrowStop(e);
            return true;
        }
    }

    /**
     * The nearest screen by the map's edges, from {@code screen}, with an edge not tried yet, or, where the run reads
     * trees, an input; not itself, nor, when {@code notAside}, one set aside.
     */
    private Optional<String> nearest(String screen, boolean notAside) {
        Set<String> reached = new LinkedHashSet<>(List.of(screen));
        Deque<String> queue = new ArrayDeque<>(reached);
        while (!queue.isEmpty()) {
            String at = queue.poll();
            for (Edge edge : map.edges()) {
                if (!edge.from().equals(at) || !reached.add(edge.to())) continue;
                String to = edge.to();
                // A website's input the run presses itself is work too: a dialog's button is no edge until pressed.
                boolean work = map.edges().stream().anyMatch(e -> e.from().equals(to) && untriedEdge(e))
                        || readsTrees() && !untriedInputs(to).isEmpty();
                if (work &&!(notAside && aside.contains(to))) return Optional.of(to);
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
        Spent waits = settling ? Spent.SETTLING : Spent.CLAUDE;
        settling = false;
        if (parked.containsKey(parkedKey(kind, place))) return null;
        // The stop a replayed fault was came back on the rebuilt SDK: the fault is still there, held again.
        if (fault == null && dev && replays(kind, place)) {
            settling = waits == Spent.SETTLING;
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
        long pausedAt = nowMs.getAsLong();
        Paused paused = pause(stop, neverParks);
        // A resume that gave the screen a fresh clock moved its start past the pause: only what follows it is waited.
        waited.merge(waits, Math.max(0, nowMs.getAsLong() - Math.max(pausedAt, onSince)), Long::sum);
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
        if (fixed.kind() == StopKind.LOW_YIELD && fixed.screen() != null && !built.contains(fixed.screen())) {
            yielded(fixed.screen(), ignore);
            if (!ignore && parkedStop == null) yieldResumed.put(fixed.screen(), fixed.id());
        }
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
