package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.BotConstants.InputName;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The bot's {@code Screens} read as a graph, keyed by constant name ({@code HOME}): each screen's {@code from} is
 * the tree Discovery walks, its {@code alsoFrom} and {@code back} the other edges a run may take. An input is named by
 * its screen and its field ({@code HOME}, {@code market}). Pure: it reads values and answers, and writes nothing.
 *
 * <p>Discovery's order is breadth first (the user's call, 2026-10-16, in place of finishing each opened screen first):
 * a screen an input opens gets its marker and its inputs cut at once, and is tried once the screens above it are
 * finished. {@link #nextToExplore} is the shallowest screen with work left, information last; {@link #untried} lists
 * a screen's inputs with its information ones last.
 *
 * <p>What is not always there is read off the edges, never flagged on a screen ({@link #presence}): a screen every
 * way into which passes an input that {@link Input#comesAndGoes comes and goes} shows only at times, and one behind a
 * {@link Input#lock locked} input is locked.
 */
public final class ScreenGraph {

    /** How long a walk up or down may be: a map deeper than this has a loop its names hide. */
    private static final int DEEPEST = 64;

    /** Whether a screen can be reached now, read off the ways into it, worst last. */
    public enum Presence { ALWAYS, SOMETIMES, LOCKED }

    /** What a finished screen is: every input opened a screen, or not. */
    public enum Shape { MENU, ACTIVITY }

    /**
     * One edge a walk can take: {@code via}, the field {@code input} of {@code from}, reaches {@code to}. A {@code
     * back} edge with no input is the default way out: a close button, then Escape; one whose input is no field of
     * the screen has no {@code input}. {@code pick} is the copy of a repeated button it goes through, null for the
     * first; {@code state} the look of {@code via}'s button the edge needs ({@link Way#whenLooks}), null for any;
     * {@code when} the method answering whether it leads there now ({@link Way#when}), null for always. A way in with
     * no input is {@link #onItsOwn}: {@code to} follows {@code from} by itself, after a load ({@link Way#onItsOwn}).
     */
    public record Edge(String from, String to, String input, Input via, boolean back, Pick pick, State state,
                       BooleanSupplier when) {

        public Edge(String from, String to, String input, Input via, boolean back) {
            this(from, to, input, via, back, null, null, null);
        }

        /** Whether it is taken by waiting: {@code to} follows {@code from} by itself, nothing pressed. */
        public boolean onItsOwn() {
            return !back && input == null && via == null;
        }

        /** How it reads in a sentence: its field, else what its input acts on, else the way back. */
        public String label() {
            if (input != null) return pick == null ? input : input + " " + pick.label();
            if (onItsOwn()) return "on its own";
            return via == null ? "the way back" : via.label();
        }
    }

    /**
     * Where an input leads through {@code pick}'s copy, while its button looks as {@code state} says and {@code when}
     * answers true; each null for any.
     */
    public record Lead(Pick pick, State state, BooleanSupplier when, String to) {}

    private final Map<String, MapScreen> screens;
    /** The bot's own screens by the constant holding each, at run time; empty for a map read by Studio's tools. */
    private final Map<Screen, String> instances;

    private ScreenGraph(Map<String, MapScreen> screens, Map<Screen, String> instances) {
        this.screens = Collections.unmodifiableMap(new LinkedHashMap<>(screens));
        this.instances = instances;
    }

    /** The graph of {@code screens}, constant name to screen, in the order the bot declares them. */
    public static ScreenGraph of(Map<String, MapScreen> screens) {
        return new ScreenGraph(screens == null ? Map.of() : screens, Map.of());
    }

    /** The graph of the bot's own {@code screens}, constant name to screen, in the order the bot declares them. */
    public static ScreenGraph ofScreens(Map<String, Screen> screens) {
        Map<String, MapScreen> read = new LinkedHashMap<>();
        Map<Screen, String> instances = new IdentityHashMap<>();
        screens.forEach((constant, screen) -> {
            read.put(constant, MapScreen.of(screen));
            instances.put(screen, constant);
        });
        return new ScreenGraph(read, Collections.unmodifiableMap(instances));
    }

    public Map<String, MapScreen> screens() {
        return screens;
    }

    /** The screen called {@code constant}. */
    public Optional<MapScreen> screen(String constant) {
        return Optional.ofNullable(constant == null ? null : screens.get(constant));
    }

    /** The input {@code field} of {@code constant}, as its strip leaves it ({@link MapScreen#effective}). */
    public Optional<Input> input(String constant, String field) {
        return screen(constant).flatMap(s -> s.effective(field));
    }

    /**
     * The map's root: its {@link #start} screen, else the first screen declared that is reached from none and is no
     * popup — the game's home, which a run with no home method of its own walks to (2026-10-16). Empty for a map with
     * none.
     */
    public Optional<String> root() {
        return start().or(() -> screens.entrySet().stream()
                .filter(e -> e.getValue().from() == null && !e.getValue().isPopup())
                .map(Map.Entry::getKey).findFirst());
    }

    /**
     * The game's home, which a run with no home method of its own walks to: the {@link #root}, or, past a start
     * screen, the screen its loading ends on (Home after Loading and Splash). Nothing leads back to a start screen.
     */
    public Optional<String> home() {
        Optional<String> start = start();
        if (start.isEmpty()) return root();
        String at = start.get();
        Set<String> seen = new HashSet<>(Set.of(at));
        for (Optional<String> next = after(at); next.isPresent() && seen.add(next.get()); next = after(at)) {
            at = next.get();
        }
        return Optional.of(at);
    }

    /** The first screen made known by following {@code constant} on its own, its tree's way in. */
    private Optional<String> after(String constant) {
        return screens.keySet().stream().filter(c -> followsOnItsOwn(c) && parent(c).filter(constant::equals)
                .isPresent()).findFirst();
    }

    /** The screen the game opens on, its loading screen ({@link Setup#start}): the first declared so. */
    public Optional<String> start() {
        return screens.entrySet().stream().filter(e -> e.getValue().isStart() && !e.getValue().isPopup())
                .map(Map.Entry::getKey).findFirst();
    }

    /** The screen {@code way} is on: the one its input is a field of, or the one it follows on its own. */
    public Optional<String> onScreen(Way way) {
        if (way == null) return Optional.empty();
        if (way.isOnItsOwn()) return constantOf(way.after());
        return nameOf(way.via()).map(InputName::screen);
    }

    /** The name the game shows for {@code constant}, or the constant when it has none. */
    public String shown(String constant) {
        MapScreen screen = screens.get(constant);
        return screen == null || screen.name() == null || screen.name().isBlank() ? constant : screen.name();
    }

    /**
     * The constant {@code screen} is: the one a reference names ({@code BotConstants.screen}), else the constant
     * holding this very screen.
     */
    public Optional<String> constantOf(Screen screen) {
        if (screen == null) return Optional.empty();
        String named = BotConstants.screenName(screen);
        if (named != null) return screens.containsKey(named) ? Optional.of(named) : Optional.empty();
        return Optional.ofNullable(instances.get(screen));
    }

    /** The bot's own screen held by {@code constant}, at run time; empty for a map read by Studio's tools. */
    public Optional<Screen> instance(String constant) {
        return instances.entrySet().stream().filter(e -> e.getValue().equals(constant)).map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * The screen and the field {@code input} is: the one a reference names ({@code BotConstants.input}), else the
     * field holding this very input. Empty for an input no screen of the map holds.
     */
    public Optional<InputName> nameOf(Input input) {
        if (input == null) return Optional.empty();
        InputName named = BotConstants.inputName(input);
        if (named != null) {
            return screen(named.screen()).flatMap(s -> s.input(named.field())).isPresent() ? Optional.of(named)
                    : Optional.empty();
        }
        for (Map.Entry<String, MapScreen> e : screens.entrySet()) {
            for (Map.Entry<String, Input> field : e.getValue().inputs().entrySet()) {
                if (field.getValue() == input) return Optional.of(new InputName(e.getKey(), field.getKey()));
            }
        }
        return Optional.empty();
    }

    /** The screen and the field holding {@code readout}, this very value; empty when no screen of the map holds it. */
    public Optional<InputName> nameOf(Readout readout) {
        if (readout == null) return Optional.empty();
        for (Map.Entry<String, MapScreen> e : screens.entrySet()) {
            for (Map.Entry<String, Readout> field : e.getValue().readouts().entrySet()) {
                if (field.getValue() == readout) return Optional.of(new InputName(e.getKey(), field.getKey()));
            }
        }
        return Optional.empty();
    }

    /**
     * The screens that follow {@code constant} on its own, one step on, in the order declared: how a fight ends,
     * Victory or Defeat each {@code .after(FIGHT)}. {@link #followers} goes on past them.
     */
    public List<String> results(String constant) {
        List<String> results = new ArrayList<>();
        screens.forEach((c, screen) -> {
            boolean follows = screen.ways().stream()
                    .anyMatch(way -> way.isOnItsOwn() && onScreen(way).filter(constant::equals).isPresent());
            if (follows && !c.equals(constant)) results.add(c);
        });
        return List.copyOf(results);
    }

    /**
     * The screen {@code constant} was made known from: the screen its {@code from} is an input of, or the one it
     * follows on its own.
     */
    public Optional<String> parent(String constant) {
        return screen(constant).map(MapScreen::from).flatMap(this::onScreen);
    }

    /** The screen {@code constant} is a panel of ({@link Setup#panelOf}); empty for a screen of its own. */
    public Optional<String> panelOf(String constant) {
        return screen(constant).map(s -> s.setup().panelOf()).flatMap(this::constantOf)
                .filter(c -> !c.equals(constant));
    }

    /** Every screen a way into {@code constant} is on: its {@code from}'s, then each {@code alsoFrom}'s. */
    public Set<String> opensFrom(String constant) {
        Set<String> from = new LinkedHashSet<>();
        screen(constant).ifPresent(screen -> screen.ways().forEach(way -> onScreen(way).ifPresent(from::add)));
        return from;
    }

    /** Whether {@code constant} follows the screen it was made known from on its own: Home after the loading screen. */
    public boolean followsOnItsOwn(String constant) {
        return screen(constant).map(MapScreen::from).filter(Way::isOnItsOwn).isPresent();
    }

    /**
     * How long the screen after {@code constant} may take to show when it follows on its own: the start screen's load,
     * or a transition's ({@link Setup#loadTime}).
     */
    public long loadMs(String constant) {
        return screen(constant).map(s -> s.setup().loadTime()).orElse(Setup.LOADS).toMillis();
    }

    /**
     * The screens that follow {@code constant} on its own, and those that follow them in turn, nearest first: what the
     * game shows once a transition is done, Battle after Entering battle (the user's call, 2026-10-18).
     */
    public List<String> followers(String constant) {
        Set<String> found = new LinkedHashSet<>();
        Deque<String> next = new ArrayDeque<>(List.of(constant));
        while (!next.isEmpty() && found.size() < DEEPEST) {
            String at = next.poll();
            screens.forEach((c, screen) -> {
                boolean follows = screen.ways().stream()
                        .anyMatch(way -> way.isOnItsOwn() && onScreen(way).filter(at::equals).isPresent());
                if (follows && !c.equals(constant) && found.add(c)) next.add(c);
            });
        }
        return List.copyOf(found);
    }

    /** The screens {@code constant}'s ways in are on, and theirs in turn: every screen it is reached through. */
    public Set<String> dependsOn(String constant) {
        Set<String> found = new LinkedHashSet<>();
        Deque<String> next = new ArrayDeque<>(List.of(constant));
        while (!next.isEmpty() && found.size() < DEEPEST * DEEPEST) {
            for (String from : opensFrom(next.poll())) if (found.add(from)) next.add(from);
        }
        return found;
    }

    /** The screens above {@code constant} in the tree: its parent, the parent's, and so on to the first screen. */
    public Set<String> above(String constant) {
        Set<String> up = new LinkedHashSet<>();
        for (Optional<String> at = parent(constant); at.isPresent() && up.add(at.get()) && up.size() < DEEPEST; ) {
            at = parent(at.get());
        }
        return up;
    }

    /** The screens made known from {@code constant}, in declared order. */
    public List<String> children(String constant) {
        return screens.keySet().stream().filter(c -> !c.equals(constant))
                .filter(c -> parent(c).filter(constant::equals).isPresent()).toList();
    }

    /** The screen the field {@code field} of {@code constant} opens, by any screen's {@code from} or {@code alsoFrom}. */
    public Optional<String> opens(String constant, String field) {
        return leads(constant, field).stream().map(Lead::to).findFirst();
    }

    /**
     * Every screen the field {@code field} of {@code constant} opens, each with the copy and the look of its button
     * the way needs, in declared order: one for most inputs, one per copy or per look for a button that leads where
     * its copy or its look says.
     */
    public List<Lead> leads(String constant, String field) {
        InputName wanted = new InputName(constant, field);
        List<Lead> leads = new ArrayList<>();
        for (String c : screens.keySet()) {
            for (Way way : screens.get(c).ways()) {
                if (nameOf(way.via()).filter(wanted::equals).isPresent()) {
                    leads.add(new Lead(way.pick(), way.state(), way.when(), c));
                }
            }
        }
        return leads;
    }

    /** Whether the field {@code field} of {@code constant} was tried: settled, its way back, or it opened a screen. */
    public boolean tried(String constant, String field) {
        return input(constant, field).filter(Input::settled).isPresent() || isBack(constant, field)
                || opens(constant, field).isPresent();
    }

    /** Whether the field {@code field} of {@code constant} is its way back. */
    public boolean isBack(String constant, String field) {
        return screen(constant).map(MapScreen::back).flatMap(this::nameOf)
                .filter(n -> n.screen().equals(constant) && n.field().equals(field)).isPresent();
    }

    /**
     * {@code constant}'s fields still to try, those that come and go aside, in the order Discovery tries them: the
     * screen's own work first, its information ({@link #isInfo}) last.
     */
    public List<String> untried(String constant) {
        return fields(constant).stream().filter(f -> !comesAndGoes(constant, f) && !tried(constant, f))
                .sorted(Comparator.comparing(f -> isInfo(f, input(constant, f).orElse(null)))).toList();
    }

    /**
     * Whether the input {@code input}, the field {@code field}, only tells: {@link Input#isInfo}, or a picture or
     * a field called help, info, rules, faq, how to play, a leaderboard or the rankings ({@code help_button},
     * {@code info_i}, {@code i}). Discovery tries such an input last on its screen, and explores the screen it opens
     * after every other (the user's call, 2026-10-16). A rank-up or a tier's button is no information: {@code rank}
     * alone and an {@code i} inside a name are not.
     */
    public static boolean isInfo(String field, Input input) {
        if (input != null && input.isInfo()) return true;
        if (input instanceof Button button && button.picture() != null && INFO.matcher(button.picture().id()).find()) {
            return true;
        }
        return field != null && INFO.matcher(snake(field)).find();
    }

    private static final Pattern INFO = Pattern.compile(
            "(?i)^i$|(^|_)(help|info|rules|faq|how_to_play|leaderboards?|rankings)(_|$)");

    /** {@code infoLuck} as {@code info_luck}: a field's words as a picture's id spells them. */
    private static String snake(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
    }

    /** {@code constant}'s fields that come and go and were never seen to try. */
    public List<String> unseen(String constant) {
        return fields(constant).stream().filter(f -> comesAndGoes(constant, f) && !tried(constant, f)).toList();
    }

    /** Whether anything on {@code constant} was tried. */
    public boolean triedAny(String constant) {
        return fields(constant).stream().anyMatch(f -> tried(constant, f));
    }

    /**
     * Whether {@code constant} still has inputs to try, or none cut yet: every screen has one, its way out, which
     * counts once it is the screen's {@code back}.
     */
    public boolean unfinished(String constant) {
        return noneCut(constant) || !untried(constant).isEmpty();
    }

    /**
     * Whether {@code constant} has nothing cut on it: no input and no way back. A popup is never so: the game opens
     * it, and its default way back (a close button, then Escape) is its way out. Nor is a start screen: the screen
     * after it follows on its own.
     */
    public boolean noneCut(String constant) {
        MapScreen screen = screens.get(constant);
        return fields(constant).isEmpty()
                && (screen == null || screen.back() == null && !screen.isPopup() && !screen.isStart());
    }

    /**
     * The first screen, popups included, with no marker that does not say why it has none ({@code marker — every
     * icon here animates} among its {@link Setup#owes}): one with a reason holds no other screen back.
     */
    public Optional<String> owingMarker() {
        return screens.entrySet().stream().filter(e -> !e.getValue().hasMarker() && !markerExplained(e.getValue()))
                .map(Map.Entry::getKey).findFirst();
    }

    /** Whether a thing owed is the marker, with why: {@code marker — every icon here animates}. */
    public static boolean markerWithReason(String owed) {
        return MARKER_REASON.matcher(owed).matches();
    }

    private static final Pattern MARKER_REASON = Pattern.compile("(?i)marker[ \\t]+[—–-][ \\t]+\\S.*");

    private static boolean markerExplained(MapScreen screen) {
        return screen.owes().stream().anyMatch(ScreenGraph::markerWithReason);
    }

    /**
     * The screen Discovery explores next: the shallowest one still unfinished, so a screen's own inputs are all
     * tried before any screen they opened, and main screens before the panels under them (the user's call,
     * 2026-10-16, breadth first). One that shows only at times ({@link #presence}: Engineer, whose Home button shows
     * only while tools wait) comes after every other, then one opened by an information input ({@link #isInfo}:
     * help, rules, a leaderboard); a locked one is none to go to, nor is a popup. Of two otherwise alike, the one
     * declared first, as the map was found. Empty when nothing is left.
     */
    public Optional<String> nextToExplore() {
        List<String> order = new ArrayList<>(screens.keySet());
        Map<String, Presence> presence = presences();
        return order.stream()
                .filter(c -> depth(c).isPresent() && presence.get(c) != Presence.LOCKED && unfinished(c))
                .min(Comparator.<String, Presence>comparing(presence::get)
                        .thenComparing(this::underInfo)
                        .thenComparingInt(c -> depth(c).orElse(DEEPEST))
                        .thenComparingInt(order::indexOf));
    }

    /** Whether a way on {@code constant}'s path from the first screen is an information input, or names help. */
    private boolean underInfo(String constant) {
        Set<String> seen = new HashSet<>();
        for (String at = constant; at != null && seen.add(at) && seen.size() < DEEPEST; at = parent(at).orElse(null)) {
            MapScreen screen = screens.get(at);
            if (screen == null || screen.from() == null) return false;
            Optional<InputName> via = nameOf(screen.fromInput());
            if (via.filter(v -> isInfo(v.field(), input(v.screen(), v.field()).orElse(null))).isPresent()
                    || INFO.matcher(at).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many {@code from} steps lead from the first screen to {@code constant}: 0 for a screen made known from
     * none that is no popup; empty for a popup, or a chain that loops.
     */
    public OptionalInt depth(String constant) {
        Set<String> seen = new HashSet<>();
        String at = constant;
        for (int depth = 0; at != null && depth < DEEPEST && seen.add(at); depth++) {
            MapScreen screen = screens.get(at);
            if (screen == null || screen.isPopup()) return OptionalInt.empty();
            if (screen.from() == null) return OptionalInt.of(depth);
            at = parent(at).orElse(null);
        }
        return OptionalInt.empty();
    }

    /** A finished screen's shape: a menu when every input tried, never-done, strips and its way back aside, opened one. */
    public Optional<Shape> shape(String constant) {
        boolean opened = false;
        for (String field : fields(constant)) {
            Input input = input(constant, field).orElseThrow();
            if (input.never() != null || input.lock() != null || input instanceof Strip || input instanceof Tree
                    || isBack(constant, field)
                    || input.isLeaving() || !tried(constant, field)) {
                continue;
            }
            if (opens(constant, field).isEmpty()) return Optional.of(Shape.ACTIVITY);
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
                known.put(c, screen.isPopup() || screen.ways().isEmpty() ? Presence.ALWAYS : Presence.LOCKED));
        for (boolean changed = true; changed; ) {
            changed = false;
            for (Map.Entry<String, MapScreen> e : screens.entrySet()) {
                if (e.getValue().isPopup()) continue;
                for (Way way : e.getValue().ways()) {
                    Optional<String> on = onScreen(way);
                    if (on.isEmpty()) continue;
                    Optional<InputName> via = nameOf(way.via());
                    // A way on its own is as good as the screen it follows.
                    Presence through = worst(of(via.flatMap(v -> input(v.screen(), v.field())).orElse(null)),
                            known.get(on.get()));
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

    /**
     * Every edge: each way in, then each way back to the screen a screen was made known from. A way on its own is an
     * edge with no input, and its way back skips what it followed: the game never shows a loading screen or a
     * transition again, so Battle after Entering battle goes back to Home, which opened the transition, and Home after
     * Loading has none.
     */
    public List<Edge> edges() {
        List<Edge> edges = new ArrayList<>();
        screens.forEach((c, screen) -> {
            for (Way way : screen.ways()) {
                if (way.isOnItsOwn()) {
                    onScreen(way).ifPresent(after -> edges.add(new Edge(after, c, null, null, false, null, null,
                            way.when())));
                    continue;
                }
                nameOf(way.via()).ifPresent(via -> edges.add(new Edge(via.screen(), c, via.field(),
                        input(via.screen(), via.field()).orElseThrow(), false, way.pick(), way.state(), way.when())));
            }
            if (!screen.isPopup()) backTo(c).ifPresent(p -> edges.add(back(c, p)));
        });
        return edges;
    }

    /** Where {@code constant}'s way back leads: its parent, past each screen it followed on its own. */
    private Optional<String> backTo(String constant) {
        Optional<String> up = parent(constant);
        Set<String> seen = new HashSet<>();
        for (String at = constant; followsOnItsOwn(at) && up.isPresent() && seen.add(at); up = parent(at)) {
            at = up.get();
        }
        return up;
    }

    /**
     * The way back from {@code constant} to {@code to}: its {@code back}, by its field when it is one of its own; no
     * input for the default, or for a reference to no field of the map.
     */
    private Edge back(String constant, String to) {
        Input back = screens.get(constant).back();
        Optional<InputName> name = nameOf(back);
        return name.map(n -> new Edge(constant, to, n.screen().equals(constant) ? n.field() : n.toString(),
                        input(n.screen(), n.field()).orElseThrow(), true))
                .orElseGet(() -> new Edge(constant, to, null, BotConstants.inputName(back) == null ? back : null,
                        true));
    }

    /** {@code constant}'s way out as a walk does it, leading nowhere in particular: a popup's close. */
    public Optional<Edge> backOf(String constant) {
        return screen(constant).map(s -> back(constant, null));
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
     * What Discovery still owes, one line each as {@code Campaign: untried claim}: a marker, the inputs left to try, a
     * screen with none cut, the ones that come and go never seen, and what each screen says it {@link Setup#owes}. A
     * marker the screen says why it has none ({@code marker — <why>}) is owed no more: Discovery can end without it,
     * and a walk takes that screen as reached once its way in is done.
     */
    public List<String> owed() {
        List<String> owed = new ArrayList<>();
        for (String c : screens.keySet()) {
            String name = shown(c);
            if (!screens.get(c).hasMarker() && !markerExplained(screens.get(c))) owed.add(name + ": marker");
            List<String> untried = untried(c);
            if (!untried.isEmpty()) owed.add(name + ": untried " + String.join(", ", untried));
            else if (noneCut(c)) owed.add(name + ": no input cut yet");
            List<String> unseen = unseen(c);
            if (!unseen.isEmpty()) {
                owed.add(name + ": never seen to try " + String.join(", ", unseen) + " (comes and goes)");
            }
            for (String what : screens.get(c).owes()) if (!markerWithReason(what)) owed.add(name + ": " + what);
        }
        return owed;
    }

    /**
     * The pictures {@code constant} names, each once by its id, in the order they are met: its markers and absent
     * pictures, each look's markers, each input's pictures (a button's looks and lock, a strip's members), its way
     * back's. Manage Pictures groups the bot's pictures by them.
     */
    public List<String> pictures(String constant) {
        MapScreen screen = screens.get(constant);
        if (screen == null) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        for (ImageTemplate p : Markers.of(screen).pictures()) ids.add(p.id());
        for (ScreenVariant look : screen.variants()) {
            for (ImageTemplate p : Markers.of(look).pictures()) ids.add(p.id());
        }
        List<Input> named = new ArrayList<>(screen.inputs().values());
        if (screen.back() != null && BotConstants.inputName(screen.back()) == null) named.add(screen.back());
        for (Input input : named) ids.addAll(pictureIds(input));
        return List.copyOf(ids);
    }

    /**
     * The ids of the pictures {@code input} acts on or is told by: a button's looks and the picture it is dragged
     * onto, a strip's members, a lock.
     */
    public static List<String> pictureIds(Input input) {
        List<String> ids = new ArrayList<>();
        if (input instanceof Button button) for (Look look : button.allLooks()) ids.add(look.picture().id());
        if (input instanceof Button button && button.isDrag() && button.drag().onto() != null) {
            ids.add(button.drag().onto().id());
        }
        if (input instanceof Strip strip) for (ImageTemplate member : strip.members()) ids.add(member.id());
        if (input instanceof Tree tree) for (ImageTemplate member : tree.members()) ids.add(member.id());
        if (input.lock() != null) ids.add(input.lock().id());
        return ids;
    }

    /**
     * {@code screen} with the picture {@code id} taken out: one of its markers or absent pictures or a look's, a button
     * clicking it (its field gone), a button's look or drop, a strip's member, a lock, its way back. Its ways in are
     * kept: a screen is not cut off by a picture going.
     */
    public static MapScreen without(MapScreen screen, String id) {
        // The fields that go with the picture: a look naming one names it no more.
        Set<String> gone = new HashSet<>();
        screen.inputs().forEach((field, input) -> {
            if (input instanceof Button button && is(button.picture(), id)) gone.add(field);
        });
        MapScreen now = screen.setup(s -> {
            Setup kept = s.markers(butPicture(s.markers(), id)).absent(butPicture(s.absent(), id));
            // A way back of the picture's, or one naming a field that goes with it: the default one, then.
            if (kept.back() instanceof Button back && (is(back.picture(), id) || named(back, gone))) {
                kept = kept.back(null);
            }
            List<ScreenVariant> looks = new ArrayList<>();
            for (ScreenVariant look : kept.variants()) {
                looks.add(look.markers(butPicture(look.markers(), id)).absent(butPicture(look.absent(), id))
                        .inputs(look.inputs().stream().filter(i -> !(i instanceof Button b && is(b.picture(), id))
                                && !named(i, gone)).toList()));
            }
            return kept.variants(looks);
        });
        for (Map.Entry<String, Input> e : screen.inputs().entrySet()) {
            Input input = e.getValue();
            if (gone.contains(e.getKey())) {
                now = now.withoutInput(e.getKey());
                continue;
            }
            Input kept = input;
            if (kept instanceof Button button) {
                kept = button.looks(button.looks().stream().filter(v -> !is(v.picture(), id)).toList());
                // A drag onto the picture going is pressed again.
                if (button.isDrag() && is(button.drag().onto(), id)) kept = ((Button) kept).drag(null);
            }
            if (kept instanceof Strip strip) kept = strip.members(butPicture(strip.members(), id));
            if (kept instanceof Tree tree) kept = tree.without(id);
            if (is(kept.lock(), id)) kept = kept.locked(null);
            if (kept instanceof Strip strip && strip.members().isEmpty()
                    || kept instanceof Tree tree && tree.nodes().isEmpty()) {
                now = now.withoutInput(e.getKey());
            }
            else if (!kept.equals(input)) now = now.withInput(e.getKey(), kept);
        }
        return now;
    }

    /** Whether {@code input} is a reference to one of {@code fields}. */
    private static boolean named(Input input, Set<String> fields) {
        InputName name = BotConstants.inputName(input);
        return name != null && fields.contains(name.field());
    }

    /** Whether a way into {@code constant} still clicks the picture {@code id}, by any look or lock: what keeps it. */
    public boolean enteredBy(String constant, String id) {
        return screen(constant).stream().flatMap(s -> s.ways().stream())
                .map(way -> nameOf(way.via()).flatMap(n -> input(n.screen(), n.field())).orElse(way.via()))
                .anyMatch(via -> via != null && pictureIds(via).contains(id));
    }

    /** {@code pictures} but the one whose id is {@code id}. */
    private static List<ImageTemplate> butPicture(List<ImageTemplate> pictures, String id) {
        return pictures.stream().filter(p -> !is(p, id)).toList();
    }

    private static boolean is(ImageTemplate picture, String id) {
        return picture != null && picture.id().equals(id);
    }

    /** {@code constant}'s fields, in the order its class declares them. */
    public List<String> fields(String constant) {
        MapScreen screen = screens.get(constant);
        return screen == null ? List.of() : List.copyOf(screen.inputs().keySet());
    }

    private boolean comesAndGoes(String constant, String field) {
        return input(constant, field).filter(Input::comesAndGoes).isPresent();
    }
}
