package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The bot's {@code Screens} read as a graph, keyed by constant name ({@code HOME}): each screen's {@code from} is
 * the tree Discovery walks, its {@code alsoFrom} and {@code back} the other edges a run may take. Pure: it reads
 * values and answers, and writes nothing.
 *
 * <p>Discovery's order (the user's rule, 2026-10-14) is one algorithm for every screen: cut every input on it, try
 * each, and finish a screen an input opens before the next input of the screen it was opened from. So a screen's
 * inputs wait while one {@link #unfinishedBelow below} it is unfinished, and a branch begun is finished before
 * another ({@link #branchBegun}).
 *
 * <p>What is not always there is read off the edges, never flagged on a screen ({@link #presence}): a screen every
 * way into which passes an input that {@link Input#sometimes comes and goes} shows only at times, and one behind a
 * {@link Input#locked locked} input is locked.
 */
public final class ScreenGraph {

    /** How long a walk up or down may be: a map deeper than this has a loop its names hide. */
    private static final int DEEPEST = 64;

    /** Whether a screen can be reached now, read off the ways into it, worst last. */
    public enum Presence { ALWAYS, SOMETIMES, LOCKED }

    /** What a finished screen is: every input opened a screen, or not. */
    public enum Shape { MENU, ACTIVITY }

    /**
     * One edge a walk can take: {@code via} done on {@code from} reaches {@code to}. A {@code back} edge with no
     * input is the default way out: a close button, then Escape. {@code state} is the look of {@code via}'s button
     * the edge needs ({@link Way#whenLooks}), null for any; {@code when} the method answering whether it leads there
     * now ({@link Way#when}), null for always.
     */
    public record Edge(String from, String to, Input via, boolean back, State state, BooleanSupplier when) {

        public Edge(String from, String to, Input via, boolean back) {
            this(from, to, via, back, null, null);
        }
    }

    /**
     * Where an input leads while its button looks as {@code state} says and {@code when} answers true; {@code state}
     * null for any look, {@code when} null for always.
     */
    public record Lead(State state, BooleanSupplier when, String to) {}

    private final Map<String, GameScreen> screens;

    private ScreenGraph(Map<String, GameScreen> screens) {
        this.screens = Collections.unmodifiableMap(new LinkedHashMap<>(screens));
    }

    /** The graph of {@code screens}, constant name to screen, in the order the bot declares them. */
    public static ScreenGraph of(Map<String, GameScreen> screens) {
        return new ScreenGraph(screens == null ? Map.of() : screens);
    }

    public Map<String, GameScreen> screens() {
        return screens;
    }

    /** The screen called {@code constant}. */
    public Optional<GameScreen> screen(String constant) {
        return Optional.ofNullable(screens.get(constant));
    }

    /** The name the game shows for {@code constant}, or the constant when it has none. */
    public String shown(String constant) {
        GameScreen screen = screens.get(constant);
        return screen == null || screen.name() == null || screen.name().isBlank() ? constant : screen.name();
    }

    /**
     * The constant {@code screen} is: the one a reference names ({@code BotConstants.screen}), else the constant
     * holding this very screen, else one equal to it.
     */
    public Optional<String> constantOf(GameScreen screen) {
        if (screen == null) return Optional.empty();
        String named = BotConstants.screenName(screen);
        if (named != null) return screens.containsKey(named) ? Optional.of(named) : Optional.empty();
        for (Map.Entry<String, GameScreen> e : screens.entrySet()) if (e.getValue() == screen) return Optional.of(e.getKey());
        for (Map.Entry<String, GameScreen> e : screens.entrySet()) if (e.getValue().equals(screen)) return Optional.of(e.getKey());
        return Optional.empty();
    }

    /** The screen {@code constant} was made known from, by its {@code from}. */
    public Optional<String> parent(String constant) {
        return screen(constant).map(GameScreen::from).flatMap(way -> constantOf(way.from()));
    }

    /** Every screen a way into {@code constant} is on: its {@code from}'s, then each {@code alsoFrom}'s. */
    public Set<String> opensFrom(String constant) {
        Set<String> from = new LinkedHashSet<>();
        screen(constant).ifPresent(screen -> ways(screen).forEach(way -> constantOf(way.from()).ifPresent(from::add)));
        return from;
    }

    /**
     * The input {@code way} does, as the screen it is on holds it now. A way keeps its own copy of the input, written
     * when it was found, and a look, a lock or a condition put on the screen's input later is not on that copy: the
     * screen's input is the one that says (2026-10-15). The copy stands for one the screen no longer holds. The
     * screen's own inputs are looked through before its looks', so a look's input of the same picture never wins.
     */
    public Input via(Way way) {
        if (way.via() == null) return null;
        return constantOf(way.from()).stream().flatMap(from -> inputs(from).stream()).filter(way.via()::sameAs)
                .findFirst().orElse(way.via());
    }

    /**
     * {@code input} as a way names it: what is done and on what, nothing else. Its kind, result, looks and conditions
     * are the screen's input's to say ({@link #via}).
     */
    public static Input bare(Input input) {
        if (input == null) return null;
        return switch (input.act()) {
            case CLICK -> Input.click(input.picture());
            case KEY -> Input.key(input.key());
            case SCROLL -> Input.scroll(input.picture(), input.notches());
        };
    }

    /**
     * The screens {@code constant}'s ways in name, and theirs in turn: each must be declared above it, since Java reads
     * a constant only once it is declared.
     */
    public Set<String> dependsOn(String constant) {
        Set<String> found = new LinkedHashSet<>();
        Deque<String> next = new ArrayDeque<>(List.of(constant));
        while (!next.isEmpty() && found.size() < DEEPEST * DEEPEST) {
            GameScreen screen = screens.get(next.poll());
            if (screen == null) continue;
            for (Way way : ways(screen)) {
                constantOf(way.from()).filter(found::add).ifPresent(next::add);
            }
        }
        return found;
    }

    /** The screens made known from {@code constant}, in declared order. */
    public List<String> children(String constant) {
        return screens.keySet().stream().filter(c -> !c.equals(constant))
                .filter(c -> parent(c).filter(constant::equals).isPresent()).toList();
    }

    /** The screen {@code input} on {@code constant} opens, by any screen's {@code from} or {@code alsoFrom}. */
    public Optional<String> opens(String constant, Input input) {
        for (String c : screens.keySet()) {
            for (Way way : ways(screens.get(c))) {
                if (way.via() != null && way.via().sameAs(input)
                        && constantOf(way.from()).filter(constant::equals).isPresent()) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Every screen {@code input} on {@code constant} opens, each with the look of its button the way needs, in
     * declared order: one for most inputs, one per look for a button that leads where its look says.
     */
    public List<Lead> leads(String constant, Input input) {
        List<Lead> leads = new ArrayList<>();
        for (String c : screens.keySet()) {
            for (Way way : ways(screens.get(c))) {
                if (way.via() != null && way.via().sameAs(input)
                        && constantOf(way.from()).filter(constant::equals).isPresent()) {
                    leads.add(new Lead(way.state(), way.when(), c));
                }
            }
        }
        return leads;
    }

    /** Whether {@code input} on {@code constant} was tried: settled, or it opened a screen. */
    public boolean tried(String constant, Input input) {
        return input.settled() || opens(constant, input).isPresent();
    }

    /** {@code constant}'s inputs still to try, the ones that come and go aside. */
    public List<Input> untried(String constant) {
        return inputs(constant).stream().filter(i -> !i.comesAndGoes() && !tried(constant, i)).toList();
    }

    /** {@code constant}'s inputs that come and go and were never seen to try. */
    public List<Input> unseen(String constant) {
        return inputs(constant).stream().filter(i -> i.comesAndGoes() && !tried(constant, i)).toList();
    }

    /** Whether anything on {@code constant} was tried. */
    public boolean triedAny(String constant) {
        return inputs(constant).stream().anyMatch(i -> tried(constant, i));
    }

    /**
     * Whether {@code constant} still has inputs to try, or none cut yet: every screen has one, its way out, which
     * counts once it is the screen's {@code back}.
     */
    public boolean unfinished(String constant) {
        return noneCut(constant) || !untried(constant).isEmpty();
    }

    private boolean noneCut(String constant) {
        GameScreen screen = screens.get(constant);
        return inputs(constant).isEmpty() && (screen == null || screen.back() == null);
    }

    /**
     * The first screen, popups included, with no marker that does not say why it has none ({@code marker — every
     * icon here animates} among its {@link GameScreen#owes}): one with a reason holds no other screen back.
     */
    public Optional<String> owingMarker() {
        return screens.entrySet().stream().filter(e -> e.getValue().marker() == null && !markerExplained(e.getValue()))
                .map(Map.Entry::getKey).findFirst();
    }

    /** Whether a thing owed is the marker, with why: {@code marker — every icon here animates}. */
    public static boolean markerWithReason(String owed) {
        return MARKER_REASON.matcher(owed).matches();
    }

    private static final Pattern MARKER_REASON = Pattern.compile("(?i)marker[ \\t]+[—–-][ \\t]+\\S.*");

    private static boolean markerExplained(GameScreen screen) {
        return screen.owes().stream().anyMatch(ScreenGraph::markerWithReason);
    }

    /** Whether {@code constant}, or a screen below it, still has an input to try. */
    public boolean workLeft(String constant) {
        return unfinished(constant) || unfinishedBelow(constant).isPresent();
    }

    /**
     * Where Discovery goes once {@code screen} is finished: up its parents to the first with work left on or below it,
     * else the top one. Empty while it has work left, and for a popup or the top screen.
     */
    public Optional<String> upTo(String screen) {
        if (workLeft(screen) || screen(screen).filter(GameScreen::isPopup).isPresent()) return Optional.empty();
        Set<String> seen = new HashSet<>(Set.of(screen));
        Optional<String> up = parent(screen);
        while (up.isPresent() && !workLeft(up.get()) && parent(up.get()).isPresent()) {
            if (!seen.add(up.get())) return Optional.empty();    // a map whose ways in loop has no top to go to
            up = parent(up.get());
        }
        return up.filter(c -> !c.equals(screen));
    }

    /**
     * The nearest screen made known from {@code constant}, directly or deeper, still unfinished: a child before its
     * own children. {@code constant}'s next input waits for it.
     */
    public Optional<String> unfinishedBelow(String constant) {
        return unfinishedBelow(constant, new HashSet<>());
    }

    private Optional<String> unfinishedBelow(String constant, Set<String> seen) {
        if (!seen.add(constant) || seen.size() > DEEPEST * DEEPEST) return Optional.empty();
        for (String child : children(constant)) {
            if (seen.contains(child)) continue;
            if (unfinished(child)) return Optional.of(child);
            Optional<String> deeper = unfinishedBelow(child, seen);
            if (deeper.isPresent()) return deeper;
        }
        return Optional.empty();
    }

    /**
     * A screen begun (something on it tried) whose branch is not finished, off {@code constant}'s own path to the
     * first screen: it is finished first, so one branch is explored to its end before the next is begun. A popup is
     * on no branch: it shows over any screen, and its inputs are tried whenever it does.
     */
    public Optional<String> branchBegun(String constant) {
        if (screen(constant).filter(GameScreen::isPopup).isPresent()) return Optional.empty();
        Set<String> above = new HashSet<>();
        String at = constant;
        while (at != null && above.add(at) && above.size() < DEEPEST) at = parent(at).orElse(null);
        return screens.keySet().stream()
                .filter(c -> !above.contains(c) && depth(c).isPresent() && triedAny(c))
                .filter(c -> !branchDone(c, new HashSet<>()))
                .findFirst();
    }

    private boolean branchDone(String constant, Set<String> seen) {
        if (!seen.add(constant) || seen.size() > DEEPEST * DEEPEST) return true;
        return !unfinished(constant) && children(constant).stream().allMatch(c -> branchDone(c, seen));
    }

    /**
     * How many {@code from} steps lead from the first screen to {@code constant}: 0 for a screen made known from
     * none that is no popup; empty for a popup, or a chain that loops.
     */
    public OptionalInt depth(String constant) {
        Set<String> seen = new HashSet<>();
        String at = constant;
        for (int depth = 0; at != null && depth < DEEPEST && seen.add(at); depth++) {
            GameScreen screen = screens.get(at);
            if (screen == null || screen.isPopup()) return OptionalInt.empty();
            if (screen.from() == null) return OptionalInt.of(depth);
            at = parent(at).orElse(null);
        }
        return OptionalInt.empty();
    }

    /** A finished screen's shape: a menu when every input tried, never-done and scrolled ones aside, opened one. */
    public Optional<Shape> shape(String constant) {
        boolean opened = false;
        for (Input input : inputs(constant)) {
            if (input.never() != null || input.lock() != null || input.act() == Input.Act.SCROLL
                    || input.did() == Did.LEAVES || !tried(constant, input)) {
                continue;
            }
            if (opens(constant, input).isEmpty()) return Optional.of(Shape.ACTIVITY);
            opened = true;
        }
        return opened ? Optional.of(Shape.MENU) : Optional.empty();
    }

    /**
     * Whether {@code constant} can be reached now: the best of its ways in, each as bad as its input (one that
     * comes and goes, one locked) and the screen it is on. The first screen and a popup are always there.
     */
    public Presence presence(String constant) {
        return presences().getOrDefault(constant, Presence.LOCKED);
    }

    /**
     * Every screen's presence, from the first screens and popups outward: each starts LOCKED and only gets better
     * through a way in, until nothing changes, so a loop of ways adds nothing and ends.
     */
    private Map<String, Presence> presences() {
        Map<String, Presence> known = new HashMap<>();
        screens.forEach((c, screen) ->
                known.put(c, screen.isPopup() || ways(screen).isEmpty() ? Presence.ALWAYS : Presence.LOCKED));
        for (boolean changed = true; changed; ) {
            changed = false;
            for (Map.Entry<String, GameScreen> e : screens.entrySet()) {
                if (e.getValue().isPopup()) continue;
                for (Way way : ways(e.getValue())) {
                    Optional<String> from = constantOf(way.from());
                    if (from.isEmpty()) continue;
                    Presence through = worst(of(via(way)), known.get(from.get()));
                    if (through.ordinal() < known.get(e.getKey()).ordinal()) {
                        known.put(e.getKey(), through);
                        changed = true;
                    }
                }
            }
        }
        return known;
    }

    private static Presence of(Input input) {
        if (input == null) return Presence.ALWAYS;
        return input.lock() != null ? Presence.LOCKED : input.comesAndGoes() ? Presence.SOMETIMES : Presence.ALWAYS;
    }

    private static Presence worst(Presence a, Presence b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** Every edge: each way in, then each way back to the screen a screen was made known from. */
    public List<Edge> edges() {
        List<Edge> edges = new ArrayList<>();
        screens.forEach((c, screen) -> {
            for (Way way : ways(screen)) {
                if (way.via() == null) continue;    // a way in names what is done; only a way back has a default
                Input via = via(way);
                constantOf(way.from()).ifPresent(from -> edges.add(new Edge(from, c, via, false, way.state(),
                        way.when())));
            }
            if (!screen.isPopup()) parent(c).ifPresent(p -> edges.add(new Edge(c, p, screen.back(), true)));
        });
        return edges;
    }

    /** The fewest edges {@code usable} allows from {@code from} to {@code to}; empty when none leads there. */
    public Optional<List<Edge>> pathTo(String from, String to, Predicate<Edge> usable) {
        if (!screens.containsKey(from) || !screens.containsKey(to)) return Optional.empty();
        if (from.equals(to)) return Optional.of(List.of());
        List<Edge> edges = edges().stream().filter(usable).toList();
        Map<String, Edge> reachedBy = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>(List.of(from));
        Set<String> seen = new HashSet<>(Set.of(from));
        while (!queue.isEmpty()) {
            String at = queue.poll();
            for (Edge edge : edges) {
                if (!edge.from().equals(at) || !seen.add(edge.to())) continue;
                reachedBy.put(edge.to(), edge);
                if (edge.to().equals(to)) {
                    List<Edge> path = new ArrayList<>();
                    for (String step = to; !step.equals(from); step = reachedBy.get(step).from()) {
                        path.addFirst(reachedBy.get(step));
                    }
                    return Optional.of(path);
                }
                queue.add(edge.to());
            }
        }
        return Optional.empty();
    }

    /**
     * What Discovery still owes, one line each as {@code Campaign: untried click campaign_claim}: a marker, the
     * inputs left to try, a screen with none cut, the ones that come and go never seen, and what each screen says it
     * {@link GameScreen#owes}. A marker the screen says why it has none ({@code marker — <why>}) is owed no more:
     * Discovery can end without it, and a walk takes that screen as reached once its way in is done.
     */
    public List<String> owed() {
        List<String> owed = new ArrayList<>();
        for (String c : screens.keySet()) {
            String name = shown(c);
            if (screens.get(c).marker() == null && !markerExplained(screens.get(c))) owed.add(name + ": marker");
            List<Input> untried = untried(c);
            if (!untried.isEmpty()) owed.add(name + ": untried " + labels(untried));
            else if (noneCut(c) && !screens.get(c).isPopup()) owed.add(name + ": no input cut yet");
            List<Input> unseen = unseen(c);
            if (!unseen.isEmpty()) owed.add(name + ": never seen to try " + labels(unseen) + " (comes and goes)");
            for (String what : screens.get(c).owes()) if (!markerWithReason(what)) owed.add(name + ": " + what);
        }
        return owed;
    }

    /**
     * The pictures {@code constant} names, each once by its id, in the order they are met: its marker, each input's
     * picture, variants and lock, each look's marker and inputs, its way back's. Manage Pictures groups the bot's
     * pictures by them.
     */
    public List<String> pictures(String constant) {
        GameScreen screen = screens.get(constant);
        if (screen == null) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        if (screen.marker() != null) ids.add(screen.marker().id());
        for (ScreenVariant look : screen.variants()) {
            if (look.marker() != null) ids.add(look.marker().id());
        }
        List<Input> named = new ArrayList<>(screen.allInputs());
        if (screen.back() != null) named.add(screen.back());
        for (Input input : named) {
            for (Variant look : input.looks()) ids.add(look.picture().id());
            if (input.lock() != null) ids.add(input.lock().id());
        }
        return List.copyOf(ids);
    }

    /**
     * {@code screen} with the picture {@code id} taken out: its marker or a look's, an input clicking it, a button's
     * look, a lock, its way back. Its ways in are kept: a screen is not cut off by a picture going.
     */
    public static GameScreen without(GameScreen screen, String id) {
        GameScreen now = screen;
        if (is(now.marker(), id)) now = now.marker(null);
        if (now.back() != null) {
            List<Input> back = without(List.of(now.back()), id);
            now = now.back(back.isEmpty() ? null : back.getFirst());
        }
        now = now.inputs(without(now.inputs(), id));
        List<ScreenVariant> looks = new ArrayList<>();
        for (ScreenVariant look : now.variants()) {
            looks.add((is(look.marker(), id) ? look.marker(null) : look).inputs(without(look.inputs(), id)));
        }
        return now.variants(looks);
    }

    /** Whether a way into {@code screen} still clicks the picture {@code id}, by any look or lock: what keeps it. */
    public static boolean enteredBy(GameScreen screen, String id) {
        List<Way> ways = new ArrayList<>(screen.alsoFrom());
        if (screen.from() != null) ways.add(screen.from());
        return ways.stream().map(Way::via).filter(Objects::nonNull).anyMatch(via -> is(via.lock(), id)
                || via.looks().stream().anyMatch(v -> is(v.picture(), id)));
    }

    private static List<Input> without(List<Input> inputs, String id) {
        List<Input> kept = new ArrayList<>();
        for (Input input : inputs) {
            if (is(input.picture(), id)) continue;
            Input now = input.variants(input.variants().stream().filter(v -> !is(v.picture(), id)).toList());
            kept.add(is(now.lock(), id) ? now.locked(null) : now);
        }
        return kept;
    }

    private static boolean is(ImageTemplate picture, String id) {
        return picture != null && picture.id().equals(id);
    }

    /** How {@code inputs} read in a sentence: {@code click gold, scroll list -3}. */
    public static String labels(List<Input> inputs) {
        return String.join(", ", inputs.stream().map(Input::label).toList());
    }

    private List<Input> inputs(String constant) {
        GameScreen screen = screens.get(constant);
        return screen == null ? List.of() : screen.allInputs();
    }

    private static List<Way> ways(GameScreen screen) {
        List<Way> ways = new ArrayList<>();
        if (screen.from() != null) ways.add(screen.from());
        ways.addAll(screen.alsoFrom());
        return ways;
    }
}
