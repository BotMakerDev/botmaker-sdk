package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.BotConstants.InputName;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The map edited by hand from 🗺 Screens' side panel: what the assistant's tools record by trying, set by the user
 * instead (the user's call, 2026-10-15): an input's result, purpose, never, lock or coming and going, an input removed
 * or made the way back, a screen renamed, made a popup, taken back, deleted or given a new shot, pictures the bot has
 * put on a screen, a marker or a picture it never shows added or taken off, a screen added under another without
 * trying its way in, a way in or a way back taken off, and when a way, an input or a look is there, and where a card
 * sits on the map. An input is named by its field ({@code beerBuy}). The assistant's edit_map does each by the same
 * method ({@link MapEdits}). Each answers in a sentence what it wrote, or why it did not. On the FX thread, except
 * {@link #retakeShot}, which reads the game, and {@link #addMarker} and {@link #removeMarker}, which read every shot.
 */
public final class ScreenEdits {

    /** How an answer begins when nothing was written. */
    public static final String NOT_WRITTEN = "Not written: ";

    /** The longest a button or a hotkey is held, as 🗺's spinners offer: past it the game is held, not pressed. */
    public static final Duration LONGEST_HOLD = Duration.ofMinutes(1);

    private ScreenEdits() {}

    /** {@code input} as it was cut, untried: tried, leaving, never and lock cleared, the rest kept. */
    public static Input untried(Input input) {
        return switch (input) {
            case Button b -> new Button(b.picture(), false, false, b.isInfo(), null, b.comesAndGoes(), null,
                    b.looks(), b.when(), b.repeats(), b.cycles(), b.gesture(), b.hold());
            case Hotkey h -> new Hotkey(h.combo(), false, false, h.isInfo(), null, h.comesAndGoes(), null, h.when());
            case Strip s -> new Strip(s.scroll(), s.members(), false, false, s.isInfo(), null, s.comesAndGoes(), null,
                    s.when());
            case Tree t -> new Tree(t.nodes(), t.isDragged(), t.notches(), false, false, t.isInfo(), null,
                    t.comesAndGoes(), null, t.when());
        };
    }

    /** {@code input} shown only at times, or always ({@code sometimes} false: when it is there goes too). */
    public static Input sometimes(Input input, boolean sometimes) {
        if (sometimes) return input.sometimes();
        return switch (input) {
            case Button b -> new Button(b.picture(), b.isTried(), b.isLeaving(), b.isInfo(), b.never(), false,
                    b.lock(), b.looks(), null, b.repeats(), b.cycles(), b.gesture(), b.hold());
            case Hotkey h -> new Hotkey(h.combo(), h.isTried(), h.isLeaving(), h.isInfo(), h.never(), false, h.lock(),
                    null);
            case Strip s -> new Strip(s.scroll(), s.members(), s.isTried(), s.isLeaving(), s.isInfo(), s.never(),
                    false, s.lock(), null);
            case Tree t -> new Tree(t.nodes(), t.isDragged(), t.notches(), t.isTried(), t.isLeaving(), t.isInfo(),
                    t.never(), false, t.lock(), null);
        };
    }

    /** {@code input} only telling, or not ({@code info} false). */
    public static Input info(Input input, boolean info) {
        if (info) return input.info();
        return switch (input) {
            case Button b -> new Button(b.picture(), b.isTried(), b.isLeaving(), false, b.never(), b.comesAndGoes(),
                    b.lock(), b.looks(), b.when(), b.repeats(), b.cycles(), b.gesture(), b.hold());
            case Hotkey h -> new Hotkey(h.combo(), h.isTried(), h.isLeaving(), false, h.never(), h.comesAndGoes(),
                    h.lock(), h.when());
            case Strip s -> new Strip(s.scroll(), s.members(), s.isTried(), s.isLeaving(), false, s.never(),
                    s.comesAndGoes(), s.lock(), s.when());
            case Tree t -> new Tree(t.nodes(), t.isDragged(), t.notches(), t.isTried(), t.isLeaving(), false,
                    t.never(), t.comesAndGoes(), t.lock(), t.when());
        };
    }

    /** Puts {@code now} as {@code constant}'s field {@code field}; {@code now} null removes it. */
    public static String setInput(StudioServices services, String constant, String field, Input now) {
        return said(ScreenBook.update(services, constant,
                        s -> now == null ? s.withoutInput(field) : s.withInput(field, now)),
                now == null ? field + " is off " + constant + "." : "Noted: " + field + ".");
    }

    /**
     * Makes {@code strip} one of {@code constant}'s strips, as its field {@code field} (null: a new one named from its
     * first member), in place of every strip that holds one of its members; a strip of no members (or 0 notches) takes
     * those strips off, their buttons looked for at their places.
     */
    public static String setStrip(StudioServices services, String constant, String field, Strip strip) {
        boolean off = strip.members().isEmpty() || strip.scroll() == null || !strip.scroll().moves();
        String names = strip.members().stream().map(ImageTemplate::id).collect(Collectors.joining(", "));
        return said(ScreenBook.update(services, constant, s -> withStrip(s, field, strip, off)),
                off ? "The strip is off " + constant + ": its buttons are looked for at their places."
                        : constant + " has a strip of " + names + ", each found anywhere along it and scrolled to by "
                        + strip.scroll().label() + " over another that shows.");
    }

    /** {@code screen} without any strip holding one of {@code strip}'s members, and {@code strip} last unless {@code off}. */
    static MapScreen withStrip(MapScreen screen, String field, Strip strip, boolean off) {
        MapScreen now = screen;
        Strip had = null;
        String hadField = null;
        for (Map.Entry<String, Strip> held : screen.strips().entrySet()) {
            if (held.getKey().equals(field) || strip.members().stream().anyMatch(held.getValue()::has)) {
                if (had == null) {
                    had = held.getValue();
                    hadField = held.getKey();
                }
                now = now.withoutInput(held.getKey());
            }
        }
        // A button is in one strip or one tree.
        for (Map.Entry<String, Tree> held : screen.trees().entrySet()) {
            if (strip.members().stream().anyMatch(held.getValue()::has)) now = now.withoutInput(held.getKey());
        }
        if (off) return now;
        // The strip it replaces keeps its field and what it says of its members: only its members and scroll change,
        // and a scroll not measured that goes the same way keeps what Discovery measured of the old one.
        Strip made = had == null ? strip : had.members(strip.members()).scroll(kept(had.scroll(), strip.scroll()));
        String name = hadField != null ? hadField : field != null && !now.inputs().containsKey(field) ? field
                : now.fieldFor((strip.members().isEmpty() ? "" : strip.members().getFirst().id()) + " strip");
        return now.withInput(name, made);
    }

    /** {@code now}, or {@code had} when {@code now} is a scroll not measured yet that goes the way {@code had} goes. */
    private static Scroll kept(Scroll had, Scroll now) {
        boolean unmeasured = now.step() == 0 && now.settle().equals(Scroll.SETTLE) && now.most() == Scroll.MOST;
        boolean same = had != null && had.notches() == now.notches() && had.drag() == now.drag()
                && had.px() == now.px() && had.time().equals(now.time());
        return unmeasured && same ? had : now;
    }

    /**
     * Makes {@code tree} one of {@code constant}'s trees, as its field {@code field} (null: a new one named from its
     * first node), in place of every strip or tree that holds one of its nodes; a tree of no nodes takes those off,
     * their buttons looked for at their places.
     */
    public static String setTree(StudioServices services, String constant, String field, Tree tree) {
        String names = tree.members().stream().map(ImageTemplate::id).collect(Collectors.joining(", "));
        String scrolled = tree.isDragged() ? "dragged" : tree.notches() != 0 ? "scrolled by the wheel"
                : "fitting on the screen";
        return said(ScreenBook.update(services, constant, s -> withTree(s, field, tree)),
                tree.nodes().isEmpty() ? "The tree is off " + constant + ": its buttons are looked for at their places."
                        : constant + " has a tree of " + names + ", " + scrolled + ". Link its nodes (edit_map link), "
                        + "then try_input scroll over one of them to measure where each sits.");
    }

    /**
     * {@code screen} without any strip or tree holding one of {@code tree}'s nodes, and {@code tree} last unless it has
     * none. The tree it replaces keeps its field and traits, and each node it had keeps its lines and offsets to nodes
     * still in it; only how it scrolls is {@code tree}'s.
     */
    static MapScreen withTree(MapScreen screen, String field, Tree tree) {
        MapScreen now = screen;
        Tree had = null;
        String hadField = null;
        // Each node any tree it leaves had, with its lines and offsets: the first tree's wins.
        Map<String, Node> kept = new LinkedHashMap<>();
        for (Map.Entry<String, Input> held : screen.inputs().entrySet()) {
            boolean holds = switch (held.getValue()) {
                case Strip s -> tree.members().stream().anyMatch(s::has);
                case Tree t -> held.getKey().equals(field) || tree.members().stream().anyMatch(t::has);
                default -> false;
            };
            if (!holds) continue;
            if (held.getValue() instanceof Tree t) {
                if (had == null) {
                    had = t;
                    hadField = held.getKey();
                }
                for (Node n : t.nodes()) if (n.picture() != null) kept.putIfAbsent(n.picture().id(), n);
            }
            now = now.withoutInput(held.getKey());
        }
        if (tree.nodes().isEmpty()) return now;
        Tree made = tree.nodes(tree.nodes().stream()
                .map(n -> n.picture() == null ? n : kept.getOrDefault(n.picture().id(), n)).toList());
        if (had != null) {
            made = new Tree(made.nodes(), tree.isDragged(), tree.notches(), had.isTried(), had.isLeaving(),
                    had.isInfo(), had.never(), had.comesAndGoes(), had.lock(), had.when());
        }
        for (String gone : kept.keySet()) {
            if (made.members().stream().noneMatch(p -> p != null && p.id().equals(gone))) made = made.without(gone);
        }
        String name = hadField != null ? hadField : field != null && !now.inputs().containsKey(field) ? field
                : now.fieldFor((tree.members().isEmpty() ? "" : tree.members().getFirst().id()) + " tree");
        return now.withInput(name, made);
    }

    /**
     * Records that the lines of {@code parents} lead to {@code node}, each a node of the same tree of {@code on}
     * ({@code constant}): they unlock it. No parent clears its lines. By picture id, any case.
     */
    public static String link(StudioServices services, String constant, MapScreen on, String node,
                              List<String> parents) {
        Optional<Map.Entry<String, Tree>> held = on.trees().entrySet().stream()
                .filter(e -> idOf(e.getValue(), node).isPresent()).findFirst();
        if (held.isEmpty()) {
            return NOT_WRITTEN + node + " is no node of a tree of " + on.name() + ": edit_map group it first.";
        }
        Tree tree = held.get().getValue();
        ImageTemplate child = idOf(tree, node).orElseThrow();
        List<ImageTemplate> after = new ArrayList<>();
        for (String parent : parents) {
            Optional<ImageTemplate> found = idOf(tree, parent);
            if (found.isEmpty()) return NOT_WRITTEN + parent + " is no node of " + held.get().getKey() + ".";
            if (found.get().id().equals(child.id())) return NOT_WRITTEN + node + " cannot unlock itself.";
            if (!after.contains(found.get())) after.add(found.get());
        }
        List<Node> nodes = tree.nodes().stream()
                .map(n -> n.picture() != null && n.picture().id().equals(child.id()) ? n.after(after) : n).toList();
        String done = after.isEmpty() ? child.id() + " has no lines leading to it."
                : child.id() + " is unlocked by " + after.stream().map(ImageTemplate::id)
                .collect(Collectors.joining(", ")) + ".";
        return said(ScreenBook.update(services, constant, s -> s.withInput(held.get().getKey(), tree.nodes(nodes))),
                done);
    }

    /**
     * Writes where {@code node} of {@code constant}'s tree {@code field} sits {@code beside} a node, as try_input writes
     * one it measured: an offset it had from that node is replaced, and one that node had from it is taken off.
     */
    public static String placeNode(StudioServices services, String constant, String field, ImageTemplate node,
                                   Beside beside) {
        return said(ScreenBook.update(services, constant, s -> s.trees().containsKey(field) ? s.withInput(field,
                        TreeMeasure.with(s.trees().get(field), List.of(new TreeMeasure.Offset(node, beside)))) : s),
                node.id() + " sits " + beside.across() + " of " + beside.other().id() + "'s widths across and "
                        + beside.down() + " of its heights down from it.");
    }

    /** Takes off the offset between {@code node} and {@code other} of {@code constant}'s tree {@code field}, either way. */
    public static String unplaceNode(StudioServices services, String constant, String field, String node,
                                     String other) {
        return said(ScreenBook.update(services, constant, s -> s.trees().containsKey(field)
                        ? s.withInput(field, apart(s.trees().get(field), node, other)) : s),
                node + " is no longer placed from " + other + ".");
    }

    /** {@code tree} with no offset between the nodes {@code node} and {@code other}, written on either. */
    static Tree apart(Tree tree, String node, String other) {
        return tree.nodes(tree.nodes().stream().map(n -> {
            if (n.picture() == null) return n;
            String id = n.picture().id();
            String gone = id.equals(node) ? other : id.equals(other) ? node : null;
            return gone == null ? n : n.beside(n.beside().stream()
                    .filter(b -> b.other() == null || !b.other().id().equals(gone)).toList());
        }).toList());
    }

    /** The picture of {@code tree}'s node named {@code id}, any case. */
    private static Optional<ImageTemplate> idOf(Tree tree, String id) {
        String typed = id == null ? "" : id.strip();
        return tree.members().stream().filter(p -> p != null && p.id().equalsIgnoreCase(typed)).findFirst();
    }

    /**
     * Makes the field {@code field} {@code constant}'s way back; a way back it had that was no field of its own is an
     * untried input of it again.
     */
    public static String useAsBack(StudioServices services, String constant, String field) {
        return said(ScreenBook.update(services, constant, s -> keepBack(s).setup(x -> x.back(
                        BotConstants.input(constant, field)))
                        .withInput(field, untried(s.input(field).orElseThrow()))),
                field + " is the way back.");
    }

    /** {@code screen} with its way back, when that is no field of its own, one of its inputs untried. */
    private static MapScreen keepBack(MapScreen screen) {
        Input back = screen.back();
        if (back == null || BotConstants.inputName(back) != null || screen.fieldOf(back).isPresent()) return screen;
        return screen.withInput(screen.fieldFor(back.label()), untried(back));
    }

    /** Gives {@code constant} the name the game shows, {@code name}; its constant stays. */
    public static String rename(StudioServices services, String constant, String name) {
        if (name == null || !MapTools.isName(name.strip())) return NOT_WRITTEN + "name it in plain words, on one line.";
        String typed = name.strip();
        Optional<String> taken = ScreenBook.find(ScreenBook.read(services), typed).filter(c -> !c.equals(constant));
        if (taken.isPresent()) return NOT_WRITTEN + "Screens." + taken.get() + " is called " + typed + " already.";
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.name(typed))), "Renamed " + typed + ".");
    }

    /** Makes {@code constant} a popup, or a screen of the tree again ({@code popup} false). */
    public static String setPopup(StudioServices services, String constant, boolean popup) {
        boolean panel = ScreenBook.read(services).screen(constant).filter(MapScreen::isPanel).isPresent();
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> popup ? x.popup() : x.notPopup())),
                popup ? "A popup now" + (panel ? ", no longer a panel." : ".") : "No longer a popup.");
    }

    /** Makes {@code constant} a screen of the tree: no popup, start screen or panel, whichever marks it had. */
    public static String ofTheTree(StudioServices services, String constant) {
        MapScreen screen = ScreenBook.read(services).screens().get(constant);
        if (screen == null) return NOT_WRITTEN + "no screen " + constant + " in the map.";
        if (!screen.isPopup() && !screen.isStart() && !screen.isPanel()) {
            return "A screen of the tree already: nothing to change.";
        }
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.notPopup().notStart().panelOf(null))),
                "A screen of the tree now.");
    }

    /**
     * Makes {@code constant} the screen the game opens on, its loading screen ({@code start} true), or no start screen
     * any more. Refused for one with a way in: the game opens on it, so nothing leads into it. Another start screen
     * loses its mark in the same step of Studio's undo: the map has one.
     */
    public static String setStart(StudioServices services, String constant, boolean start) {
        ScreenGraph graph = ScreenBook.read(services);
        MapScreen screen = graph.screens().get(constant);
        if (screen == null) return NOT_WRITTEN + "no screen " + constant + " in the map.";
        if (!start) return said(ScreenBook.update(services, constant, s -> s.setup(Setup::notStart)),
                "No longer the start screen.");
        if (screen.isPopup()) {
            return NOT_WRITTEN + graph.shown(constant) + " is a popup, opened over any screen: make it a screen of "
                    + "the tree first.";
        }
        if (screen.isPanel()) {
            return NOT_WRITTEN + graph.shown(constant) + " is a panel, shown inside another screen: make it a screen "
                    + "of its own first.";
        }
        if (!screen.ways().isEmpty()) {
            return NOT_WRITTEN + "the game opens on a start screen, so no way leads into it: take "
                    + graph.shown(constant) + "'s ways in off first.";
        }
        Optional<String> was = graph.start().filter(c -> !c.equals(constant));
        Optional<String> failed = ScreenBook.step(services, "Make " + graph.shown(constant) + " the start screen",
                () -> was.flatMap(c -> ScreenBook.update(services, c, s -> s.setup(Setup::notStart)))
                        .or(() -> ScreenBook.update(services, constant, s -> s.setup(Setup::start))));
        return said(failed, "The game opens on " + graph.shown(constant) + " now"
                + was.map(c -> "; " + graph.shown(c) + " is no start screen any more.").orElse(".")
                + " Give the screen after it a way on its own.");
    }

    /**
     * Makes {@code constant} a panel of the screen its way in is on ({@code panel} true), Upgrade shown inside Home, or
     * a screen of its own again. Refused for a popup, a start screen, and a screen no input of another opens.
     */
    public static String setPanel(StudioServices services, String constant, boolean panel) {
        ScreenGraph graph = ScreenBook.read(services);
        MapScreen screen = graph.screens().get(constant);
        if (screen == null) return NOT_WRITTEN + "no screen " + constant + " in the map.";
        if (!panel) return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.panelOf(null))),
                "A screen of its own now.");
        if (screen.isPopup() || screen.isStart()) {
            return NOT_WRITTEN + graph.shown(constant) + " is " + (screen.isPopup() ? "a popup, opened over any screen"
                    : "the start screen, in no other") + ": a panel is opened by an input of the screen it is in.";
        }
        Optional<String> parent = graph.parent(constant).filter(c -> !graph.followsOnItsOwn(constant));
        if (parent.isEmpty()) {
            return NOT_WRITTEN + "a panel is opened by an input of the screen it is in, and no input opens "
                    + graph.shown(constant) + ": give it a way in first.";
        }
        return said(ScreenBook.update(services, constant,
                        s -> s.setup(x -> x.panelOf(BotConstants.screen(parent.get())))),
                "A panel of " + graph.shown(parent.get()) + " now.");
    }

    /**
     * Gives the screen that follows {@code constant} on its own {@code seconds} to show before a walk calls the game
     * stuck loading; 0 for the default, {@link Setup#LOADS}.
     */
    public static String setLoads(StudioServices services, String constant, int seconds) {
        if (seconds < 0 || seconds > 3600) return NOT_WRITTEN + "give it 1 to 3600 seconds, or 0 for the default.";
        Duration loads = seconds == 0 ? null : Duration.ofSeconds(seconds);
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.loads(loads))),
                "The screen after it is given " + (loads == null ? Setup.LOADS : loads).toSeconds() + " s to show.");
    }

    /** Adds the screen the game opens on, called {@code name}: a start screen with no way in, owing its marker. */
    public static String newStartScreen(StudioServices services, String name) {
        if (name == null || !MapTools.isName(name.strip())) return NOT_WRITTEN + "name it in plain words, on one line.";
        ScreenGraph graph = ScreenBook.read(services);
        if (ScreenBook.find(graph, name.strip()).isPresent()) {
            return NOT_WRITTEN + "the map has " + name.strip() + " already.";
        }
        Optional<String> was = graph.start();
        if (was.isPresent()) return NOT_WRITTEN + "the game opens on " + graph.shown(was.get()) + " already.";
        String constant = ScreenBook.constantFor(graph, name.strip());
        return said(ScreenBook.add(services, constant, MapScreen.of(Setup.named(name.strip()).start())),
                "Added " + name.strip() + " as Screens." + constant + ", the screen the game opens on: it owes its "
                        + "marker.");
    }

    /**
     * Records that {@code to} follows {@code after} on its own, nothing pressed: Home once the loading screen is done.
     * Its way in, when it has none, else one more.
     */
    public static String addWayOnItsOwn(StudioServices services, String to, String after) {
        ScreenGraph graph = ScreenBook.read(services);
        MapScreen target = graph.screens().get(to);
        MapScreen before = graph.screens().get(after);
        if (target == null || before == null) {
            return NOT_WRITTEN + "no screen " + (target == null ? to : after) + " in the map.";
        }
        if (to.equals(after)) return NOT_WRITTEN + "a screen does not follow itself.";
        if (target.isPopup() || before.isPopup()) {
            return NOT_WRITTEN + graph.shown(target.isPopup() ? to : after) + " is a popup, opened by the game "
                    + "itself: it is followed by no screen of the tree.";
        }
        if (target.isStart()) return NOT_WRITTEN + graph.shown(to) + " is the start screen: nothing leads into it.";
        if (graph.dependsOn(after).contains(to)) {
            return NOT_WRITTEN + graph.shown(after) + " is reached through " + graph.shown(to) + ".";
        }
        if (target.ways().stream().anyMatch(way -> wayFrom(graph, way, after, null))) {
            return NOT_WRITTEN + graph.shown(to) + " follows " + graph.shown(after) + " on its own already.";
        }
        Way way = Way.onItsOwn(BotConstants.screen(after));
        return said(ScreenBook.update(services, to, s -> s.setup(x -> {
                    if (x.from() == null) return x.fromWay(way);
                    List<Way> also = new ArrayList<>(x.alsoFrom());
                    also.add(way);
                    return x.alsoFrom(also);
                })),
                graph.shown(to) + " follows " + graph.shown(after) + " on its own.");
    }

    /** Puts {@code notes} on {@code constant}, over what it had; blank removes them. */
    public static String setNotes(StudioServices services, String constant, String notes) {
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.notes(notes == null ? null
                        : notes.strip()))),
                notes == null || notes.isBlank() ? "Notes removed." : "Notes saved.");
    }

    /** not_a_screen: {@code constant} off the map, its way in noted on its parent as changing it in place. */
    public static String notAScreen(AgentContext context, String constant) {
        return ScreenDesk.text(MapTools.notAScreen(new MapTools.Mistake(constant), context));
    }

    /** Deletes {@code constant} and its shot; refused while another screen is reached through one of its inputs. */
    public static String delete(StudioServices services, String constant) {
        ScreenGraph graph = ScreenBook.read(services);
        List<String> after = graph.screens().keySet().stream()
                .filter(c -> !c.equals(constant) && (graph.opensFrom(c).contains(constant)
                        || graph.panelOf(c).filter(constant::equals).isPresent())).map(graph::shown).toList();
        if (!after.isEmpty()) {
            return NOT_WRITTEN + String.join(", ", after) + " is reached from it or a panel of it: delete those "
                    + "first.";
        }
        Optional<String> failed = ScreenBook.step(services, "Delete " + graph.shown(constant),
                () -> ScreenBook.remove(services, constant));
        if (failed.isEmpty()) ScreenShots.remove(services.resourcesDir(), constant);
        return said(failed, "Deleted " + graph.shown(constant) + ".");
    }

    /**
     * {@code constant}'s whole-screen shot taken again from the game now, only while the game shows it: its marker is
     * found. Off the FX thread: it reads the game.
     */
    public static String retakeShot(AgentContext context, String constant) {
        Optional<BufferedImage> frame = Frames.look(context);
        return frame.isEmpty() ? Frames.noScreen() : retakeShot(context, constant, frame.get());
    }

    /** As {@link #retakeShot(AgentContext, String)}, from {@code frame}: the live board's frozen frame. */
    public static String retakeShot(AgentContext context, String constant, BufferedImage frame) {
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return Frames.noProject();
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(context.services()));
        } catch (IllegalStateException busy) {
            return "Studio did not hand over the bot's Screens: " + busy.getMessage();
        }
        MapScreen screen = graph.screens().get(constant);
        if (screen == null) return "No screen " + constant + " in the map.";
        if (!screen.hasMarker()) {
            return graph.shown(constant) + " has no marker yet, so nothing tells it from the frame: cut its marker, "
                    + "which takes its shot.";
        }
        if (!ScreenFit.shows(reach.get(), graph, constant, frame)) {
            return "The game does not show " + graph.shown(constant) + " now: its markers are not found. The shot was "
                    + "kept; go there first.";
        }
        ScreenShots.save(context.services().resourcesDir(), constant, frame);
        return "Shot taken.";
    }

    /** place_pictures: pictures the bot has, put on {@code screen} as inputs, its marker or its way back. */
    public static String place(AgentContext context, String screen, List<String> pictures, String marker,
                               String back) {
        return ScreenDesk.text(MapTools.placePictures(new MapTools.Places(screen, pictures, marker, back, null, null),
                context));
    }

    /**
     * Adds a screen the game calls {@code name} under {@code parent}, opened by its field {@code viaField}, without
     * trying it; with no field, {@code via} is put on {@code parent} first, a field named from what it acts on.
     */
    public static String newScreen(StudioServices services, String parent, String name, String viaField, Input via) {
        if (name == null || !MapTools.isName(name.strip())) return NOT_WRITTEN + "name it in plain words, on one line.";
        ScreenGraph graph = ScreenBook.read(services);
        if (ScreenBook.find(graph, name.strip()).isPresent()) {
            return NOT_WRITTEN + "the map has " + name.strip() + " already.";
        }
        MapScreen on = graph.screens().get(parent);
        if (on == null) return NOT_WRITTEN + "no screen " + parent + " in the map.";
        if (viaField == null && via == null) return NOT_WRITTEN + "name the input that opens it.";
        String field = viaField != null ? viaField : on.fieldOf(via).orElseGet(() -> on.fieldFor(via.label()));
        if (on.input(field).isEmpty() && via == null) return NOT_WRITTEN + parent + " has no input " + field + ".";
        String constant = ScreenBook.constantFor(graph, name.strip());
        MapScreen made = MapScreen.of(Setup.named(name.strip()).from(BotConstants.input(parent, field)));
        Input entrance = on.input(field).orElseGet(() -> untried(via));
        // The entrance on the parent and the new screen: one step of Studio's undo.
        Optional<String> failed = ScreenBook.step(services, "Add " + name.strip(), () -> ScreenBook.update(services,
                parent, s -> s.withInput(field, untried(entrance)))
                .or(() -> ScreenBook.add(services, constant, made)));
        return said(failed, "Added " + name.strip() + " as Screens." + constant + ": it owes its marker.");
    }

    /**
     * Makes {@code to}'s way in through {@code from}'s field {@code viaField} open only while {@code method} answers
     * true — a method reference, {@code Firestone::eventOn} — or always, {@code method} blank.
     */
    public static String setWayWhen(StudioServices services, String to, String from, String viaField,
                                    String method) {
        if (!ScreenTypes.isMethod(method)) return notMethod(method);
        BooleanSupplier when = ScreenTypes.condition(method);
        ScreenGraph graph = ScreenBook.read(services);
        if (ways(graph.screens().get(to)).stream().noneMatch(way -> wayFrom(graph, way, from, viaField))) {
            return noWay(graph, to, from, viaField);
        }
        return said(ScreenBook.update(services, to, s -> withWayWhen(graph, s, from, viaField, when)),
                when == null ? "That way is always open now." : "That way is open only while " + method.strip() + ".");
    }

    /** {@code screen} with its way in through {@code from}'s field {@code viaField} taken only while {@code when}. */
    static MapScreen withWayWhen(ScreenGraph graph, MapScreen screen, String from, String viaField,
                                 BooleanSupplier when) {
        return changingWay(graph, screen, from, viaField, way -> way.when(when));
    }

    /**
     * Makes {@code to}'s way in through {@code from}'s field {@code viaField} go through the copy {@code pick} names of
     * that repeated button; {@code pick} null, or its first copy from the north, for the topmost one.
     */
    public static String setWayPick(StudioServices services, String to, String from, String viaField, Pick pick) {
        ScreenGraph graph = ScreenBook.read(services);
        if (ways(graph.screens().get(to)).stream().noneMatch(way -> wayFrom(graph, way, from, viaField))) {
            return noWay(graph, to, from, viaField);
        }
        Pick kept = pick == null || pick.equals(Pick.first()) ? null : pick;
        if (kept != null && !(graph.input(from, viaField).orElse(null) instanceof Button button && button.repeats())) {
            return NOT_WRITTEN + viaField + " is no button " + graph.shown(from) + " shows more than once: make it "
                    + "repeated first.";
        }
        return said(ScreenBook.update(services, to, s -> withWayPick(graph, s, from, viaField, kept)),
                "That way goes through " + (kept == null ? Pick.first() : kept).label() + " now.");
    }

    /** {@code screen} with its way in through {@code from}'s field {@code viaField} through the copy {@code pick}. */
    static MapScreen withWayPick(ScreenGraph graph, MapScreen screen, String from, String viaField, Pick pick) {
        return changingWay(graph, screen, from, viaField, way -> way.pick(pick));
    }

    /** {@code screen} with its way in through {@code from}'s field {@code viaField} as {@code change} makes it. */
    private static MapScreen changingWay(ScreenGraph graph, MapScreen screen, String from, String viaField,
                                         UnaryOperator<Way> change) {
        return screen.setup(s -> {
            Setup now = s.from() != null && wayFrom(graph, s.from(), from, viaField) ? s.fromWay(change.apply(s.from()))
                    : s;
            return now.alsoFrom(now.alsoFrom().stream()
                    .map(way -> wayFrom(graph, way, from, viaField) ? change.apply(way) : way).toList());
        });
    }

    /** {@code button} stepping through its looks on each press ({@link Button#cycling}), or not. */
    public static Button cycles(Button button, boolean cycles) {
        return new Button(button.picture(), button.isTried(), button.isLeaving(), button.isInfo(), button.never(),
                button.comesAndGoes(), button.lock(), button.looks(), button.when(), button.repeats(), cycles,
                button.gesture(), button.hold());
    }

    /** {@code button} pressed by {@code gesture}, a hold held {@code hold} when given, else as long as it was. */
    static Button gestured(Button button, Gesture gesture, Duration hold) {
        return gesture == Gesture.HOLD && hold != null ? button.hold(hold) : button.gesture(gesture);
    }

    /**
     * {@code constant}'s button {@code field} pressed by {@code gesture}, a hold held {@code hold} (null: the default
     * time); a field that is no button is refused.
     */
    public static String setGesture(StudioServices services, String constant, String field, Gesture gesture,
                                    Duration hold) {
        if (gesture == Gesture.UNKNOWN) return NOT_WRITTEN + "no such gesture.";
        if (hold != null && (hold.isNegative() || hold.compareTo(LONGEST_HOLD) > 0)) {
            return NOT_WRITTEN + "a hold lasts 0 to " + LONGEST_HOLD.toMillis() + " ms.";
        }
        ScreenGraph graph = ScreenBook.read(services);
        if (!(graph.input(constant, field).orElse(null) instanceof Button button)) {
            return NOT_WRITTEN + field + " is no button of " + graph.shown(constant) + ": only a button has a gesture.";
        }
        Button now = gestured(button, gesture, hold);
        return said(ScreenBook.update(services, constant, s -> s.withInput(field, now)),
                field + " is pressed by " + (now.gesture() == Gesture.HOLD
                        ? "a hold of " + now.holdTime().toMillis() + " ms" : now.gesture().displayName()) + " now.");
    }

    /**
     * Records that {@code from}'s field {@code viaField} leads to {@code to}, a screen mapped already: one more way into
     * it, the input untried again so its result is the screen it opens — what try_input writes for an input that led
     * to one mapped already, done by hand.
     */
    public static String addWay(StudioServices services, String to, String from, String viaField) {
        ScreenGraph graph = ScreenBook.read(services);
        Optional<String> refused = wayRefused(graph, to, from, viaField);
        if (refused.isPresent()) return NOT_WRITTEN + refused.get();
        Input via = graph.screens().get(from).input(viaField).orElseThrow();
        Optional<String> failed = ScreenBook.step(services, "Add a way into " + graph.shown(to),
                () -> ScreenBook.update(services, from, s -> s.withInput(viaField, untried(via)))
                        .or(() -> ScreenBook.update(services, to, s -> withWay(s, from, viaField))));
        return said(failed, graph.shown(from) + "'s " + viaField + " leads to " + graph.shown(to) + ".");
    }

    /** Why {@code from}'s {@code viaField} may not be made a way into {@code to}; empty when it may. */
    public static Optional<String> wayRefused(ScreenGraph graph, String to, String from, String viaField) {
        MapScreen target = graph.screens().get(to);
        MapScreen on = graph.screens().get(from);
        if (target == null || on == null) {
            return Optional.of("no screen " + (target == null ? to : from) + " in the map.");
        }
        if (on.input(viaField).isEmpty()) return Optional.of(graph.shown(from) + " has no input " + viaField + ".");
        if (to.equals(from)) {
            return Optional.of("an input that stays on its own screen changes it in place: set it tried, opening "
                    + "nothing.");
        }
        if (target.isPopup()) {
            return Optional.of(graph.shown(to) + " is a popup, opened by the game itself: no way leads into it.");
        }
        if (target.isStart()) {
            return Optional.of(graph.shown(to) + " is the screen the game opens on: no way leads into it.");
        }
        if (graph.parent(from).filter(to::equals).isPresent()) {
            return Optional.of(graph.shown(to) + " is the screen above it: use " + viaField + " as its way back.");
        }
        if (graph.above(from).contains(to)) {
            return Optional.of(graph.shown(from) + " is reached through " + graph.shown(to) + ": set " + viaField
                    + " as leaving for a screen reached otherwise.");
        }
        if (target.ways().stream().anyMatch(way -> wayFrom(graph, way, from, viaField))) {
            return Optional.of(viaField + " leads to " + graph.shown(to) + " already.");
        }
        return Optional.empty();
    }

    /** {@code screen} with one more way in, through {@code from}'s field {@code viaField}. */
    static MapScreen withWay(MapScreen screen, String from, String viaField) {
        return screen.setup(s -> {
            List<Way> ways = new ArrayList<>(s.alsoFrom());
            ways.add(Way.of(BotConstants.input(from, viaField)));
            return s.alsoFrom(ways);
        });
    }

    /**
     * Takes {@code to}'s way in through {@code from}'s field {@code viaField} off the map: its next way in becomes the
     * tree's when it was the tree's. Refused for a screen's only way in through an input: the screen is deleted
     * instead. A way on its own ({@code viaField} null) may go last: the screen is the first one again.
     */
    public static String removeWay(StudioServices services, String to, String from, String viaField) {
        ScreenGraph graph = ScreenBook.read(services);
        MapScreen screen = graph.screens().get(to);
        if (ways(screen).stream().noneMatch(way -> wayFrom(graph, way, from, viaField))) {
            return noWay(graph, to, from, viaField);
        }
        if (viaField != null && screen.from() != null && withoutWay(graph, screen, from, viaField).ways().isEmpty()) {
            return NOT_WRITTEN + "that is " + graph.shown(to) + "'s only way in: delete " + graph.shown(to)
                    + " instead, or take it for a change of " + graph.shown(from) + " in place (not a screen).";
        }
        return said(ScreenBook.update(services, to, s -> withoutWay(graph, s, from, viaField)),
                "Took " + graph.shown(to) + "'s way in from " + graph.shown(from)
                        + (viaField == null ? " on its own" : " by " + viaField) + " off the map.");
    }

    /** {@code screen} without its ways in through {@code from}'s {@code viaField}; the tree's is its next one. */
    private static MapScreen withoutWay(ScreenGraph graph, MapScreen screen, String from, String viaField) {
        return screen.setup(s -> {
            List<Way> also = s.alsoFrom().stream().filter(way -> !wayFrom(graph, way, from, viaField)).toList();
            if (s.from() == null || !wayFrom(graph, s.from(), from, viaField)) return s.alsoFrom(also);
            return also.isEmpty() ? s.fromWay(null).alsoFrom(also)
                    : s.fromWay(also.getFirst()).alsoFrom(also.subList(1, also.size()));
        });
    }

    /** {@code screen}'s ways in, the tree's first; none for no screen. */
    private static List<Way> ways(MapScreen screen) {
        return screen == null ? List.of() : screen.ways();
    }

    private static String noWay(ScreenGraph graph, String to, String from, String viaField) {
        return NOT_WRITTEN + graph.shown(to) + " has no way in from " + graph.shown(from)
                + (viaField == null ? " on its own." : " by " + viaField + ".");
    }

    /** Whether {@code way} leaves {@code from} by its field {@code viaField}, or on its own ({@code viaField} null). */
    private static boolean wayFrom(ScreenGraph graph, Way way, String from, String viaField) {
        if (viaField == null) return way.isOnItsOwn() && graph.onScreen(way).filter(from::equals).isPresent();
        return graph.nameOf(way.via()).filter(new InputName(from, viaField)::equals).isPresent();
    }

    /**
     * Takes {@code constant}'s own way back off, a walk then closing it by its close button, else Escape: the input
     * it was stays an input of the screen, untried again.
     */
    public static String removeBack(StudioServices services, String constant) {
        ScreenGraph graph = ScreenBook.read(services);
        MapScreen screen = graph.screens().get(constant);
        if (screen == null || screen.back() == null) return NOT_WRITTEN + "it has no way back of its own.";
        Optional<String> field = graph.nameOf(screen.back()).filter(n -> n.screen().equals(constant))
                .map(InputName::field);
        String was = field.orElse(screen.back().label());
        return said(ScreenBook.update(services, constant, s -> {
                    MapScreen kept = keepBack(s).setup(x -> x.back(null));
                    return field.flatMap(kept::input).map(i -> kept.withInput(field.get(), untried(i))).orElse(kept);
                }),
                "No way back of its own: a walk closes it by its close button, else Escape. " + was
                        + " is untried on it again.");
    }

    /**
     * Adds {@code picture} to what tells {@code constant} apart: one more marker, all of which show, or one it never
     * shows ({@code absent}). Refused when another screen's shot shows the set too: a run would take that one for it.
     * Off the FX thread, unlike the other edits: it matches the set over every other screen's shot.
     */
    public static String addMarker(StudioServices services, String constant, ImageTemplate picture, boolean absent) {
        MapScreen screen = FxCall.call(() -> ScreenBook.read(services)).screens().get(constant);
        if (screen == null) return NOT_WRITTEN + "no screen " + constant + " in the map.";
        Markers was = Markers.of(screen);
        String id = picture.id();
        if (holds(absent ? was.shown() : was.absent(), id)) {
            return NOT_WRITTEN + id + " is " + (absent ? "one of its markers" : "a picture it never shows")
                    + ": remove it from there first.";
        }
        if (holds(absent ? was.absent() : was.shown(), id)) return "It is told by " + was.said() + " already.";
        Markers now = absent ? new Markers(was.shown(), plus(was.absent(), picture))
                : new Markers(plus(was.shown(), picture), was.absent());
        return toldBy(services, constant, now);
    }

    /**
     * Takes {@code picture} off what tells {@code constant} apart, its markers or the pictures it never shows. Refused
     * for its last marker, and when another screen's shot shows what is left. Off the FX thread, as {@link #addMarker}.
     */
    public static String removeMarker(StudioServices services, String constant, String picture) {
        MapScreen screen = FxCall.call(() -> ScreenBook.read(services)).screens().get(constant);
        if (screen == null) return NOT_WRITTEN + "no screen " + constant + " in the map.";
        Markers was = Markers.of(screen);
        String id = picture == null ? "" : picture.strip();
        if (!holds(was.shown(), id) && !holds(was.absent(), id)) {
            return NOT_WRITTEN + "it is told by " + was.said() + ", not " + id + ".";
        }
        Markers now = new Markers(minus(was.shown(), id), minus(was.absent(), id));
        if (!now.any()) return NOT_WRITTEN + id + " is its last marker: add another first.";
        return toldBy(services, constant, now);
    }

    /** {@code constant} told by {@code now}, unless another screen's shot shows it too. */
    private static String toldBy(StudioServices services, String constant, Markers now) {
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<MapAudit.Clash> clash = MapAudit.clash(services.resourcesDir(), MapAudit.confidence(services), graph,
                constant, now);
        if (clash.isPresent()) return NOT_WRITTEN + MapTools.clashSaid(graph, constant, now, clash.get());
        return said(FxCall.call(() -> ScreenBook.update(services, constant,
                s -> s.setup(x -> x.markers(now.shown()).absent(now.absent())))), "Told by " + now.said() + ".");
    }

    private static boolean holds(List<ImageTemplate> pictures, String id) {
        return pictures.stream().anyMatch(p -> p.id().equalsIgnoreCase(id));
    }

    private static List<ImageTemplate> plus(List<ImageTemplate> pictures, ImageTemplate picture) {
        List<ImageTemplate> more = new ArrayList<>(pictures);
        more.add(picture);
        return more;
    }

    private static List<ImageTemplate> minus(List<ImageTemplate> pictures, String id) {
        return pictures.stream().filter(p -> !p.id().equalsIgnoreCase(id)).toList();
    }

    /** {@code constant}'s look {@code look} shown only while {@code method} answers true; blank: no method says. */
    public static String setLookWhen(StudioServices services, String constant, String look, String method) {
        if (!ScreenTypes.isMethod(method)) return notMethod(method);
        BooleanSupplier when = ScreenTypes.condition(method);
        MapScreen screen = ScreenBook.read(services).screens().get(constant);
        Optional<ScreenVariant> had = screen == null || look == null ? Optional.empty() : screen.variants().stream()
                .filter(v -> v.name().equalsIgnoreCase(look.strip())).findFirst();
        if (had.isEmpty()) {
            return NOT_WRITTEN + "no look " + look + " of it" + (screen == null || screen.variants().isEmpty() ? ""
                    : ": its looks are " + String.join(", ", screen.variants().stream().map(ScreenVariant::name)
                    .toList())) + ".";
        }
        String name = had.get().name();
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.withVariant(had.get().when(when)))),
                when == null ? "No method says when " + name + " shows." : name + " shows only while "
                        + method.strip() + ".");
    }

    /** {@code constant}'s field {@code field} there only while {@code method} answers true; blank: no method says. */
    public static String setInputWhen(StudioServices services, String constant, String field, String method) {
        if (!ScreenTypes.isMethod(method)) return notMethod(method);
        BooleanSupplier when = ScreenTypes.condition(method);
        return said(ScreenBook.update(services, constant, s -> s.input(field)
                        .map(input -> s.withInput(field, input.when(when))).orElse(s)),
                when == null ? "No method says when " + field + " is there."
                        : field + " is there only while " + method.strip() + ".");
    }

    /**
     * Puts each screen {@code corners} names at its card's corner on 🗺 Screens' map; a screen already there is not
     * written. Nothing a run reads changes.
     */
    public static String move(StudioServices services, Map<String, Point> corners) {
        ScreenGraph graph = ScreenBook.read(services);
        Map<String, Point> moving = new LinkedHashMap<>();
        corners.forEach((constant, corner) -> {
            MapScreen screen = graph.screens().get(constant);
            if (screen != null && !Objects.equals(screen.at(), corner)) moving.put(constant, corner);
        });
        if (moving.isEmpty()) return "Nothing moved.";
        // One step of Studio's undo for the whole drag, however many cards it pinned.
        String label = moving.size() == 1 ? "Move " + graph.shown(moving.keySet().iterator().next())
                : "Move " + moving.size() + " cards";
        String[] answer = {moving.size() == 1 ? "Moved 1 screen." : "Moved " + moving.size() + " screens."};
        services.history().batch(label, () -> {
            for (Map.Entry<String, Point> corner : moving.entrySet()) {
                Optional<String> failed = ScreenBook.update(services, corner.getKey(),
                        s -> s.setup(x -> x.at(corner.getValue())));
                if (failed.isPresent()) {
                    answer[0] = said(failed, "");
                    return;
                }
            }
        });
        return answer[0];
    }

    /** Puts every card of 🗺 Screens' map back where the layout puts it: no screen keeps a corner of its own. */
    public static String tidy(StudioServices services) {
        ScreenGraph graph = ScreenBook.read(services);
        String[] answer = {"Every card is where the layout puts it."};
        services.history().batch("Tidy the map", () -> {
            for (Map.Entry<String, MapScreen> screen : graph.screens().entrySet()) {
                if (screen.getValue().at() == null) continue;
                Optional<String> failed = ScreenBook.update(services, screen.getKey(), s -> s.setup(x -> x.at(null)));
                if (failed.isPresent()) {
                    answer[0] = said(failed, "");
                    return;
                }
            }
        });
        return answer[0];
    }

    /** The bot's picture {@code name}, as its constant holds it. */
    public static ImageTemplate picture(StudioServices services, String name) {
        return ScreenBook.picture(services, name);
    }

    /** Every picture the bot has, by name. */
    public static List<String> pictures(StudioServices services) {
        Path resources = services.resourcesDir();
        if (resources == null) return List.of();
        List<String> names = new ArrayList<>();
        for (Path file : TemplateLibrary.list(resources)) {
            if (!TemplateLibrary.isUnmodifiedDefaultTemplate(file)) names.add(TemplateLibrary.baseName(file));
        }
        names.sort(String::compareTo);
        return names;
    }

    private static String notMethod(String method) {
        return NOT_WRITTEN + "\"" + method.strip() + "\" is no method reference (Firestone::eventOn).";
    }

    private static String said(Optional<String> failed, String done) {
        return failed.map(why -> NOT_WRITTEN + why).orElse(done);
    }
}
