package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.internal.bot.BotConstants.InputName;
import com.botmaker.sdk.internal.bot.InstalledRun;
import com.botmaker.sdk.internal.screens.Conditions;
import com.botmaker.sdk.internal.screens.MapWalk;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
        STOPPED,
        /** Its budget ({@link Settings#hours}) ran out. */
        BUDGET
    }

    /**
     * How long a stop waits before it is parked, how long with no press is stuck, how many plan rounds with no new
     * screen before a walk to an untried input, one press's time, the whole run's (null: no end), and how often a
     * paused run looks for a command.
     */
    public record Settings(Duration parkAfter, Duration stuckAfter, int planRoundsWithoutNew, Duration edge,
                           Duration hours, Duration poll) {

        public static final Settings DEFAULT = new Settings(Duration.ofMinutes(10), Duration.ofMinutes(15), 5,
                Duration.ofSeconds(30), null, Duration.ofSeconds(1));

        /** These settings with a budget of {@code hours}; null for none. */
        public Settings within(Duration hours) {
            return new Settings(parkAfter, stuckAfter, planRoundsWithoutNew, edge, hours, poll);
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
    private enum Paused { RESUMED, BACK, PARKED, END }

    private final Journal journal;
    private final Play play;
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

    public ExploreLoop(ScreenGraph map, Play play, Reload reload, Guards guards, Journal journal, Settings settings,
                       LongSupplier nowMs) {
        this.map = map;
        this.play = play;
        this.reload = reload;
        this.guards = guards;
        this.journal = journal;
        this.settings = settings;
        this.nowMs = nowMs;
        // A queue a run before left is avoided too; stop numbers go on from the journal's.
        for (Stop stop : journal.queue()) parked.put(key(stop), stop);
        stops = (int) journal.events().stream().filter(ExploreEvent.Stopped.class::isInstance).count();
    }

    /** Runs rounds until a command stops it or its budget runs out. */
    public End run() {
        began = nowMs.getAsLong();
        lastPress = began;
        while (true) {
            if (spent()) return End.BUDGET;
            if (journal.takeCommand().orElse(null) instanceof Journal.Command.End) return End.STOPPED;
            if (!round()) return spent() ? End.BUDGET : End.STOPPED;
        }
    }

    /** Whether the run's budget is spent; never, without one. */
    private boolean spent() {
        return settings.hours() != null && nowMs.getAsLong() - began >= settings.hours().toMillis();
    }

    /** One round; false when a command, or the budget, ended the run while it was paused. */
    boolean round() {
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
            return goesOn;
        } catch (RuntimeException e) {
            rethrowStop(e);
            String at = whereAfterCrash();
            return stop(StopKind.CRASH, at, (at == null ? "unknown" : at) + ":" + e.getClass().getSimpleName(),
                    Look.NOTHING, "the bot's code threw: " + e, false) != Paused.END;
        }
    }

    private boolean play() {
        Optional<String> where = play.where();
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
        if (seen.add(screen)) {
            planRounds = 0;
            emit(new ExploreEvent.NewScreen(screen, from, by, nowMs.getAsLong()));
        }
        Look look = play.look(screen);
        Optional<Stop.Text> value = missingFact(screen, look);
        if (value.isPresent()) {
            Stop.Text text = value.get();
            return stop(StopKind.MISSING_FACT, screen, textPlace(screen, text.box()), look, "\"" + text.text().strip()
                    + "\" reads as a value no readout covers: add a readout into a fact, or resume with ignore",
                    false) != Paused.END;
        }
        if (resumed != null && screen.equals(resumed.screen())) passed(StopKind.MISSING_FACT, resumed.place());
        Optional<Edge> untried = untried(screen);
        if (untried.isPresent()) return press(screen, untried.get(), Mode.UNTRIED, look);
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
        if (resumed == null || resumed.kind() != kind || !resumed.place().equals(place)) return;
        emit(new ExploreEvent.Fixed(resumed.id(), "reloaded as generation " + generation, nowMs.getAsLong()));
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
        lastPress = nowMs.getAsLong();
        presses.addLast(what);
        while (presses.size() > Stop.PRESSES) presses.removeFirst();
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
        if (parked.containsKey(parkedKey(kind, place))) return null;
        String id = kind.id() + "-" + ++stops;
        Stop stop = new Stop(id, kind, screen, place, play.keep(id), look.texts(), look.elements(), look.facts(),
                List.copyOf(presses), missing, nowMs.getAsLong());
        boolean again = resumed != null && resumed.same(stop);
        if (again) stop = new Stop(id, kind, screen, place, stop.framePath(), stop.texts(), stop.elements(),
                stop.facts(), stop.lastPresses(), "the fix did not take: " + missing, stop.at());
        resumed = null;
        emit(new ExploreEvent.Stopped(stop, again, nowMs.getAsLong()));
        Paused paused = pause(stop, neverParks);
        // The time paused is Claude's, not the run's: it does not count as nothing pressed.
        lastPress = nowMs.getAsLong();
        return paused;
    }

    /** Waits on {@code stop} for a command, the game back in front, or its parking time. */
    private Paused pause(Stop stop, boolean neverParks) {
        String id = stop.id();
        long deadline = nowMs.getAsLong() + settings.parkAfter().toMillis();
        while (true) {
            Optional<Journal.Command> command = journal.takeCommand();
            if (command.orElse(null) instanceof Journal.Command.End || spent()) return Paused.END;
            // A resume of a stop that is neither this one nor parked is stale: it changes nothing.
            if (command.orElse(null) instanceof Journal.Command.Resume(String stopId, boolean ignore)
                    && (stop.id().equals(stopId) || parked.values().stream().anyMatch(s -> s.id().equals(stopId)))) {
                Optional<Paused> resumedNow = resume(stop, stopId, ignore);
                if (resumedNow.isPresent()) return resumedNow.get();
                deadline = nowMs.getAsLong() + settings.parkAfter().toMillis();
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

    /**
     * A resume of {@code stopId} while paused on {@code open}: the bot reloaded. Refused, the run stays paused (empty).
     * A parked stop resumed leaves the queue and its place is tried again; the open stop stays open (empty). The open
     * stop resumed: what the next round meets says whether the fix took.
     */
    private Optional<Paused> resume(Stop open, String stopId, boolean ignore) {
        Stop parkedStop = open.id().equals(stopId) ? null
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
