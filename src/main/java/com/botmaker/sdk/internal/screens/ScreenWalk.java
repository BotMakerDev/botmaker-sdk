package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
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
 * popup of the map that shows is closed first ({@link #closePopups}, which the run's {@code PopupGuard} calls too),
 * and an overlay the map lacks cleared once ({@link #uncover}); a step that lands elsewhere is read again and the way
 * planned anew from there.
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
    private final Button close;
    /** Whether {@link #go} clears an overlay the map lacks: a run's walks do, the assistant's leave it to the user. */
    private final boolean uncovers;

    /** How long a screen of the map is given to show once an overlay's close was pressed: short, a wait timed out. */
    static final long UNCOVER_MS = 2_000;

    /**
     * A walk over {@code graph} by {@code reach}, giving each step {@code stepMs} to show the next screen's marker,
     * and telling {@code reached} each screen it is on by constant, the one it starts on first.
     */
    public ScreenWalk(ScreenGraph graph, Reach reach, long stepMs, Consumer<String> reached) {
        this(graph, reach, stepMs, reached, null);
    }

    /** As above; a way back with no input clicks {@code close}, the game's usual close button, while it shows. */
    public ScreenWalk(ScreenGraph graph, Reach reach, long stepMs, Consumer<String> reached, Button close) {
        this(graph, reach, stepMs, reached, close, false);
    }

    private ScreenWalk(ScreenGraph graph, Reach reach, long stepMs, Consumer<String> reached, Button close,
                       boolean uncovers) {
        this.graph = graph;
        this.reach = reach;
        this.stepMs = stepMs;
        this.reached = reached == null ? name -> {} : reached;
        this.close = close;
        this.uncovers = uncovers;
    }

    /**
     * This walk, clearing once an overlay the map lacks when {@link #go} finds no screen of the map ({@link #uncover},
     * Escape included): a run's {@code Navigate}. Without it, such a walk ends {@link Outcome#STUCK}, as the
     * assistant's do while the screen it explores is not mapped yet.
     */
    public ScreenWalk uncovering() {
        return new ScreenWalk(graph, reach, stepMs, reached, close, true);
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
     * shown is the answer. Of shown screens that neither hides, the one told by more markers wins: its set holds the
     * other's and more (a tab of a window, the window's title and its own), the nearest first between equal sets.
     */
    public Optional<String> whereOn(BufferedImage frame, String near) {
        Set<String> order = new LinkedHashSet<>();
        Set<String> close = new HashSet<>();
        if (near != null && graph.screens().containsKey(near)) {
            order.add(near);
            close.add(near);
            for (ScreenGraph.Edge edge : graph.edges()) {
                if (edge.from().equals(near)) order.add(edge.to());
                if (edge.from().equals(near) || edge.to().equals(near)) {
                    close.add(edge.from());
                    close.add(edge.to());
                }
            }
            for (Optional<String> up = graph.parent(near); up.isPresent() && close.add(up.get()); ) {
                up = graph.parent(up.get());
            }
        }
        order.addAll(graph.screens().keySet());
        List<String> told = order.stream().filter(c -> !graph.screens().get(c).isPopup()
                && graph.screens().get(c).hasMarker()).toList();
        // The screens one step from where the game was looked for first: every marker of the map is looked for only
        // when none of them shows (the user's call, 2026-10-16: a walk knows where its last input led).
        if (!close.isEmpty()) {
            List<String> nearShown = shown(told.stream().filter(close::contains).toList(), frame);
            Optional<String> nearby = shownTop(nearShown);
            if (nearby.isPresent()) {
                // A screen further off may stand above it, its markers still in view: looked for too.
                List<String> above = told.stream()
                        .filter(c -> !close.contains(c) && standsAbove(c, nearby.get())).toList();
                if (above.isEmpty()) return nearby;
                List<String> both = new ArrayList<>(nearShown);
                both.addAll(shown(above, frame));
                return shownTop(both);
            }
        }
        return shownTop(shown(told, frame));
    }

    /**
     * Whether {@code over}, shown, would be the answer in place of {@code under}: it is opened over it (from it or a
     * screen opened from it), or its markers hold all of {@code under}'s and more (a tab of a window).
     */
    private boolean standsAbove(String over, String under) {
        Set<String> marks = new HashSet<>(Markers.of(graph.screens().get(under)).shown().stream()
                .map(ImageTemplate::id).toList());
        List<String> its = Markers.of(graph.screens().get(over)).shown().stream().map(ImageTemplate::id).toList();
        if (its.size() > marks.size() && its.containsAll(marks)) return true;
        for (String from : graph.opensFrom(over)) {
            if (from.equals(over)) continue;
            for (Optional<String> up = Optional.of(from); up.isPresent(); up = graph.parent(up.get())) {
                if (up.get().equals(under)) return true;
                if (up.get().equals(over)) break;
            }
        }
        return false;
    }

    /** Of {@code shown}, the screen nothing else shown hides, as {@link #whereOn} picks it. */
    private Optional<String> shownTop(List<String> shown) {
        Set<String> under = new HashSet<>();
        for (String c : shown) {
            for (String from : graph.opensFrom(c)) {
                if (from.equals(c)) continue;
                for (Optional<String> up = Optional.of(from); up.isPresent() && under.add(up.get()); ) {
                    up = graph.parent(up.get());
                }
            }
        }
        // Ranked by markers only among the screens nothing shown hides; the loop's fallback keeps the nearest first.
        return shown.stream().filter(c -> !under.contains(c))
                .min(Comparator.comparingInt((String c) -> -graph.screens().get(c).markers().size()))
                .or(() -> shown.stream().findFirst());
    }

    /**
     * Walks to {@code target} from where the game is, {@code near} the screen it was last seen on, or null. A popup of
     * the map that will not close ends it {@link Outcome#STUCK}; an overlay the map lacks is cleared once
     * ({@link #uncover}), and the walk's answer says so.
     */
    public Result go(String target, String near) {
        try {
            return walk(target, near);
        } catch (Stayed stayed) {
            return new Result(Outcome.STUCK, near, List.of(), stayed.getMessage());
        }
    }

    private Result walk(String target, String near) {
        Optional<MapScreen> goal = graph.screen(target);
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
        String uncovered = null;   // what cleared an overlay the map lacks, said once arrived
        boolean lookedAgain = false;
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
            if (here.isEmpty() && uncovers && uncovered == null) {
                if (!lookedAgain) {
                    // A frame caught mid-transition shows no screen either: one more look before pressing anything.
                    lookedAgain = true;
                    reach.pause(POLL_MS);
                    continue;
                }
                Optional<Uncovered> cleared = uncover(frame.get(), at, true);
                if (cleared.isPresent()) {
                    uncovered = covered(at, cleared.get());
                    continue;    // planned from a fresh look: this one is the covered frame
                }
            }
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
            if (at.equals(target)) {
                return new Result(Outcome.ARRIVED, at, walked, "On " + graph.shown(at) + "."
                        + (uncovered == null ? "" : " " + uncovered));
            }
            String from = at;
            BufferedImage seen = frame.get();
            Predicate<ScreenGraph.Edge> known = edge -> !missed.contains(edge) && !strayed.contains(edge);
            Optional<List<ScreenGraph.Edge>> path = graph.pathTo(at, target,
                            known.and(edge -> open(edge, from, seen, false)))
                    .or(() -> graph.pathTo(from, target, known.and(edge -> open(edge, from, seen, true))));
            if (path.isEmpty()) return blocked(at, target, missed, strayed, walked);
            ScreenGraph.Edge next = path.get().getFirst();
            if (!perform(next, seen)) {
                missed.add(next);    // another way, or why none
                continue;
            }
            if (arrives(next)) {
                // Read again from the next look: a way back may land on another screen than the map's parent.
                at = next.to();
                if (!graph.screens().get(at).hasMarker()) {
                    unmarked = at;
                    left = from;
                }
                continue;
            }
            strayed.add(next);
            if (strayed.size() > MOST_REPLANS) {
                return new Result(Outcome.STUCK, at, walked, graph.shown(next.to()) + " did not show after "
                        + next.label() + " on " + graph.shown(from) + "; " + strayed.size() + " steps landed elsewhere.");
            }
        }
        return new Result(Outcome.STUCK, at, walked, "No way to " + graph.shown(target) + " in " + most + " steps.");
    }

    /**
     * Whether {@code edge} can be taken: never through a lock. On the screen the game is on, what comes and goes only
     * while it shows, a button with looks only while it is not greyed out, and a way needing one look only while the
     * button has it. Anywhere, a way or an input whose condition answers false; the rest is looked for when the walk
     * gets there. Without {@code search}, nothing that comes and goes but what shows now: a search of a list both
     * ways that finds nothing took 35 s, so another way is planned first (Firestone's Tavern, 2026-10-16).
     */
    private boolean open(ScreenGraph.Edge edge, String at, BufferedImage frame, boolean search) {
        Input via = edge.via();
        if (via == null) return true;
        if (via.lock() != null) return false;
        if (!Conditions.holds(edge.when()) || !Conditions.holds(via.when())) return false;
        // Further on, what comes and goes is planned with only when nothing else is open: planned first, Home's
        // Tavern icon pulled a walk on Town back to Home, which found it gone and went to Town again.
        if (!edge.from().equals(at)) return search || !via.comesAndGoes();
        if (!(via instanceof Button button)) return true;
        Optional<Strip> strip = stripOf(edge);
        boolean searched = strip.isPresent() && (search || !via.comesAndGoes());
        if (edge.state() == null && button.looks().isEmpty()) {
            // One a strip scrolls to is looked for by the click, which scrolls first.
            return !via.comesAndGoes() || searched || reach.seen(button, null, edge.pick(), frame).isPresent();
        }
        Optional<Reach.Seen> seen = reach.seen(button, strip.orElse(null), edge.pick(), frame);
        // A member out of view is looked at once the click scrolls to it.
        if (seen.isEmpty()) return searched || strip.isEmpty() && !via.comesAndGoes() && edge.state() == null;
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
        return reach.perform(edge.via(), stripOf(edge).orElse(null), edge.pick(), frame);
    }

    /** The strip of the screen {@code edge} leaves that its input is a member of; empty for one at its place. */
    private Optional<Strip> stripOf(ScreenGraph.Edge edge) {
        return graph.screen(edge.from()).flatMap(from -> from.stripOf(edge.via()));
    }

    /**
     * Whether {@code edge} got somewhere within a step's time, a popup that shows meanwhile closed. A way in arrives
     * once its screen's markers show. A way back arrives once the screen left is gone and another of the map shows:
     * a panel's parent stays in view under it, and a screen opened from another than its parent in the tree (an
     * {@code alsoFrom}) closes back to that one, which the walk reads from the next look (2026-10-15).
     */
    private boolean arrives(ScreenGraph.Edge edge) {
        MapScreen screen = graph.screens().get(edge.to());
        Markers left = Markers.of(graph.screens().get(edge.from()));
        Markers shows = edge.back() ? left : Markers.of(screen);
        if (!shows.any()) {
            reach.pause(POLL_MS);
            return true;    // a screen with no marker cannot be told: the walk takes it as reached, or left
        }
        long deadline = reach.nowMs() + stepMs;
        do {
            reach.pause(POLL_MS);
            Optional<BufferedImage> frame = closePopups(reach.look());
            if (frame.isPresent() && (edge.back() ? !left.on(reach, frame.get()) && (!screen.hasMarker()
                    || whereOn(frame.get(), edge.to()).filter(c -> !c.equals(edge.from())).isPresent())
                    : shows.on(reach, frame.get()))) {
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

    /** Whether the map has a popup it can tell: one with markers. Without one, closing popups looks at nothing. */
    public boolean hasPopups() {
        return !popups().isEmpty();
    }

    /** A popup of the map that its way out and Escape both left on the screen. */
    public static final class Stayed extends RuntimeException {
        Stayed(String why) {
            super(why);
        }
    }

    /**
     * {@code frame}, or the look after closing each popup of the map it shows, up to {@link #MOST_POPUPS}. A popup
     * closes by its {@code back} (Offline progress: its Claim, the choice that closes it), else Escape, and is closed
     * once its markers are gone; still shown a step later, Escape once more.
     *
     * @throws Stayed when a popup is still shown after that
     */
    public Optional<BufferedImage> closePopups(Optional<BufferedImage> frame) {
        List<String> popups = popups();
        for (int i = 0; i < MOST_POPUPS && frame.isPresent() && !popups.isEmpty(); i++) {
            Optional<String> popup = first(popups, frame.get());
            if (popup.isEmpty()) return frame;
            frame = close(popup.get(), frame.get());
        }
        return frame;
    }

    /** The look once {@code popup} is closed: its way out, then Escape when it stays. */
    private Optional<BufferedImage> close(String popup, BufferedImage frame) {
        Markers shows = Markers.of(graph.screens().get(popup));
        ScreenGraph.Edge back = graph.backOf(popup).orElseThrow();
        boolean escaped = back.via() == null || !reach.perform(back.via(), frame);
        if (escaped) reach.key(Key.ESCAPE);
        Optional<BufferedImage> after = gone(shows);
        if (after.isEmpty() && !escaped) {
            reach.key(Key.ESCAPE);
            after = gone(shows);
        }
        if (after.isPresent()) return after;
        throw new Stayed(graph.shown(popup) + " stayed after " + (escaped ? "Escape"
                : back.label() + " and Escape") + ".");
    }

    /** The first look within a step's time that no longer shows {@code shows}; empty when it stays. */
    private Optional<BufferedImage> gone(Markers shows) {
        long deadline = reach.nowMs() + stepMs;
        do {
            reach.pause(POLL_MS);
            Optional<BufferedImage> frame = reach.look();
            if (frame.isPresent() && !shows.on(reach, frame.get())) return frame;
        } while (reach.nowMs() < deadline && !Thread.currentThread().isInterrupted());
        return Optional.empty();
    }

    /** What cleared an overlay the map does not know, and the screen of the map it uncovered. */
    public record Uncovered(String at, String how) {}

    /**
     * Clears what covers the game when {@code frame} shows no screen and no popup of the map: an overlay the map
     * lacks. The map's close buttons (the usual one first, then each way back that is a picture) are looked for on
     * {@code frame} and the first found clicked; with {@code escape}, Escape when none is, or when the click uncovered
     * nothing: only a walk, which knows it left a mapped screen, presses it. Empty when
     * {@code frame} shows a screen of the map already, when the map tells no screen yet, or when nothing brought one
     * back.
     */
    public Optional<Uncovered> uncover(BufferedImage frame, String near, boolean escape) {
        boolean tells = graph.screens().values().stream().anyMatch(s -> !s.isPopup() && s.hasMarker());
        if (!tells || Thread.currentThread().isInterrupted() || whereOn(frame, near).isPresent()
                || popupOn(frame).isPresent()) {
            return Optional.empty();
        }
        List<Button> closes = closeButtons();
        List<Optional<Rect>> found = reach.findEach(closes.stream().map(Button::picture).toList(), frame);
        for (int i = 0; i < closes.size(); i++) {
            if (found.get(i).isEmpty()) continue;
            reach.click(reach.pointOn(found.get(i).get()));
            Optional<String> at = screenWithin(near);
            if (at.isPresent()) return Optional.of(new Uncovered(at.get(), closes.get(i).picture().id()));
            break;
        }
        if (!escape || Thread.currentThread().isInterrupted()) return Optional.empty();
        reach.key(Key.ESCAPE);
        return screenWithin(near).map(at -> new Uncovered(at, "Escape"));
    }

    /** The note for an overlay the map lacks, over {@code near} (the screen last seen, or null), cleared as said. */
    public String covered(String near, Uncovered cleared) {
        return "An unmapped popup covered " + (near == null ? "the game" : graph.shown(near)) + " (" + cleared.how()
                + " closed it): map it in Upkeep.";
    }

    /** The map's close buttons: the usual one, then every way back that clicks a picture, each picture once. */
    private List<Button> closeButtons() {
        List<Button> closes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Input> backs = new ArrayList<>();
        if (close != null) backs.add(close);
        for (String constant : graph.screens().keySet()) {
            graph.backOf(constant).map(ScreenGraph.Edge::via).ifPresent(backs::add);
        }
        for (Input back : backs) {
            if (back instanceof Button button && button.picture() != null && seen.add(button.picture().id())) {
                closes.add(button);
            }
        }
        return closes;
    }

    /** The screen of the map the game shows within {@link #UNCOVER_MS}, its popups closed; empty when none shows. */
    private Optional<String> screenWithin(String near) {
        long deadline = reach.nowMs() + Math.min(stepMs, UNCOVER_MS);
        do {
            reach.pause(POLL_MS);
            Optional<String> at = closePopups(reach.look()).flatMap(f -> whereOn(f, near));
            if (at.isPresent()) return at;
        } while (reach.nowMs() < deadline && !Thread.currentThread().isInterrupted());
        return Optional.empty();
    }

    /** Which look of {@code constant} {@code frame} shows: the first whose markers show; empty for none. */
    public Optional<ScreenVariant> lookOn(String constant, BufferedImage frame) {
        MapScreen screen = graph.screens().get(constant);
        if (screen == null) return Optional.empty();
        List<ScreenVariant> told = screen.variants().stream().filter(ScreenVariant::hasMarker).toList();
        List<Boolean> on = Markers.onEach(reach, told.stream().map(Markers::of).toList(), frame);
        for (int i = 0; i < told.size(); i++) if (on.get(i)) return Optional.of(told.get(i));
        return Optional.empty();
    }

    private List<String> popups() {
        return graph.screens().entrySet().stream()
                .filter(e -> e.getValue().isPopup() && e.getValue().hasMarker()).map(e -> e.getKey()).toList();
    }

    /** The first of {@code constants} whose markers are on {@code frame}, all looked for on that one frame. */
    private Optional<String> first(List<String> constants, BufferedImage frame) {
        return shown(constants, frame).stream().findFirst();
    }

    /** Those of {@code constants} whose markers are on {@code frame}, in their order, all looked for on that frame. */
    private List<String> shown(List<String> constants, BufferedImage frame) {
        List<Boolean> on = Markers.onEach(reach,
                constants.stream().map(c -> Markers.of(graph.screens().get(c))).toList(), frame);
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < constants.size(); i++) if (on.get(i)) shown.add(constants.get(i));
        return shown;
    }

    private static String labels(Set<ScreenGraph.Edge> edges) {
        return String.join(", ", edges.stream().map(ScreenGraph.Edge::label).toList());
    }
}
