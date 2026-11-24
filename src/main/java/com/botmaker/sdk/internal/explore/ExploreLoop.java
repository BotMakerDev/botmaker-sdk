// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.bot.BotConstants.InputName;
import com.botmaker.sdk.internal.bot.InstalledRun;
import com.botmaker.sdk.internal.explore.ScreenClock.Spent;
import com.botmaker.sdk.internal.explore.tree.Effect;
import com.botmaker.sdk.internal.explore.tree.FormRules;
import com.botmaker.sdk.internal.explore.tree.Locator;
import com.botmaker.sdk.internal.explore.tree.Overlay;
import com.botmaker.sdk.internal.explore.tree.RouteGuess;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.sdk.internal.vision.Images;
import com.botmaker.sdk.internal.vision.TemplateSource;
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

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * <em>Keep exploring</em>'s loop (spec §1–3): each round, where the game is, everything read on it, one press chosen,
 * guarded, pressed and judged. What the bot does not know stops the run and hands the game to Claude through the
 * {@link Journal}; a resume reloads the bot and checks the stop is gone, and a stop nobody takes up in time is parked
 * and its place avoided.
 *
 * <p>The loop holds the round and what it reads; what to press next is {@link Chooser}'s, each screen's clock and
 * budget {@link ScreenClock}'s, a fault's replay {@link Replay}'s, and the waits on stops and the parked queue
 * {@link Pauses}' (2026-11-21).
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
        /** The frontier's best press for the run's goal (spec 2026-11-24-explore-agents-design). */
        BEST("best", "Best for the goal"),
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
        /**
         * Nothing is left to press and no screen of the map is open: the map is built (2026-11-24; before, such a run
         * idled until {@link Settings#stuckAfter}).
         */
        DONE("done", "Done: nothing left to try"),
        /**
         * It waited on a stop that cannot park, another window in front of the game, for {@link Settings#stuckAfter}
         * with nothing it could press (2026-11-20).
         */
        STUCK("stuck", "Stuck: nothing could be pressed"),
        /**
         * Its goal cannot be reached within its caps: nothing is left to press within the depth cap and nothing waits
         * on the user (2026-11-24, spec 2026-11-24-explore-agents-design).
         */
        UNREACHABLE("unreachable", "Unreachable within the caps"),
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
     * How long a stop waits before it is parked, how long with no press is stuck, one press's time, the whole run's
     * (null: no end), how often a paused run looks for a command, the screens it is on before it ends, each counted
     * once (0: no end), and the bounds of the time a screen's own clock may run unbuilt before the run sets it aside
     * for another open one (null: never). The presses it makes and the agent sessions the crew starts for it before it
     * ends (0: no end), and how many edges from home the frontier's best press may be (spec
     * 2026-11-24-explore-agents-design).
     */
    public record Settings(Duration parkAfter, Duration stuckAfter, Duration edge, Duration hours, Duration poll,
                           int maxScreens, ScreenBudget screenBudget, int maxPresses, int maxCalls, int depthCap) {

        /** The frontier's depth cap when none is given. */
        public static final int DEPTH_CAP = 6;

        public static final Settings DEFAULT = new Settings(Duration.ofMinutes(10), Duration.ofMinutes(15),
                Duration.ofSeconds(30), null, Duration.ofSeconds(1), 0,
                new ScreenBudget(Duration.ofMinutes(2), Duration.ofMinutes(15)));

        /** Settings with no press or call budget and the default depth cap. */
        public Settings(Duration parkAfter, Duration stuckAfter, Duration edge, Duration hours, Duration poll,
                        int maxScreens, ScreenBudget screenBudget) {
            this(parkAfter, stuckAfter, edge, hours, poll, maxScreens, screenBudget, 0, 0, DEPTH_CAP);
        }

        /** These settings with {@code budget} for each screen's clock; null for none. */
        public Settings screens(ScreenBudget budget) {
            return new Settings(parkAfter, stuckAfter, edge, hours, poll, maxScreens, budget, maxPresses, maxCalls,
                    depthCap);
        }

        /** These settings with a budget of {@code hours}; null for none. */
        public Settings within(Duration hours) {
            return new Settings(parkAfter, stuckAfter, edge, hours, poll, maxScreens, screenBudget, maxPresses,
                    maxCalls, depthCap);
        }

        /** These settings ending once the run has been on {@code screens} screens; 0 for no end. */
        public Settings upTo(int screens) {
            return new Settings(parkAfter, stuckAfter, edge, hours, poll, screens, screenBudget, maxPresses, maxCalls,
                    depthCap);
        }

        /** These settings ending after {@code presses} presses and {@code calls} agent sessions; 0 for no end. */
        public Settings spending(int presses, int calls) {
            return new Settings(parkAfter, stuckAfter, edge, hours, poll, maxScreens, screenBudget, presses, calls,
                    depthCap);
        }

        /** These settings with the frontier's best press at most {@code depth} edges from home. */
        public Settings deep(int depth) {
            return new Settings(parkAfter, stuckAfter, edge, hours, poll, maxScreens, screenBudget, maxPresses,
                    maxCalls, depth);
        }
    }

    /**
     * The bounds of a screen's budget: what its work takes at the run's measured press time, never under {@code floor}
     * nor over {@code cap}.
     */
    public record ScreenBudget(Duration floor, Duration cap) {

        /** The same budget for every screen, whatever it holds. */
        public static ScreenBudget fixed(Duration budget) {
            return new ScreenBudget(budget, budget);
        }

        /** {@code work} between floor and cap. */
        public Duration fit(Duration work) {
            return work.compareTo(floor) < 0 ? floor : work.compareTo(cap) > 0 ? cap : work;
        }
    }

    /**
     * What was read on the game: the frame's every line, the tree's elements, the facts' values, and the places on
     * the frame that the screen's readouts cover.
     */
    public record Look(List<Stop.Text> texts, List<Rect> covered) {

        public static final Look NOTHING = new Look(List.of(), List.of());

        public Look {
            texts = texts == null ? List.of() : List.copyOf(texts);
            covered = covered == null ? List.of() : List.copyOf(covered);
        }
    }

    /**
     * What a stop carries of the screen beside its text, read only when the run stops, never each round: a tree's read
     * costs a dump of the app.
     *
     * @param elements the tree's nodes that read, one line each; none without a tree
     * @param facts    what the stop's screen's readouts show now, by the fact each feeds (its field when it feeds none)
     */
    public record Shown(List<String> elements, Map<String, String> facts) {

        public static final Shown NOTHING = new Shown(List.of(), Map.of());

        public Shown {
            elements = elements == null ? List.of() : List.copyOf(elements);
            facts = facts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        }
    }

    /** The game, as the loop plays it: {@link MapWalk}'s moves, and what reading, guarding and a plan need. */
    public interface Play extends MapWalk.Moves {

        /** Reads the game on {@code screen} (null: none of the map): its readouts into the facts, the frame's text. */
        Look look(String screen);

        /** What the game shows now of {@code screen} (null: none of the map), for a stop: its elements and facts. */
        default Shown shown(String screen) {
            return Shown.NOTHING;
        }

        /** What is around a press of {@code input} now, for the guards; null: a press of no input, its front only. */
        Guards.Around around(Input input);

        /** What is in front of the bot now. */
        Foreground front();

        /** Saves the frame the game shows now as {@code name}; where it is, or {@code ""} when it was not saved. */
        String keep(String name);

        /**
         * Saves the screen shown now as {@code name}, for its shot: a website's whole page, below the viewport too, and
         * an app's frame as {@link #keep} saves it.
         */
        default String keepWhole(String name) {
            return keep(name);
        }

        /** The bot's plan, whose goals say which spending inputs have a use; {@link Plan#NONE} for none. */
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
         * Walks to {@code screen} as {@link #goTo(String)} does, never by a way {@code refused} names: the run's own
         * walks keep off a press nobody watching may make. Whether the game shows {@code screen} now.
         */
        boolean goTo(String screen, Predicate<Edge> refused);

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

        /**
         * Whether a picture called {@code mark} matches what the game shows now: a goal's witness by picture. False
         * for a game that cannot match one (the picture check is the SDK's own matcher, wired with the crew's marks).
         */
        default boolean shows(String mark) {
            return false;
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
    private final Pauses pauses;
    private final Chooser chooser;
    private final ScreenClock clock;
    private final Replay replay;

    private final Set<String> seen = new HashSet<>();
    /** The screens whose inputs made twice this process looked for; never restored from a run's memory. */
    private final Set<String> merged = new HashSet<>();
    /** The places a guard refused this run, way or input, with the guard's kind: each said once, never restored. */
    private final Set<String> refused = new HashSet<>();
    private final Set<String> worked = new HashSet<>();
    /** The text places a missing-fact stop was raised on: once each. */
    private final Set<String> answered = new HashSet<>();
    private final Deque<String> presses = new ArrayDeque<>();
    /** How many times in a row the server answered an error page after each place: a way, an input or a page. */
    private final Map<String, Integer> errorPages = new HashMap<>();
    private long began;
    private long lastPress;
    /** How many rounds in a row found nothing to press since the press at {@link #idleMark}; ends the run at {@link #IDLE_ROUNDS}. */
    private int idleRounds;
    private long idleMark = -1;
    /** Whether the stop for what is still open was raised in this idle spell. */
    private boolean idleStopped;
    /** Whether the run ended because nothing was left to try. */
    private boolean finished;
    /** Idle rounds that must agree before the run ends or stops on them: one read can miss a page still loading. */
    private static final int IDLE_ROUNDS = 3;
    private int stops;
    /** The edge last pressed, by its key: where an unknown screen came from. */
    private String lastEdge;
    /** The way or input being pressed, {@code "SHOP.buy"}, while its press runs: what a crash in it names. */
    private String pressing;
    private String from;
    private String by;
    /** The last walk, {@code from→to}, until a press: a set-aside screen a walk came back to does not walk again. */
    private String lastWalk;
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
    /** Whether the stop raised next hands what the run settled on: its wait is settling, not Claude's. */
    private boolean settling;
    /** The inputs this run took a picture of ({@code SCREEN.field}), before the map it plays holds them. */
    private final Set<String> pictures = new HashSet<>();
    /**
     * The screens whose whole page this run took for their shot: once each, so a shot taken before it was whole, of
     * the viewport alone, is taken again (2026-11-21).
     */
    private final Set<String> shot = new HashSet<>();
    /** The screens whose forms were tried: once each. */
    private final Set<String> probed = new HashSet<>();
    /** The screens a run has said once that it has nothing to press on. */
    private final Set<String> idleSaid = new HashSet<>();
    /** What the run settled at each stop, by its id: the evidence when the plugin side's write of it falls short. */
    private final Map<String, List<TreeAnswer>> settledAt = new HashMap<>();
    /** Settled stops resumed with answers the plugin side left out, raised again at the next round's start. */
    private final Deque<Skip> skips = new ArrayDeque<>();
    /** What the run explores toward; {@link ExploreGoal#NONE}: the whole map. */
    private ExploreGoal goal = ExploreGoal.NONE;
    /** The presses the Reader ranked and the run has not made, best first. */
    private final Frontier frontier;
    /** Where the next read of the journal starts: the ranks and leases the plugin side wrote meanwhile. */
    private long journalRead;
    /** The presses this run made, and the agent sessions the crew started for it: its budget counts both. */
    private int pressCount;
    private int calls;
    /** Whether a witness held: the goal was reached. */
    private boolean reached;
    /** How many times a done was proposed: each frame kept under its own name. */
    private int proposals;

    /** The settled stop {@code stop}, resumed with {@code lines} left out of what it settled. */
    private record Skip(Stop stop, List<String> lines) {}

    /** What a command taken while paused on a stop does to the run. */
    private final Pauses.Answers answers = new Pauses.Answers() {
        @Override
        public Optional<Paused> resume(Stop open, String stopId, boolean ignore) {
            return ExploreLoop.this.resume(open, stopId, ignore);
        }

        @Override
        public void held(Stop stop, String why) {
            emit(new ExploreEvent.Faulted(stop.id(), "claude", why, "", play.sdkVersion(), nowMs.getAsLong()));
            memory(stop, stop.kind(), "claude").write(journal);
        }

        @Override
        public void refusedFault(String stopId) {
            emit(new ExploreEvent.Reloaded(stopId, generation, List.of(FAULT_RESUMES), nowMs.getAsLong()));
        }

        @Override
        public void stale(String stopId) {
            ExploreLoop.this.stale(stopId);
        }

        @Override
        public boolean spent() {
            return ExploreLoop.this.spent();
        }
    };

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
        this.pauses = new Pauses(journal, play, settings, nowMs, this::emit, dev);
        this.chooser = new Chooser(map, play, this::readsTrees, pauses::avoided);
        this.clock = new ScreenClock(settings.screenBudget(), nowMs, this::emit, this::open, chooser::left,
                pauses::built);
        this.replay = new Replay(journal, nowMs, this::emit);
        this.frontier = new Frontier(this::depth);
        // Stop numbers go on past the journal's and the queue's, so a stop this run raises never takes the id of one a
        // run before parked.
        int raised = (int) journal.events().stream().filter(ExploreEvent.Stopped.class::isInstance).count();
        stops = Math.max(raised, journal.queue().stream().mapToInt(s -> number(s.id())).max().orElse(0));
    }

    /** The number a stop's id ends with ({@code tap-12}: 12); 0 for one that ends with none. */
    static int number(String id) {
        int dash = id == null ? -1 : id.lastIndexOf('-');
        try {
            return dash < 0 ? 0 : Integer.parseInt(id.substring(dash + 1));
        } catch (NumberFormatException none) {
            return 0;
        }
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
        replay.from(file);
        return this;
    }

    /**
     * Keeps {@code budget} and {@code discover} (null for <em>Keep exploring</em>), what the run's entry was started
     * with, in the memory a held fault writes. Called before {@link #run}.
     */
    public ExploreLoop startedWith(ExploreBudget budget, DiscoverArgs discover) {
        replay.startedWith(budget, discover);
        return this;
    }

    /** Explores toward {@code goal}: best first off the frontier, ending once a witness holds. Called before {@link #run}. */
    public ExploreLoop toward(ExploreGoal goal) {
        this.goal = goal == null ? ExploreGoal.NONE : goal;
        return this;
    }

    /** Runs rounds until a command stops it or its budget runs out. */
    public End run() {
        began = nowMs.getAsLong();
        lastPress = began;
        // A command a run before left is not this run's.
        journal.dropCommands();
        End end = End.BROKE;
        try {
            if (replay.on()) {
                emit(new ExploreEvent.Restarted(replay.fault().id(), dev, began));
                restore();
                clock.restore(journal);
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
     * Takes back the memory of the fault being replayed and sets up its replay: the fault's place free to be met again,
     * and the game walked to the fault's screen. Passing the place says it is fixed and meeting it says the fix did not
     * take; other stops met meanwhile leave the replay waiting. A screen the map no longer has (the fix renamed it) is
     * said, and the run explores on. The file stays until the replay says, so a run restarted again before still
     * replays.
     */
    private void restore() {
        ResumeFile file = replay.file().orElseThrow();
        seen.addAll(file.seen());
        chooser.triedBefore(file.tried());
        worked.addAll(file.worked());
        answered.addAll(file.answered());
        Stop fault = file.fault();
        chooser.retry(file.replays(), fault.place());
        answered.remove(fault.place());
        String screen = fault.screen();
        if (screen != null && map.screen(screen).isEmpty()) {
            replay.fixed("its screen " + screen + " is no longer in the map: nothing to replay, the run explores on");
            return;
        }
        String press = file.replays() == StopKind.CRASH ? pressOf(fault.place()) : null;
        if (press != null && !mapped(press)) {
            replay.fixed(press + " is no longer in the map: nothing to replay, the run explores on");
            return;
        }
        if (screen == null) return;
        try {
            play.goTo(screen, this::refused);
        } catch (RuntimeException e) {
            // The first round finds where the game is, as after any walk that fell short.
            rethrowStop(e);
        }
    }

    /** Whether {@code press}, {@code "SHOP.buy"}, is still a way or an input of the map. */
    private boolean mapped(String press) {
        int dot = press.indexOf('.');
        String screen = press.substring(0, dot);
        String field = press.substring(dot + 1);
        return map.screen(screen).map(s -> s.inputs().containsKey(field)).orElse(false)
                || map.edges().stream().anyMatch(e -> e.from().equals(screen) && e.label().equals(field));
    }

    /** The way or input a crash's place {@code "SHOP.buy:IllegalStateException"} names; null when it names none. */
    static String pressOf(String place) {
        int colon = place.lastIndexOf(':');
        if (colon < 0) return null;
        String press = place.substring(0, colon);
        return press.contains(".") ? press : null;
    }

    /**
     * A crash's place is passed where it was raised: one that names a press when that press is made again and does
     * not throw ({@code press}), one that names none when a round plays through ({@code press} null).
     */
    private void passedCrash(String press) {
        if (resumed != null && resumed.kind() == StopKind.CRASH && Objects.equals(pressOf(resumed.place()), press)) {
            passed(StopKind.CRASH, resumed.place());
        }
        if (replay.crashedIn(press)) passed(StopKind.CRASH, replay.fault().place());
    }

    /** The run's memory, for a restart on the rebuilt SDK to go on from the fault {@code fault}. */
    private ResumeFile memory(Stop fault, StopKind replays, String source) {
        return replay.memory(fault, replays, source, seen, chooser.tried(), worked, answered);
    }

    private End rounds() {
        while (true) {
            absorb();
            if (spent()) return End.BUDGET;
            Journal.Command command = journal.takeCommand().orElse(null);
            if (command instanceof Journal.Command.End) return End.STOPPED;
            if (command instanceof Journal.Command.Done(String mark) && witnessed(mark)) return End.DONE;
            // Between stops only a parked stop can be resumed: its fix is loaded and its place tried again.
            if (command instanceof Journal.Command.Resume(String stopId, boolean ignore, List<String> skipped)) {
                // The fault being replayed is still held: only ignore lets it go, as while the run paused on it.
                if (replay.of(stopId)) {
                    if (ignore) {
                        replay.fixed("ignored");
                    } else {
                        emit(new ExploreEvent.Reloaded(stopId, generation, List.of(FAULT_RESUMES), nowMs.getAsLong()));
                    }
                } else if (pauses.holds(stopId)) {
                    pauses.keepLeftOut(stopId, skipped);
                    resume(null, stopId, ignore);
                } else {
                    stale(stopId);
                }
            }
            if (!round()) {
                return reached ? End.DONE : spent() ? End.BUDGET : pauses.stuck() ? End.STUCK
                        : finished ? finishedEnd() : End.STOPPED;
            }
        }
    }

    /**
     * How a run ends that has nothing left to press: done, with no goal; with one not reached, unreachable when
     * nothing waits on the user, else stuck on what does, said as a finding.
     */
    private End finishedEnd() {
        if (!goal.given()) return End.DONE;
        // Only what the Reader ranked toward the goal waits on the user for it: a denied label met on the way does not.
        List<Frontier.Parked> yours = frontier.needsYou().stream().filter(p -> p.entry().rank() > 0).toList();
        if (yours.isEmpty()) return End.UNREACHABLE;
        emit(new ExploreEvent.Finding(FindingKind.STUCK, String.join(", ", yours.stream()
                .map(p -> p.entry().key()).toList()), "the goal was not reached; " + yours.size()
                + " press(es) wait on you", nowMs.getAsLong()));
        return End.STUCK;
    }

    /**
     * Reads what the journal gained since the last read: a rank adds a press to the frontier (one nobody watching may
     * make is never pressed by the run either), a try takes one out, a worker's lease is an agent session spent.
     */
    private void absorb() {
        Journal.Read read = journal.events(journalRead);
        journalRead = read.next();
        for (ExploreEvent event : read.events()) {
            frontier.take(event);
            if (event instanceof ExploreEvent.Ranked ranked && !Frontier.tagOf(ranked.press(), ranked.tag()).unattended()) {
                hold(ranked.screen(), ranked.press());
            }
            if (event instanceof ExploreEvent.Leased leased && leased.role() != StopRole.SETTLER) calls++;
        }
    }

    /** Counts {@code press} of {@code screen}, a way or an input, as tried: the run never makes it unattended. */
    private void hold(String screen, String press) {
        chooser.tryOnce(Frontier.keyOf(screen, press));
        map.edges().stream().filter(e -> e.from().equals(screen) && e.label().equals(press))
                .forEach(e -> chooser.tryOnce(key(e)));
    }

    /** How many edges {@code screen} is from the map's home; {@link Integer#MAX_VALUE} when no way reaches it. */
    private int depth(String screen) {
        Optional<String> home = map.home();
        if (home.isEmpty() || home.get().equals(screen)) return 0;
        return map.pathTo(home.get(), screen, edge -> !refused(edge)).map(List::size).orElse(Integer.MAX_VALUE);
    }

    /**
     * Whether a walk of the run keeps off {@code edge}: a press nobody watching may make, waiting on the user or
     * denied by its label, is never made on the way to another screen either.
     */
    private boolean refused(Edge edge) {
        return frontier.held(edge.from(), edge.label()) || Frontier.denied(edge.label());
    }

    /**
     * A done proposed, {@code mark} what shows it (blank: none): the goal counts as reached only when a witness holds on
     * the frame shown now (the goal's screen, else the mark by picture or text), then said; else a finding says why
     * not, and the run goes on.
     */
    private boolean witnessed(String mark) {
        Optional<String> where = play.where();
        Look look = play.look(where.orElse(null));
        String frame = play.keep("done-" + ++proposals);
        Witness.Frame shown = new Witness.Frame(frame, where, name -> map.screen(name).isPresent(),
                look.texts().stream().map(Stop.Text::text).toList(), play::shows);
        List<Witness.Check> checks = new ArrayList<>();
        goal.goalScreen().ifPresent(name -> checks.add(new Witness.Check.ScreenOnMap(name)));
        if (mark != null && !mark.isBlank()) checks.add(new Witness.Check.PictureOrText(mark));
        for (Witness.Check check : checks) {
            Optional<WitnessCheck> how = Witness.how(check, shown);
            if (how.isEmpty()) continue;
            String said = check instanceof Witness.Check.ScreenOnMap(String name) ? name : mark;
            emit(new ExploreEvent.Witnessed(goal.said(), frame, how.get(), said, nowMs.getAsLong()));
            reached = true;
            return true;
        }
        emit(new ExploreEvent.Finding(FindingKind.NO_WITNESS, frame, checks.isEmpty()
                ? "done was proposed with no mark and the run has no goal screen: nothing to check"
                : "done was proposed, but no check held on the frame shown now", nowMs.getAsLong()));
        return false;
    }

    /** Whether the run's budget, of time, screens, presses or agent sessions, is spent; never, without one. */
    private boolean spent() {
        return settings.hours() != null && nowMs.getAsLong() - began >= settings.hours().toMillis()
                || settings.maxScreens() > 0 && seen.size() >= settings.maxScreens()
                || settings.maxPresses() > 0 && pressCount >= settings.maxPresses()
                || settings.maxCalls() > 0 && calls >= settings.maxCalls();
    }

    /** One round; false when a command, or the budget, ended the run while it was paused. */
    boolean round() {
        if (!skips.isEmpty()) return skipped(skips.poll()) != Paused.END;
        if (verdictsDue) {
            verdictsDue = false;
            List<InputName> untagged = map.untagged();
            if (!untagged.isEmpty()) {
                String names = String.join(", ", untagged.stream().map(n -> n.screen() + "." + n.field()).toList());
                // Parked like any stop nobody answers: the run goes on, and the guard still presses no input
                // without a verdict.
                Paused paused = stop(StopKind.NO_VERDICT, null, MAP, Look.NOTHING, untagged.size()
                        + " inputs have no verdict; say free, spends or never for each: " + names, false);
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
        // Only a press of this round can have thrown in it.
        pressing = null;
        try {
            boolean goesOn = play();
            passedCrash(null);
            return goesOn;
        } catch (RuntimeException e) {
            String press = pressing;
            pressing = null;
            rethrowStop(e);
            // A crash in a press is the screen's it was pressed on, wherever the game went.
            String at = press != null ? press.substring(0, press.indexOf('.')) : whereAfterCrash();
            // A crash in a press names it, so its replay presses that way or input again.
            String place = (press != null ? press : at == null ? "unknown" : at) + ":" + e.getClass().getSimpleName();
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
     * Raises again what the plugin side left out of the settled stop {@code skip.stop()}: a stop of its own,
     * {@link StopKind#UNWRITTEN}, that says the lines and what was settled, Claude's to write (2026-11-20: a write the
     * bot refused is the bot's to fix, never Discover's fault). Never let go unsaid.
     */
    private Paused skipped(Skip skip) {
        Stop stop = skip.stop();
        // What follows is this stop's own word on it: the resume is not checked again.
        if (resumed != null && resumed.id().equals(stop.id())) resumed = null;
        return stop(StopKind.UNWRITTEN, stop.screen(), stop.place(), Look.NOTHING, "what the run settled at "
                + stop.id() + " was not all written: " + String.join("; ", skip.lines())
                + settledEvidence(stop.id()), false);
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
            // With no way pressed last, the page's own path is the place, so two pages are never counted as one.
            String page = pagePlace(unknown.get());
            return errorPage(null, lastEdge != null ? lastEdge : page != null ? page : "unknown", unknown.get());
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
        // A screen of the map reached: the error page is not met again, and its count starts over.
        if (resumed != null) passed(StopKind.ERROR_PAGE, resumed.place());
        if (lastEdge != null) errorPages.remove(lastEdge);
        // A page settled as unknown before is passed once a screen of the map matches it.
        if (replay.on() && readsTrees()) play.state().map(ExploreLoop::pagePlace)
                .ifPresent(page -> passed(StopKind.UNKNOWN_SCREEN, page));
        if (seen.add(screen)) {
            // A new screen can be the way a parked press lacked: each is tried again.
            frontier.resume();
            emit(new ExploreEvent.NewScreen(screen, from, by, nowMs.getAsLong()));
            if (readsTrees()) play.state().ifPresent(state -> page(screen, state));
            if (website()) pictured(screen);
        }
        // Before anything on it is pressed: an input an older read made twice is never pressed again. Once a screen
        // per run, a run restarted on its memory included, so a merge an older build could not write is tried again.
        if (readsTrees() && map.screen(screen).isPresent() && merged.add(screen)) {
            notes.addAll(sameInputs(screen));
            // A screen the map holds with inputs its tree shows and no input finds is written out, never skipped.
            notes.addAll(missingInputs(screen));
        }
        // After the screen is said found and pictured: one with nothing to do is built once it is reached.
        clock(screen);
        // Exploring builds the map only: no value read here raises a missing fact (spec 2026-11-20, § Out of exploring).
        // The goal's screen reached is the goal reached, by a code check: the run ends done.
        if (goal.goalScreen().filter(screen::equals).isPresent() && witnessed("")) return false;
        Look look = play.look(screen);
        if (resumed != null && screen.equals(resumed.screen())) passed(StopKind.MISSING_FACT, resumed.place());
        if (replay.onScreen(screen)) passed(StopKind.MISSING_FACT, replay.fault().place());
        if (clock.overBudget(screen)) {
            // Its own work waits while another screen has some: that one is walked to first.
            // A walk that came back here does its own work instead of walking again.
            Optional<String> other = chooser.next(screen, to -> !clock.aside(to), this::refused);
            if (other.isPresent() && !(screen + "→" + other.get()).equals(lastWalk)) return walk(screen, other.get());
            Optional<String> lowYield = clock.lowYield(screen);
            if (lowYield.isPresent()) {
                Paused low = stop(StopKind.LOW_YIELD, screen, screen, look, lowYield.get(), false);
                // Parked already: the screen does its own work meanwhile.
                if (low != null) return low != Paused.END;
            }
        }
        // A goal-directed run presses the frontier's best first, wherever it is; with nothing ranked, as below.
        Optional<Boolean> best = best(screen, look);
        if (best.isPresent()) return best.get();
        // The screen is finished before it is left: its inputs left, which mostly stay, before its ways, which leave.
        if (readsTrees()) {
            // Once per screen the map holds: its inputs name the fields the tries found.
            if (!probed.contains(screen) && map.screen(screen).isPresent()) {
                probed.add(screen);
                notes.addAll(rules(screen));
            }
            Optional<String> input = chooser.inputsLeft(screen).stream().findFirst();
            if (input.isPresent()) return pressInput(screen, input.get(), look);
        }
        Optional<Edge> untried = chooser.untried(screen);
        if (untried.isPresent()) return press(screen, untried.get(), Mode.UNTRIED, look);
        if (readsTrees() && !notes.isEmpty()) {
            return settle(StopKind.SETTLED, screen, "notes " + screen + "#" + ++settledNotes, List.of(),
                    settledWhy(screen)) != Paused.END;
        }
        // Nothing left here the run presses itself: the nearest screen not done, a set-aside one last.
        Optional<String> next = chooser.next(screen, to -> !clock.aside(to), this::refused)
                .or(() -> chooser.next(screen, to -> true, this::refused));
        if (next.isPresent()) return walk(screen, next.get());
        // No screen to leave for, so none to come back from: the ways not seen here are tried, not waited on.
        if (chooser.giveUpNotSeen()) return true;
        if (idleSaid.add(screen)) {
            Say.info("Nothing to press on " + screen + ": the tree is " + (readsTrees() ? "read" : "not read") + "; "
                    + chooser.whyNoInput(screen), Map.of());
        }
        clock.doing(Spent.IDLE);
        if (idleEnds(screen, look)) return !finished;
        play.idle(settings.poll());
        return true;
    }

    /**
     * Nothing is pressable on any screen, and a few rounds in a row have said so: the run is done when no screen of the
     * map is open, else it stops at once on what is open (a role takes it) instead of idling to {@link
     * Settings#stuckAfter}. A way waiting for its condition, or a fault being replayed, is a reason to wait: those idle
     * as before. True when this round was spent ending or stopping.
     */
    private boolean idleEnds(String screen, Look look) {
        if (idleMark != lastPress) {
            idleMark = lastPress;
            idleRounds = 0;
            idleStopped = false;
        }
        if (++idleRounds < IDLE_ROUNDS || replay.on()) return false;
        List<String> open = new ArrayList<>();
        List<String> heldOff = new ArrayList<>();
        for (String each : map.screens().keySet()) {
            if (chooser.waiting(each) > 0) return false;
            List<String> why = open(each);
            if (why.isEmpty()) continue;
            // Only a press nobody watching may make leads there: not the run's to open, nor a reason to stop.
            if (!each.equals(screen) && map.pathTo(screen, each, edge -> true).isPresent()
                    && map.pathTo(screen, each, edge -> !refused(edge)).isEmpty()) {
                heldOff.add(map.shown(each));
            } else {
                open.add(map.shown(each) + ": " + String.join(", ", why));
            }
        }
        if (open.isEmpty()) {
            Say.info("Map complete: nothing is left to press on any of its " + map.screens().size() + " screens"
                    + (heldOff.isEmpty() ? "." : "; only a press you make reaches " + String.join(", ", heldOff)
                    + "."), Map.of());
            finished = true;
            return true;
        }
        if (idleStopped) return false;
        idleStopped = true;
        stop(StopKind.STUCK, screen, screen, look, "nothing is left the run can press itself; still open: "
                + String.join("; ", open), false);
        return true;
    }

    /**
     * The round spent on the frontier's best press within the depth cap: pressed when it is on {@code screen}, walked
     * to when it is elsewhere, parked when no way leads there, dropped when it is no longer pressable. Empty when the
     * frontier has nothing open within the cap: the round goes on as an unranked one.
     */
    private Optional<Boolean> best(String screen, Look look) {
        // Ranks a run with no goal finds (carried from one toward a goal) are not its order.
        if (!goal.given()) return Optional.empty();
        Optional<Frontier.Entry> next = frontier.next(settings.depthCap());
        if (next.isEmpty()) return Optional.empty();
        Frontier.Entry entry = next.get();
        if (!entry.screen().equals(screen)) {
            if (map.pathTo(screen, entry.screen(), edge -> !refused(edge)).isEmpty()) {
                frontier.park(entry, "no way from " + screen + " to " + entry.screen());
                return Optional.of(true);
            }
            // A walk there that came back here did not arrive: the entry waits for the map to change.
            if ((screen + "→" + entry.screen()).equals(lastWalk)) {
                frontier.park(entry, "the walk from " + screen + " to " + entry.screen() + " did not arrive");
                return Optional.of(true);
            }
            return Optional.of(walk(screen, entry.screen()));
        }
        Optional<Edge> way = map.edges().stream().filter(e -> e.from().equals(screen)
                && e.label().equals(entry.press()) && chooser.untriedEdge(e)).findFirst();
        if (way.isPresent()) return Optional.of(press(screen, way.get(), Mode.BEST, look));
        if (readsTrees() && chooser.inputsLeft(screen).contains(entry.press())) {
            return Optional.of(pressInput(screen, entry.press(), look));
        }
        // Tried already, or gone from the map: nothing left of it to press.
        frontier.pressed(screen, entry.press());
        return Optional.of(true);
    }

    /** The run is on {@code screen} now (null: on none of the map): its clock and what is looked for there move. */
    private void clock(String screen) {
        clock.moveTo(screen);
        chooser.visit(screen);
    }

    private boolean walk(String screen, String to) {
        clock.doing(Spent.WALKING);
        lastWalk = screen + "→" + to;
        lastEdge = null;
        from = screen;
        by = Mode.WALK.id();
        play.goTo(to, this::refused);
        return true;
    }

    /** {@code screen}'s budget ({@link ScreenClock#budget}). */
    Duration budget(String screen) {
        return clock.budget(screen);
    }

    /**
     * The run went past {@code place} without a stop of {@code kind} coming back: when that is the stop resumed
     * last, its fix took. A place is passed where its stop would be raised: a screen recognised after an unknown one,
     * a screen read with no value outside its readouts, a way pressed that arrived, a press after a stuck stop.
     */
    private void passed(StopKind kind, String place) {
        if (replay.replays(kind, place)) replay.fixed("replayed");
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
        chooser.tryOnce(key);
        return Optional.of(refusal.kind() != Guards.Kind.NO_VERDICT
                || stop(StopKind.NO_VERDICT, screen, key, look, label + ": " + refusal.why(), false) != Paused.END);
    }

    private boolean press(String screen, Edge edge, Mode mode, Look look) {
        String key = key(edge);
        Guards.Around around = play.around(edge.via());
        Guards.Refusal refusal = edge.via() == null ? guards.front(around) : guards.check(edge.via(), around);
        Optional<Boolean> refused = refused(screen, key, edge.label(), refusal, look);
        if (refused.isPresent()) return refused.get();
        if (denied(screen, key, edge.label())) return true;
        chooser.tryOnce(key);
        lastEdge = key;
        clock.doing(Spent.PRESSING);
        pressed(screen, edge.label());
        long before = nowMs.getAsLong();
        pressing = screen + "." + edge.label();
        Took took = play.take(edge, settings.edge().toMillis());
        clock.took(nowMs.getAsLong() - before);
        pressing = null;
        Arrival arrival = took == null || took.arrival() == null || nowMs.getAsLong() - before > settings.edge().toMillis()
                ? Arrival.TIMED_OUT : took.arrival();
        // A way back returns where the game came from: any screen with a way in, not only the one the map names.
        if (arrival == Arrival.ELSEWHERE && edge.back() && took.at() != null && cameFrom(screen, took.at())) {
            arrival = Arrival.AT_TARGET;
        }
        emit(new ExploreEvent.Tried(screen, edge.label(), mode, arrival,nowMs.getAsLong()));
        passedCrash(screen + "." + edge.label());
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

    /**
     * Whether {@code label} of {@code screen}, its place {@code key}, is a press nobody watching makes (sign out,
     * delete, buy, …): then it is set aside as tried and ranked destructive, so it waits on the user, never pressed.
     */
    private boolean denied(String screen, String key, String label) {
        // Checked at each press, not only once: a reload or a retry that forgets a try never makes a held press.
        if (frontier.held(screen, label)) {
            chooser.tryOnce(key);
            return true;
        }
        if (!Frontier.denied(label)) return false;
        chooser.tryOnce(key);
        emit(new ExploreEvent.Ranked(screen, label, 0, PressTag.DESTRUCTIVE, nowMs.getAsLong()));
        return true;
    }

    private void pressed(String screen, String what) {
        pressCount++;
        if (resumed != null) passed(StopKind.STUCK, resumed.place());
        if (replay.on()) passed(StopKind.STUCK, replay.fault().place());
        lastPress = nowMs.getAsLong();
        lastWalk = null;
        clock.pressed(screen);
        presses.addLast(screen + ": " + what);
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
                    || pictures.contains(screen + "." + input.getKey()) || shown(button.element())) continue;
            List<UiNode> found = ElementMatch.matches(button.element(), state.get().tree());
            if (found.size() != 1) continue;
            String field = input.getKey();
            play.picture(screen + "." + field, found.getFirst().bounds()).ifPresent(file -> inputs.put(field, file));
        }
        inputs.keySet().forEach(field -> pictures.add(screen + "." + field));
        // pictured runs on a website only: its shot is the whole page, taken again once a run.
        String frame = shot.add(screen) ? play.keepWhole("shot-" + screen) : "";
        if (!frame.isEmpty() || !inputs.isEmpty()) {
            emit(new ExploreEvent.Pictured(screen, frame, inputs, nowMs.getAsLong()));
        }
    }

    /**
     * Whether {@code element}'s picture shows it: one it has and that is not {@link Images#flat}, a crop cut before its
     * section showed (nunu.ai's, 2026-11-22), which this run takes again. One unread is left as it is.
     */
    private static boolean shown(Element element) {
        if (element.shot() == null) return false;
        try {
            Optional<TemplateSource.Found> found = TemplateSource.read(element.shot(), new ArrayList<>());
            if (found.isEmpty()) return true;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(found.get().bytes()));
            return image == null || !Images.flat(image);
        } catch (IOException unread) {
            return true;
        }
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
        long untried = map.edges().stream().filter(e -> e.from().equals(screen) && chooser.untriedEdge(e)).count()
                + (readsTrees() ? chooser.inputsLeft(screen).size() : 0);
        long unjudged = map.untagged().stream().filter(name -> name.screen().equals(screen)).count();
        // A way whose condition says no is not tried yet: the screen waits for it to hold. An unknown one is untried.
        long waiting = chooser.waiting(screen);
        long facts = pauses.on(screen, StopKind.MISSING_FACT);
        long conditions = pauses.on(screen, StopKind.MISSING_CONDITION);
        // A low-yield stop is about the screen's yield, not something on it: it holds nothing open.
        long others = pauses.on(screen) - facts - conditions - pauses.on(screen, StopKind.LOW_YIELD);
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
        same.forEach(s -> chooser.tryOnce(screen + "." + s.field()));
        return same;
    }

    /**
     * The inputs {@code screen}'s tree shows that no input of the map finds, as answers handed on with the next
     * settled stop, each with the guard's verdict: a map built by hand, or before every input was read, would otherwise
     * leave the screen "done" with its inputs never pressed.
     */
    private List<TreeAnswer> missingInputs(String screen) {
        MapScreen on = map.screen(screen).orElseThrow();
        UiTree tree = play.state().map(Effect.State::tree).orElse(null);
        if (tree == null) return List.of();
        Map<String, Element> elements = new LinkedHashMap<>();
        for (Map.Entry<String, Input> input : on.inputs().entrySet()) {
            if (input.getValue() instanceof Button button && button.element() != null) {
                elements.put(input.getKey(), button.element());
            }
        }
        return new ArrayList<>(TreeAnswer.missingInputs(screen, tree, elements, play::refusal,
                (field, located) -> play.picture(screen + "." + field, located.node().bounds()).orElse(null)));
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
        if (denied(screen, key, field)) return true;
        chooser.tryOnce(key);
        // What the guard stopped before this press is not its doing.
        play.stopped();
        Effect.State before = play.state().orElse(Effect.State.of(null, ""));
        clock.doing(Spent.PRESSING);
        pressed(screen, field);
        lastEdge = null;
        pressing = key;
        long started = nowMs.getAsLong();
        Optional<String> at = play.press(screen, field, settings.edge().toMillis());
        clock.took(nowMs.getAsLong() - started);
        pressing = null;
        Effect.State after = play.state().orElse(Effect.State.of(null, ""));
        Effect effect = Effect.judge(before, after, play.stopped());
        String to = at.orElse(null);
        Arrival arrival = to == null ? Arrival.UNKNOWN : to.equals(screen) ? Arrival.UNCHANGED : Arrival.AT_TARGET;
        emit(new ExploreEvent.Tried(screen, field, Mode.UNTRIED, arrival, nowMs.getAsLong()));
        passedCrash(key);
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
        notes.add(TreeAnswer.InputNote.of(screen, field, effect));
        // An input that failed before and fails again is the Cartographer's: a verdict, or a press fixed.
        if (effect instanceof Effect.Fails(String why) && input != null && input.failed() != null) {
            return stop(StopKind.FAILED_INPUT, screen, key, look, field + " failed again (" + why + "; before: "
                    + input.failed() + "): give it a verdict, or fix how it is pressed", false) != Paused.END;
        }
        // An error page the server answered is left for a screen of the map.
        if (after.httpError()) return errorPage(screen, key, after);
        errorPages.remove(key);
        passed(StopKind.ERROR_PAGE, key);
        // A list or menu left open catches every press after it (a select's list holds the page): the page is
        // loaded again so the next input is pressed on the page as it was.
        if (effect instanceof Effect.Changes) play.reopen();
        return true;
    }

    /**
     * The server answered {@code page}, an error page, after {@code place} on {@code screen} (null: none known): the
     * game is brought back to a screen of the map. The second time in a row on one place it is a stop of its own, said rather
     * than recovered in silence (R6). False when the run ended while paused on it.
     */
    private boolean errorPage(String screen, String place, Effect.State page) {
        Paused paused = errorPages.merge(place, 1, Integer::sum) < 2 ? null
                : stop(StopKind.ERROR_PAGE, screen, place, Look.NOTHING, "the server answered HTTP " + page.status()
                        + (page.place().isEmpty() ? "" : " at " + page.place()) + " twice after " + place
                        + "; the run went back to a screen of the map each time", false);
        if (paused == Paused.END) return false;
        if (!play.recover()) play.restart();
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
        if (pauses.parked(kind, place)) return null;
        boolean replayed = replay.replays(kind, place);
        // A resumed UNWRITTEN stop is the same place's answer, refused: settled again, it did not take either.
        if (replayed || resumed != null && (resumed.kind() == kind || resumed.kind() == StopKind.UNWRITTEN)
                && resumed.place().equals(place)) {
            return fault(kind, screen, place, "did-not-take", missing + "; what the tree settled was written and did "
                    + "not take", "settled again: " + answers
                    + settledEvidence(replayed ? replay.fault().id() : resumed.id()));
        }
        return settled(kind, screen, place, answers, missing);
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

    /** An edge's key: {@code "Home.shop→Shop"}. */
    static String key(Edge edge) {
        return edge.from() + "." + edge.label() + "→" + edge.to();
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
        boolean settles = settling;
        settling = false;
        if (pauses.parked(kind, place)) return null;
        // The stop a replayed fault was came back on the rebuilt SDK: the fault is still there, held again.
        if (fault == null && dev && replay.replays(kind, place)) {
            settling = settles;
            return stop(StopKind.FAULT, screen, place, look, missing, true, new Fault(kind, replay.source(),
                    "the stop came back as " + kind.id() + " after the SDK's reload"));
        }
        String id = kind.id() + "-" + ++stops;
        Shown shown = play.shown(screen);
        Stop stop = new Stop(id, kind, screen, place, play.keep(id), look.texts(), shown.elements(), shown.facts(),
                List.copyOf(presses), missing, nowMs.getAsLong());
        boolean again = resumed != null && resumed.same(stop) || kind == StopKind.FAULT && replay.on(place);
        if (again) stop = new Stop(id, kind, screen, place, stop.framePath(), stop.texts(), stop.elements(),
                stop.facts(), stop.lastPresses(), "the fix did not take: " + missing, stop.at());
        resumed = null;
        // A stop elsewhere, or one that only pauses on the fault's place, leaves the replay waiting for it. One of
        // another kind that set the place aside as tried means the fault is never met again: the replay is lost, and
        // said. Its own kind there is the fault again.
        if (replay.on(place)) {
            if (kind == StopKind.FAULT || kind == replay.kind()) {
                replay.met();
            } else if (chooser.tried(place)) {
                replay.lost(kind.id() + " was met on " + place + " before the fault could be replayed");
            }
        }
        emit(new ExploreEvent.Stopped(stop, again, nowMs.getAsLong()));
        if (fault != null) {
            emit(new ExploreEvent.Faulted(id, fault.source(), stop.missing(), fault.evidence(), play.sdkVersion(),
                    nowMs.getAsLong()));
            // Written before the run holds, so the SDK's reload restarts it where it is.
            memory(stop, fault.plain(), fault.source()).write(journal);
        }
        long pausedAt = nowMs.getAsLong();
        Paused paused = pauses.pause(stop, neverParks, answers);
        clock.waitedAt(pausedAt, settles);
        // The time paused is Claude's, not the run's: it does not count as nothing pressed.
        lastPress = nowMs.getAsLong();
        return paused;
    }

    /** A resume of {@code stopId}, neither open nor parked, answered as refused so the tool waiting on it hears. */
    private void stale(String stopId) {
        emit(new ExploreEvent.Reloaded(stopId, generation, List.of("No stop " + stopId + " is open or parked."),
                nowMs.getAsLong()));
    }

    /**
     * A resume of {@code stopId} while paused on {@code open} (null: between stops, a parked one): the bot reloaded,
     * but with {@code ignore}, which changes nothing in the bot and so compiles nothing (R8). Refused, the run stays
     * paused (empty). A parked stop resumed leaves the queue and its place is tried again; the open stop stays open
     * (empty). The open stop resumed: what the next round meets says whether the fix took.
     */
    private Optional<Paused> resume(Stop open, String stopId, boolean ignore) {
        Stop parkedStop = open != null && open.id().equals(stopId) ? null : pauses.parked(stopId).orElse(null);
        if (!ignore) {
            Reload.Reloaded done = reload.reload();
            if (!done.errors().isEmpty()) {
                emit(new ExploreEvent.Reloaded(stopId, generation, done.errors(), nowMs.getAsLong()));
                return Optional.empty();
            }
            generation = done.generation();
            map = done.map();
            chooser.use(map);
            // The reloaded map can hold the way a parked press lacked.
            frontier.resume();
            if (done.guards() != null) guards = done.guards();
            verdictsDue = true;
        }
        emit(new ExploreEvent.Reloaded(stopId, generation, List.of(), nowMs.getAsLong()));
        Stop fixed = parkedStop != null ? parkedStop : open;
        if (fixed.kind() == StopKind.LOW_YIELD && fixed.screen() != null) {
            clock.yielded(fixed.screen(), fixed.id(), ignore, parkedStop == null);
        }
        List<String> lines = pauses.takeLeftOut(fixed.id());
        if (lines != null && !ignore) skips.add(new Skip(fixed, lines));
        if (parkedStop != null) pauses.unpark(parkedStop);
        // Its place is tried again, so a fix that did not take shows; an ignored value is never raised again.
        chooser.retry(fixed.kind(), fixed.place());
        if (!(ignore && fixed.kind() == StopKind.MISSING_FACT)) answered.remove(fixed.place());
        if (parkedStop != null) {
            emit(new ExploreEvent.Fixed(parkedStop.id(), ignore ? "ignored" : "reloaded as generation " + generation,
                    nowMs.getAsLong()));
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
    static void rethrowStop(RuntimeException e) {
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
