package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Walks the bot's screen map on the game: which screen it is on ({@link #where}), and the way to another
 * ({@link #go}), one edge at a time. Each step does the edge's input, then waits for the next screen's marker. A
 * popup that shows is closed first; a step that lands elsewhere is read again and the way planned anew from there.
 * A way back with no input is the game's usual close button when the walk is given one, else Escape.
 *
 * <p>What is not always there decides the answer when no way is open now: one only through an input that comes and
 * goes is {@link Outcome#NOT_NOW}, one only through a locked input {@link Outcome#LOCKED}, and none at all — the map
 * names a picture the game no longer shows — {@link Outcome#MISSING}, the sign the bot needs its Upkeep. Steps that
 * keep landing elsewhere are {@link Outcome#STUCK}: the map may be right and the game slow or in another state.
 */
public final class ScreenWalk {

    /** How a walk ended. */
    public enum Outcome { ARRIVED, NOT_NOW, LOCKED, MISSING, STUCK, STOPPED }

    /** A walk's end: how, the screen it ended on (null when none was known), the screens reached, and why. */
    public record Result(Outcome outcome, String at, List<String> walked, String why) {
        public boolean arrived() {
            return outcome == Outcome.ARRIVED;
        }
    }

    /**
     * How long one step is given for the next screen's marker to show, by a run and by the assistant's walks alike:
     * fixed, not the bot's timeouts, since a screen slow to draw is the game's pace, not the bot's.
     */
    public static final long STEP_MS = 10_000;
    /** How often the screen is read while waiting for a marker. */
    static final long POLL_MS = 250;
    /** How many popups in a row are closed before a step; more is a loop. */
    static final int MOST_POPUPS = 3;
    /** How many steps may land elsewhere before the walk gives up. */
    static final int MOST_REPLANS = 3;

    private final ScreenGraph graph;
    private final Reach reach;
    private final long stepMs;
    private final Consumer<String> reached;
    private final Input close;

    /**
     * A walk over {@code graph} by {@code reach}, giving each step {@code stepMs} to show the next screen's marker,
     * and telling {@code reached} each screen it is on by constant, the one it starts on first.
     */
    public ScreenWalk(ScreenGraph graph, Reach reach, long stepMs, Consumer<String> reached) {
        this(graph, reach, stepMs, reached, null);
    }

    /** As above; a way back with no input clicks {@code close}, the game's usual close button, while it shows. */
    public ScreenWalk(ScreenGraph graph, Reach reach, long stepMs, Consumer<String> reached, Input close) {
        this.graph = graph;
        this.reach = reach;
        this.stepMs = stepMs;
        this.reached = reached == null ? name -> {} : reached;
        this.close = close;
    }

    /** The screen the game shows now, {@code near}'s neighbours looked for first; popups aside. */
    public Optional<String> where(String near) {
        return reach.look().flatMap(frame -> whereOn(frame, near));
    }

    /**
     * The same, on {@code frame}: a look taken already. A screen opened over another (a panel, a dialog) leaves the
     * one under it in view, so of the screens whose markers show, one another shown screen was opened from is not
     * the answer: Info luck over Tavern is Info luck (2026-10-15). That is every screen a way into it is on, and the
     * screens above those in the tree; when ways loop so that every screen shown is under another, the nearest
     * shown is the answer.
     */
    public Optional<String> whereOn(BufferedImage frame, String near) {
        Set<String> order = new LinkedHashSet<>();
        if (near != null && graph.screens().containsKey(near)) {
            order.add(near);
            for (ScreenGraph.Edge edge : graph.edges()) if (edge.from().equals(near)) order.add(edge.to());
        }
        order.addAll(graph.screens().keySet());
        List<String> told = order.stream().filter(c -> !graph.screens().get(c).isPopup()
                && graph.screens().get(c).marker() != null).toList();
        List<String> shown = shown(told, frame);
        Set<String> under = new HashSet<>();
        for (String c : shown) {
            for (String from : graph.opensFrom(c)) {
                if (from.equals(c)) continue;
                for (Optional<String> up = Optional.of(from); up.isPresent() && under.add(up.get()); ) {
                    up = graph.parent(up.get());
                }
            }
        }
        return shown.stream().filter(c -> !under.contains(c)).findFirst().or(() -> shown.stream().findFirst());
    }

    /** Walks to {@code target} from where the game is, {@code near} the screen it was last seen on, or null. */
    public Result go(String target, String near) {
        Optional<GameScreen> goal = graph.screen(target);
        if (goal.isEmpty()) {
            return new Result(Outcome.MISSING, near, List.of(), target + " is not in the bot's screen map.");
        }
        if (goal.get().isPopup()) {
            return new Result(Outcome.NOT_NOW, near, List.of(), graph.shown(target) + " is a popup: it shows on its "
                    + "own, and no walk leads to it.");
        }
        List<String> walked = new ArrayList<>();
        Set<ScreenGraph.Edge> missed = new HashSet<>();     // its picture was not on the screen
        Set<ScreenGraph.Edge> strayed = new HashSet<>();    // done, and the game showed another screen
        String at = near;
        String told = null;        // the screen the walk last said it is on
        String unmarked = null;    // a screen reached that has no marker to tell it by
        String left = null;        // the screen the step to it was done on
        int most = graph.screens().size() * 2 + MOST_REPLANS;
        for (int step = 0; step < most; step++) {
            if (Thread.currentThread().isInterrupted()) return new Result(Outcome.STOPPED, at, walked, "Stopped.");
            Optional<BufferedImage> frame = closePopups(reach.look());
            if (frame.isEmpty()) return new Result(Outcome.STUCK, at, walked, "The screen could not be captured.");
            Optional<String> here = whereOn(frame.get(), at);
            // A screen with no marker, just reached, is where the game is while the one it was opened from shows
            // under it, or none shows.
            if (unmarked != null && (here.isEmpty() || here.get().equals(left))) here = Optional.of(unmarked);
            unmarked = null;
            if (here.isEmpty()) {
                return new Result(Outcome.STUCK, at, walked, "The game shows no screen of the bot's map"
                        + (at == null ? "." : "; it was last on " + graph.shown(at) + "."));
            }
            at = here.get();
            if (!at.equals(told)) {
                // The screen it starts on is told too, so whoever shows where the run is knows from the first step.
                if (told != null) walked.add(at);
                reached.accept(at);
                told = at;
            }
            if (at.equals(target)) return new Result(Outcome.ARRIVED, at, walked, "On " + graph.shown(at) + ".");
            String from = at;
            BufferedImage seen = frame.get();
            Optional<List<ScreenGraph.Edge>> path = graph.pathTo(at, target,
                    edge -> !missed.contains(edge) && !strayed.contains(edge) && open(edge, from, seen));
            if (path.isEmpty()) return blocked(at, target, missed, strayed, walked);
            ScreenGraph.Edge next = path.get().getFirst();
            if (!perform(next, seen)) {
                missed.add(next);    // another way, or why none
                continue;
            }
            if (arrives(next)) {
                // Read again from the next look: a way back may land on another screen than the map's parent.
                at = next.to();
                if (graph.screens().get(at).marker() == null) {
                    unmarked = at;
                    left = from;
                }
                continue;
            }
            strayed.add(next);
            if (strayed.size() > MOST_REPLANS) {
                return new Result(Outcome.STUCK, at, walked, graph.shown(next.to()) + " did not show after "
                        + label(next) + " on " + graph.shown(from) + "; " + strayed.size() + " steps landed elsewhere.");
            }
        }
        return new Result(Outcome.STUCK, at, walked, "No way to " + graph.shown(target) + " in " + most + " steps.");
    }

    /**
     * Whether {@code edge} can be taken: never through a lock. On the screen the game is on, what comes and goes only
     * while it shows, a button with looks only while it is not greyed out, and a way needing one look only while the
     * button has it. Anywhere, a way or an input whose condition answers false; the rest is looked for when the walk
     * gets there.
     */
    private boolean open(ScreenGraph.Edge edge, String at, BufferedImage frame) {
        Input via = edge.via();
        if (via == null) return true;
        if (via.lock() != null) return false;
        if (!Conditions.holds(edge.when()) || !Conditions.holds(via.when())) return false;
        if (!edge.from().equals(at)) return true;
        if (via.act() == Input.Act.KEY) return true;
        if (edge.state() == null && via.variants().isEmpty()) {
            return !via.comesAndGoes() || via.picture() != null && reach.find(via.picture(), frame).isPresent();
        }
        Optional<Reach.Seen> seen = reach.seen(via, frame);
        if (seen.isEmpty()) return !via.comesAndGoes() && edge.state() == null;
        if (seen.get().state() == State.DISABLED) return false;
        return edge.state() == null || edge.state() == seen.get().state();
    }

    /** Why no way is open from {@code at} to {@code target}: what the ways there would take. */
    private Result blocked(String at, String target, Set<ScreenGraph.Edge> missed, Set<ScreenGraph.Edge> strayed,
                           List<String> walked) {
        Predicate<ScreenGraph.Edge> known = edge -> !missed.contains(edge) && !strayed.contains(edge);
        String to = graph.shown(target);
        if (graph.pathTo(at, target, known.and(edge -> edge.via() == null || edge.via().lock() == null)).isPresent()) {
            return new Result(Outcome.NOT_NOW, at, walked, "The way to " + to + " is not on the screen now: it comes "
                    + "and goes.");
        }
        if (graph.pathTo(at, target, known).isPresent()) {
            return new Result(Outcome.LOCKED, at, walked, "The way to " + to + " is locked.");
        }
        if (!strayed.isEmpty()) {
            return new Result(Outcome.STUCK, at, walked, "No way to " + to + " is left: " + labels(strayed)
                    + " landed elsewhere" + (missed.isEmpty() ? "." : ", and " + labels(missed) + " was not found."));
        }
        return new Result(Outcome.MISSING, at, walked, "No way from " + graph.shown(at) + " to " + to + " is on the "
                + "game as the map has it" + (missed.isEmpty() ? "." : ": " + labels(missed) + " not found."));
    }

    /**
     * Does {@code edge}'s input; a way back with none is the usual close button while it shows, else Escape: the walk
     * is on a screen below another then, never on the top one, where Escape may ask to quit the game. False when its
     * picture is not on {@code frame}.
     */
    private boolean perform(ScreenGraph.Edge edge, BufferedImage frame) {
        if (edge.via() == null) {
            if (close == null || !reach.perform(close, frame)) reach.key(Key.ESCAPE);
            return true;
        }
        return reach.perform(edge.via(), frame);
    }

    /**
     * Whether {@code edge} got somewhere within a step's time, a popup that shows meanwhile closed. A way in arrives
     * once its screen's marker shows. A way back arrives once the screen left is gone and another of the map shows:
     * a panel's parent stays in view under it, and a screen opened from another than its parent in the tree (an
     * {@code alsoFrom}) closes back to that one, which the walk reads from the next look (2026-10-15).
     */
    private boolean arrives(ScreenGraph.Edge edge) {
        GameScreen screen = graph.screens().get(edge.to());
        ImageTemplate left = edge.back() ? graph.screens().get(edge.from()).marker() : null;
        ImageTemplate shows = edge.back() ? left : screen.marker();
        if (shows == null) {
            reach.pause(POLL_MS);
            return true;    // a screen with no marker cannot be told: the walk takes it as reached, or left
        }
        long deadline = reach.nowMs() + stepMs;
        do {
            reach.pause(POLL_MS);
            Optional<BufferedImage> frame = closePopups(reach.look());
            if (frame.isPresent() && (edge.back() ? reach.find(left, frame.get()).isEmpty() && (screen.marker() == null
                    || whereOn(frame.get(), edge.to()).filter(c -> !c.equals(edge.from())).isPresent())
                    : reach.find(screen.marker(), frame.get()).isPresent())) {
                return true;
            }
        } while (reach.nowMs() < deadline && !Thread.currentThread().isInterrupted());
        return false;
    }

    /** The popup whose marker {@code frame} shows, if any: what {@link #whereOn} leaves out. */
    public Optional<String> popupOn(BufferedImage frame) {
        List<String> popups = popups();
        return popups.isEmpty() ? Optional.empty() : first(popups, frame);
    }

    /** Which look of {@code constant} {@code frame} shows: the first whose marker is found; empty for none. */
    public Optional<ScreenVariant> lookOn(String constant, BufferedImage frame) {
        GameScreen screen = graph.screens().get(constant);
        if (screen == null) return Optional.empty();
        return screen.variants().stream().filter(v -> v.marker() != null && reach.find(v.marker(), frame).isPresent())
                .findFirst();
    }

    private List<String> popups() {
        return graph.screens().entrySet().stream()
                .filter(e -> e.getValue().isPopup() && e.getValue().marker() != null).map(e -> e.getKey()).toList();
    }

    /** {@code frame}, or the one after closing each popup it shows, up to {@link #MOST_POPUPS}. */
    private Optional<BufferedImage> closePopups(Optional<BufferedImage> frame) {
        List<String> popups = popups();
        for (int i = 0; i < MOST_POPUPS && frame.isPresent() && !popups.isEmpty(); i++) {
            Optional<String> popup = first(popups, frame.get());
            if (popup.isEmpty()) return frame;
            Input back = graph.screens().get(popup.get()).back();
            if (back == null || !reach.perform(back, frame.get())) reach.key(Key.ESCAPE);
            reach.pause(POLL_MS);
            frame = reach.look();
        }
        return frame;
    }

    /** The first of {@code constants} whose marker is on {@code frame}, all looked for on that one frame. */
    private Optional<String> first(List<String> constants, BufferedImage frame) {
        return shown(constants, frame).stream().findFirst();
    }

    /** Those of {@code constants} whose marker is on {@code frame}, in their order, all looked for on that frame. */
    private List<String> shown(List<String> constants, BufferedImage frame) {
        List<ImageTemplate> markers = constants.stream().map(c -> graph.screens().get(c).marker()).toList();
        List<Optional<Rect>> found = reach.findEach(markers, frame);
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < constants.size(); i++) if (found.get(i).isPresent()) shown.add(constants.get(i));
        return shown;
    }

    private static String label(ScreenGraph.Edge edge) {
        return edge.via() == null ? "the way back" : edge.via().label();
    }

    private static String labels(Set<ScreenGraph.Edge> edges) {
        return String.join(", ", edges.stream().map(ScreenWalk::label).toList());
    }
}
