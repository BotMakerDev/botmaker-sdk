package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Grid;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Place;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Typing;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants.InputName;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * edit_map: the map fixed by hand, as 🗺 Screens' menus fix it, by the same {@link ScreenEdits} methods (the user's
 * call, 2026-10-15). What try_input recorded wrong (a panel taken for a way back, a result, a way in) or what the game
 * changed since is set here, nothing clicked. An input is named as try_input names it: by its picture (a strip by its
 * first member, with scroll) or its hotkey.
 */
final class MapEdits {

    private static final String SCREEN = "Home or HOME; new_screen: the one it opens from";
    private static final String PICTURE = "the input: its field (beerBuy) or picture";
    private static final String SCROLL = "with picture: a strip's notches to it";
    private static final String KEY = "the input, by its hotkey: Q";
    private static final String FROM = "the screen the way in leaves";
    private static final String METHOD = "the bot's method true while it holds (Firestone::eventOn); blank: always";

    /**
     * What set_input records an input did, opening nothing: tried, or leaves for a screen reached otherwise; untried
     * forgets it.
     */
    enum Result {
        TRIED, LEAVES, UNTRIED
    }

    /** One edit of the bot's Screens; {@link #screen} is the screen it edits. */
    sealed interface Edit {
        String screen();
    }

    /** An edit of one input of the screen, named as try_input names it: by its picture, or its hotkey. */
    interface OfInput {
        String picture();

        Integer scroll();

        String key();
    }

    @Describe("rename the screen")
    record Rename(@Describe(SCREEN) String screen,
                  @Describe("the new name, or new_screen's, as shown") String name) implements Edit {
    }

    @Describe("delete the screen")
    record Delete(String screen) implements Edit {
    }

    /** What kind of screen set_kind makes it: one of the tree, a popup, the start screen, or a panel. */
    enum Kind {
        SCREEN, POPUP, START, PANEL
    }

    @Describe("screen; popup, the game opens it; start, it opens on it (new if none so called); panel, in the screen "
            + "it opens from")
    record SetKind(String screen, Kind as) implements Edit {
    }

    @Describe("an input's result, info, never, lock, sometimes, repeated, gesture, text field, drag")
    record SetInput(String screen,
                    @Describe(value = PICTURE, optional = true) String picture,
                    @Describe(value = SCROLL, optional = true) Integer scroll,
                    @Describe(value = KEY, optional = true) String key,
                    @Describe(value = "what it does, opening nothing (else new_screen)", optional = true) Result did,
                    @Describe(value = "true: it only tells (help, rules), tried last", optional = true) Boolean info,
                    @Describe(value = "why never clicked; blank clears", optional = true) String never,
                    @Describe(value = "the picture proving it locked; blank clears", optional = true) String locked,
                    @Describe(value = "true: shows only at times", optional = true) Boolean sometimes,
                    @Describe(value = "true: a button shown more than once", optional = true) Boolean repeated,
                    @Describe(value = "how a button is pressed", optional = true) Gesture gesture,
                    @Describe(value = "a hold's time; a hotkey's keys held", optional = true) Integer holdMs,
                    @Describe(value = "a text field's text; blank: the body's", optional = true) String types,
                    @Describe(value = "a field: Enter after", optional = true) Boolean submits,
                    @Describe(value = "a field: Ctrl+A first", optional = true) Boolean clears,
                    @Describe(value = "dragged onto this picture; blank: no drag", optional = true) String dragOnto,
                    @Describe(value = "dragged into this Zones zone", optional = true) String dragInto,
                    @Describe(value = "a drag's time", optional = true) Integer dragMs,
                    @Describe(value = "a button found only at this side of anchor; unknown: anywhere", optional = true)
                    Place.Side side,
                    @Describe(value = "a picture", optional = true) String anchor,
                    @Describe(value = "between's second", optional = true) String other)
            implements Edit, OfInput {
    }

    @Describe("an input untried again")
    record ResetInput(String screen, @Describe(value = "", optional = true) String picture,
                      @Describe(value = "", optional = true) Integer scroll,
                      @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("remove an input")
    record RemoveInput(String screen, @Describe(value = "", optional = true) String picture,
                       @Describe(value = "", optional = true) Integer scroll,
                       @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("an input as its way back")
    record UseAsBack(String screen, @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("its way back an untried input again")
    record RemoveBack(String screen) implements Edit {
    }

    @Describe("remove a way in: from, its input")
    record RemoveWay(String screen, @Describe(FROM) String from,
                     @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("a way in by from's input; or none + seconds (from's load, 0: 60): shows by itself after from")
    record AddWay(String screen, String from,
                  @Describe(value = "", optional = true) String picture,
                  @Describe(value = "", optional = true) Integer scroll,
                  @Describe(value = "", optional = true) String key,
                  @Describe(value = "", optional = true) Integer seconds) implements Edit, OfInput {
    }

    @Describe("a way in (from, its input) open only while method holds")
    record WayWhen(String screen, String from,
                   @Describe(value = "", optional = true) String picture,
                   @Describe(value = "", optional = true) Integer scroll,
                   @Describe(value = "", optional = true) String key,
                   @Describe(value = METHOD, optional = true) String method) implements Edit, OfInput {
    }

    @Describe("an input there only while method holds")
    record InputWhen(String screen, @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key,
                     @Describe(value = "", optional = true) String method) implements Edit, OfInput {
    }

    /** What a group of a screen's buttons is: a strip along one line, or a tree of linked nodes. */
    enum GroupKind {
        STRIP, TREE
    }

    /** How a group scrolls. */
    enum GroupScroll {
        WHEEL, DRAG, NONE
    }

    @Describe("buttons scrolled together: a strip (one line; by none: off) or a tree (linked nodes)")
    record Group(String screen, @Describe(value = "", optional = true) GroupKind kind,
                 @Describe(value = "its buttons' pictures, in order", optional = true) List<String> members,
                 @Describe(value = "drag: a tree only", optional = true) GroupScroll by,
                 @Describe(value = "wheel notches a scroll, - down", optional = true) Integer notches)
            implements Edit {
    }

    @Describe("a tree node's parents (lines lead to it)")
    record Link(String screen, String picture,
                @Describe(value = "their pictures; none clears them", optional = true) List<String> after)
            implements Edit {
    }

    @Describe("a look shown only while method holds")
    record LookWhen(String screen, @Describe("the look's name") String look,
                    @Describe(value = "", optional = true) String method) implements Edit {
    }

    @Describe("add a marker (all show), or absent: one it never shows; refused if another shot shows the set")
    record AddMarker(String screen, @Describe("a picture the bot has") String picture,
                     @Describe(value = "", optional = true) Boolean absent) implements Edit {
    }

    @Describe("take a marker, or one it never shows, off")
    record RemoveMarker(String screen, String picture) implements Edit {
    }

    @Describe("words read in a zone: a marker, or with field a text button clicked on them")
    record AddText(String screen, @Describe("as the screen shows them") String text,
                   @Describe("a Zones constant") String zone,
                   @Describe(value = "", optional = true) String field) implements Edit {
    }

    @Describe("take a text marker off")
    record RemoveText(String screen, String text) implements Edit {
    }

    @Describe("a pictureless button: across widths, down heights from anchor picture's centre; rows×columns: a grid "
            + "step apart")
    record AddSpot(String screen, String field, String anchor, Double across, Double down,
                   @Describe(value = "", optional = true) Integer rows,
                   @Describe(value = "", optional = true) Integer columns,
                   @Describe(value = "", optional = true) Double stepAcross,
                   @Describe(value = "", optional = true) Double stepDown) implements Edit {
    }


    @Describe("a new screen, opened by an input of screen (added untried)")
    record NewScreen(String screen, String name,
                     @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            ToolTiming.actions("edit_map", "Fixes the bot's Screens by hand when try_input recorded something "
                    + "wrong or the game changed; clicks nothing", Edit.class,
                    MapEdits::edit));

    private MapEdits() {}

    static AgentReply edit(Edit edit, AgentContext context) {
        if (context.services().projectDir() == null) return AgentReply.refused(Frames.noProject());
        try {
            // Off the FX thread: what tells a screen apart is matched over every other screen's shot first.
            if (edit instanceof AddMarker || edit instanceof RemoveMarker) return marker(edit, context);
            return FxCall.call(() -> {
                ScreenGraph before = ScreenBook.read(context.services());
                AgentReply reply = edited(edit, context);
                // What ScreenEdits would not write (a name taken, no such way) is refused already. A write that
                // left the map as it was asked for what it already said: said plainly, not as an error.
                return reply.isRefused() || !before.screens().equals(ScreenBook.read(context.services()).screens())
                        ? reply : AgentReply.text("Nothing to change: the map already says so.");
            });
        } catch (IllegalStateException busy) {
            return AgentReply.refused("Studio did not answer while editing the bot's Screens: " + busy.getMessage()
                    + " Read the map (read_map) before trying again.");
        }
    }

    /** {@code edit} done on the FX thread. */
    private static AgentReply edited(Edit edit, AgentContext context) {
        StudioServices services = context.services();
        ScreenGraph graph = ScreenBook.read(services);
        Optional<String> found = ScreenBook.find(graph, edit.screen());
        // A start screen the map lacks is added.
        if (found.isEmpty() && edit instanceof SetKind set && set.as() == Kind.START) {
            return said(ScreenEdits.newStartScreen(services, set.screen()));
        }
        if (found.isEmpty()) {
            return AgentReply.refused("No screen " + edit.screen() + " in the bot's Screens. Nothing was written.");
        }
        String screen = found.get();
        MapScreen on = graph.screens().get(screen);
        return switch (edit) {
            case Rename rename -> said(ScreenEdits.rename(services, screen, rename.name()));
            case Delete ignored -> said(ScreenEdits.delete(services, screen));
            case SetKind set -> said(kind(services, screen, set.as()));
            case RemoveBack ignored -> said(ScreenEdits.removeBack(services, screen));
            case LookWhen look -> said(ScreenEdits.setLookWhen(services, screen, look.look(), method(look.method())));
            case AddWay way -> addWay(way, graph, screen, services);
            case RemoveWay way -> way(way, way.from(), null, graph, screen, services);
            case WayWhen way -> way(way, way.from(), method(way.method()), graph, screen, services);
            case NewScreen opened -> newScreen(opened, graph, screen, context);
            case AddMarker ignored -> marker(edit, context);
            case RemoveMarker ignored -> marker(edit, context);
            case AddText add -> said(ScreenEdits.addText(services, screen, add.zone(), add.text(), add.field()));
            case RemoveText remove -> said(ScreenEdits.removeText(services, screen, remove.text()));
            case AddSpot spot -> said(ScreenEdits.addSpot(services, screen, spot.field(), spot.anchor(),
                    spot.across() == null ? 0 : spot.across(), spot.down() == null ? 0 : spot.down(), grid(spot)));
            case SetInput set -> onInput(set, graph, screen, on, field -> setInput(context, screen, on, field, set));
            case ResetInput reset -> onInput(reset, graph, screen, on, field -> said(ScreenEdits.setInput(services,
                    screen, field, ScreenEdits.untried(on.inputs().get(field)))));
            case RemoveInput remove -> onInput(remove, graph, screen, on,
                    field -> said(ScreenEdits.setInput(services, screen, field, null)));
            case UseAsBack back -> onInput(back, graph, screen, on,
                    field -> said(ScreenEdits.useAsBack(services, screen, field)));
            case InputWhen when -> onInput(when, graph, screen, on,
                    field -> said(ScreenEdits.setInputWhen(services, screen, field, method(when.method()))));
            case Group group -> said(grouped(context, screen, on, group));
            case Link link -> said(ScreenEdits.link(services, screen, on, link.picture(),
                    link.after() == null ? List.of() : link.after()));
        };
    }

    /** {@code spot}'s grid: none for one spot, a step of none along a side of one cell. */
    private static Grid grid(AddSpot spot) {
        int rows = spot.rows() == null ? 1 : spot.rows();
        int columns = spot.columns() == null ? 1 : spot.columns();
        if (rows <= 1 && columns <= 1) return null;
        return Grid.of(rows, columns, spot.stepAcross() == null ? 0 : spot.stepAcross(),
                spot.stepDown() == null ? 0 : spot.stepDown());
    }

    /**
     * {@code screen}'s strip or tree of {@code group}'s members, each one of its buttons' pictures, as the screen
     * holds them (with the place each was cut at); a strip by none takes the strips holding them off (a tree goes by
     * remove_input on its field).
     * Said in a sentence, beginning {@link ScreenEdits#NOT_WRITTEN} when nothing was. On the FX thread.
     */
    static String grouped(AgentContext context, String screen, MapScreen on, Group group) {
        List<String> names = group.members() == null ? List.of()
                : group.members().stream().filter(MapEdits::has).map(String::strip).toList();
        if (names.isEmpty()) return ScreenEdits.NOT_WRITTEN + "name the pictures of the buttons in it (members).";
        List<ImageTemplate> members = new ArrayList<>();
        for (String name : names) {
            Optional<ImageTemplate> member = on.inputs().values().stream()
                    .flatMap(i -> i instanceof Button b ? Stream.of(b.picture()) : Stream.empty())
                    .filter(p -> p.id().equalsIgnoreCase(name)).findFirst();
            if (member.isEmpty()) {
                return ScreenEdits.NOT_WRITTEN + name + " is no button of " + on.name() + ": cut it there with "
                        + "crop_pictures first.";
            }
            members.add(member.get());
        }
        int notches = group.notches() == null ? 0 : group.notches();
        GroupScroll scroll = group.by() != null ? group.by() : notches != 0 ? GroupScroll.WHEEL
                : GroupScroll.NONE;
        if (scroll == GroupScroll.WHEEL && notches == 0) {
            return ScreenEdits.NOT_WRITTEN + "a wheel scroll turns by some notches: give notches.";
        }
        if (group.kind() == GroupKind.TREE) {
            Tree tree = Tree.of(members.stream().map(Node::of).toList());
            return ScreenEdits.setTree(context.services(), screen, null, switch (scroll) {
                case DRAG -> tree.dragged();
                case WHEEL -> tree.wheel(notches);
                case NONE -> tree;
            });
        }
        if (scroll == GroupScroll.DRAG) {
            return ScreenEdits.NOT_WRITTEN + "a strip scrolls by the wheel here; one dragged is tried by its members.";
        }
        return ScreenEdits.setStrip(context.services(), screen, null,
                Strip.of(Scroll.wheel(scroll == GroupScroll.NONE ? 0 : notches), members));
    }

    /**
     * add_marker and remove_marker. An absent picture the game shows on the screen now is refused: it would not tell
     * the screen apart, it would hide it.
     */
    private static AgentReply marker(Edit edit, AgentContext context) {
        StudioServices services = context.services();
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<String> found = ScreenBook.find(graph, edit.screen());
        if (found.isEmpty()) {
            return AgentReply.refused("No screen " + edit.screen() + " in the bot's Screens. Nothing was written.");
        }
        String screen = found.get();
        if (edit instanceof RemoveMarker remove) return said(ScreenEdits.removeMarker(services, screen, remove.picture()));
        AddMarker add = (AddMarker) edit;
        String picture = has(add.picture()) ? add.picture().strip() : "";
        if (!ActTools.exists(context, picture)) return AgentReply.refused(Frames.noPicture(picture));
        boolean absent = Boolean.TRUE.equals(add.absent());
        if (absent && !ScreenFit.shownOn(context, screen, List.of(picture)).isEmpty()) {
            return AgentReply.refused(picture + " shows on " + graph.shown(screen) + " now, so it is no picture "
                    + graph.shown(screen) + " never shows. Nothing was written.");
        }
        return said(ScreenEdits.addMarker(services, screen, FxCall.call(() -> ScreenBook.picture(services, picture)),
                absent));
    }

    /**
     * add_way with no input: {@code screen} follows {@code after} by itself, a way in added unless it has it, then
     * {@code way.seconds()} as how long {@code after} gives it.
     */
    private static AgentReply onItsOwn(AddWay way, ScreenGraph graph, String screen, String after,
                                       StudioServices services) {
        boolean follows = graph.screens().get(screen).ways().stream()
                .anyMatch(w -> w.isOnItsOwn() && graph.onScreen(w).filter(after::equals).isPresent());
        List<String> said = new ArrayList<>();
        if (!follows) {
            String added = ScreenEdits.addWayOnItsOwn(services, screen, after);
            if (added.startsWith(ScreenEdits.NOT_WRITTEN)) return said(added);
            said.add(added);
        }
        String loads = ScreenEdits.setLoads(services, after, way.seconds());
        if (loads.startsWith(ScreenEdits.NOT_WRITTEN)) return said(loads);
        said.add(loads);
        return said(String.join(" ", said));
    }

    /** {@code then} on the field of {@code on} that {@code named} names; refused when it names none. */
    private static AgentReply onInput(OfInput named, ScreenGraph graph, String screen, MapScreen on,
                                      Function<String, AgentReply> then) {
        String unnamed = unnamed(named);
        if (unnamed != null) return AgentReply.refused(unnamed);
        return field(on, named).map(then).orElseGet(() -> AgentReply.refused(noInput(graph, screen, named)));
    }

    /**
     * The field of {@code on} that {@code named} names: by its picture or key, else by the field itself; a picture
     * wins, so a call by picture reads as it always did.
     */
    static Optional<String> field(MapScreen on, OfInput named) {
        return on.inputs().entrySet().stream().filter(e -> named(e.getValue(), named)).map(Map.Entry::getKey)
                .findFirst().or(() -> Optional.ofNullable(asField(named)).filter(on.inputs()::containsKey));
    }

    /** The field {@code named} may name by its picture ({@code beerBuy}); null when it names a key or a scroll. */
    private static String asField(OfInput named) {
        return has(named.picture()) && !has(named.key()) && named.scroll() == null ? named.picture().strip() : null;
    }

    private static String method(String method) {
        return method == null ? "" : method;
    }

    /**
     * set_input: the result, never or lock given (one of them, each replacing the others), whether it only tells, the
     * coming and going.
     */
    private static AgentReply setInput(AgentContext context, String screen, MapScreen on, String field,
                                       SetInput edit) {
        long settles = Stream.of(edit.did(), edit.never(), edit.locked()).filter(v -> v != null).count();
        if (settles > 1) {
            return AgentReply.refused("Give one of did, never and locked: each replaces the others. Nothing was "
                    + "written.");
        }
        boolean typed = edit.types() != null || edit.submits() != null || edit.clears() != null;
        boolean drags = edit.dragOnto() != null || edit.dragInto() != null || edit.dragMs() != null;
        boolean others = settles > 0 || edit.info() != null || edit.sometimes() != null || edit.repeated() != null
                || edit.gesture() != null || edit.holdMs() != null || typed || drags;
        if (edit.side() != null || edit.anchor() != null || edit.other() != null) {
            if (others) return AgentReply.refused("Give side, anchor and other on their own. Nothing was written.");
            // Only side unknown, alone, clears a place: an anchor with no side, or a side misspelt, writes nothing.
            boolean clears = edit.side() == Place.Side.UNKNOWN && !has(edit.anchor()) && !has(edit.other());
            if (!clears && (edit.side() == null || edit.side() == Place.Side.UNKNOWN)) {
                return AgentReply.refused("Give side (above, below, left_of, right_of, nearest, between) with anchor, "
                        + "or side unknown alone for anywhere. Nothing was written.");
            }
            return said(ScreenEdits.setPlace(context.services(), screen, field, edit.side(), edit.anchor(),
                    edit.other(), null, null));
        }
        if (!others) {
            return AgentReply.refused("Say what to set: did, info, never, locked, sometimes, repeated, gesture, "
                    + "holdMs, types, submits, clears, dragOnto, dragInto, dragMs or side. Nothing was written.");
        }
        Input in = on.inputs().get(field);
        if (edit.repeated() != null && !(in instanceof Button)) {
            return AgentReply.refused(field + " is no button: only a button has copies. Nothing was written.");
        }
        boolean gestured = edit.gesture() != null || edit.holdMs() != null;
        if (gestured && !(in instanceof Button || in instanceof Hotkey && edit.gesture() == null)) {
            return AgentReply.refused(field + " is no button" + (edit.gesture() == null ? " or hotkey" : "")
                    + ": only a button has a gesture, and a hotkey a hold. Nothing was written.");
        }
        if (edit.gesture() == Gesture.UNKNOWN || edit.holdMs() != null
                && (edit.holdMs() < 0 || edit.holdMs() > ScreenEdits.LONGEST_HOLD.toMillis())) {
            return AgentReply.refused("No such gesture, or a hold outside 0 to "
                    + ScreenEdits.LONGEST_HOLD.toMillis() + " ms. Nothing was written.");
        }
        if (edit.holdMs() != null && edit.gesture() != null && edit.gesture() != Gesture.HOLD) {
            return AgentReply.refused("holdMs goes with gesture hold, or alone. Nothing was written.");
        }
        if (typed && !(in instanceof Button)) {
            return AgentReply.refused(field + " is no button: only a button is a text field. Nothing was written.");
        }
        // Enter and Ctrl+A belong to a field: a plain button made one by them would close every way through it.
        if (edit.types() == null && typed && !(in instanceof Button b && b.isField())) {
            return AgentReply.refused(field + " is no text field yet: give types (blank: the body's text) with "
                    + "submits or clears. Nothing was written.");
        }
        if (edit.types() != null && edit.types().length() > ScreenEdits.LONGEST_TEXT) {
            return AgentReply.refused("A field types at most " + ScreenEdits.LONGEST_TEXT + " characters. Nothing was"
                    + " written.");
        }
        Optional<AgentReply> dragRefused = drags ? dragRefused(context, field, in, edit, gestured || typed)
                : Optional.empty();
        if (dragRefused.isPresent()) return dragRefused.get();
        Input now = in;
        if (edit.did() != null) {
            now = switch (edit.did()) {
                case TRIED -> ScreenEdits.untried(in).tried();
                case LEAVES -> ScreenEdits.untried(in).leaves();
                case UNTRIED -> ScreenEdits.untried(in);
            };
        }
        if (edit.never() != null) {
            now = edit.never().isBlank() ? ScreenEdits.untried(in) : ScreenEdits.untried(in).never(edit.never().strip());
        }
        if (edit.locked() != null) {
            String lock = edit.locked().strip();
            if (!lock.isEmpty() && !ActTools.exists(context, lock)) return AgentReply.refused(Frames.noPicture(lock));
            now = lock.isEmpty() ? ScreenEdits.untried(in)
                    : ScreenEdits.untried(in).locked(ScreenBook.picture(context.services(), lock));
        }
        if (edit.info() != null) now = ScreenEdits.info(now, edit.info());
        if (edit.sometimes() != null) now = ScreenEdits.sometimes(now, edit.sometimes());
        if (edit.repeated() != null && now instanceof Button b) now = b.repeats(edit.repeated());
        Duration hold = edit.holdMs() == null ? null : Duration.ofMillis(edit.holdMs());
        if (gestured && now instanceof Button b) {
            now = ScreenEdits.gestured(b, edit.gesture() == null ? Gesture.HOLD : edit.gesture(), hold);
        }
        if (hold != null && now instanceof Hotkey h) now = h.combo(h.combo().held(hold));
        if (drags && now instanceof Button b) now = dragged(context, b, edit);
        if (typed && now instanceof Button b) {
            Words text = edit.types() == null ? b.text() : edit.types().isBlank() ? null : Words.of(edit.types());
            return said(ScreenEdits.setField(context.services(), screen, field, b.typing(new Typing(text,
                    edit.submits() == null ? b.isSubmitting() : edit.submits(),
                    edit.clears() == null ? b.isClearing() : edit.clears()))));
        }
        return said(ScreenEdits.setInput(context.services(), screen, field, now));
    }

    /**
     * Why set_input's drag may not be written on {@code in}: no button, a gesture or a text with it (a drag is its own
     * press), both drops, a drop the bot lacks, a time out of bounds, or a time alone on a button not dragged.
     */
    private static Optional<AgentReply> dragRefused(AgentContext context, String field, Input in, SetInput edit,
                                                    boolean pressedElse) {
        if (!(in instanceof Button b)) {
            return Optional.of(AgentReply.refused(field + " is no button: only a button is dragged. Nothing was "
                    + "written."));
        }
        if (pressedElse) {
            return Optional.of(AgentReply.refused("A drag is its own press: give dragOnto, dragInto or dragMs without "
                    + "gesture, holdMs or types. Nothing was written."));
        }
        if (has(edit.dragOnto()) && has(edit.dragInto())) {
            return Optional.of(AgentReply.refused("Give dragOnto or dragInto, not both. Nothing was written."));
        }
        if (edit.dragMs() != null && (edit.dragMs() <= 0 || edit.dragMs() > ScreenEdits.LONGEST_HOLD.toMillis())) {
            return Optional.of(AgentReply.refused("A drag takes 1 to " + ScreenEdits.LONGEST_HOLD.toMillis()
                    + " ms. Nothing was written."));
        }
        boolean dropped = has(edit.dragOnto()) || has(edit.dragInto());
        if (edit.dragMs() != null && !dropped && (edit.dragOnto() != null || edit.dragInto() != null)) {
            return Optional.of(AgentReply.refused("A blank drop presses it again: give dragMs with a drop, or not at "
                    + "all. Nothing was written."));
        }
        if (edit.dragOnto() == null && edit.dragInto() == null && !b.isDrag()) {
            return Optional.of(AgentReply.refused(field + " is no drag yet: give dragOnto or dragInto with dragMs. "
                    + "Nothing was written."));
        }
        if (has(edit.dragOnto()) && !ActTools.exists(context, edit.dragOnto().strip())) {
            return Optional.of(AgentReply.refused(Frames.noPicture(edit.dragOnto().strip())));
        }
        if (has(edit.dragInto()) && ScreenEdits.zoneOf(context.services(), edit.dragInto().strip()).isEmpty()) {
            return Optional.of(AgentReply.refused("The bot has no zone " + edit.dragInto().strip() + " in Zones "
                    + "(save_region adds one). Nothing was written."));
        }
        return Optional.empty();
    }

    /** {@code button} dragged as set_input says, checked by {@link #dragRefused}: a blank drop presses it again. */
    private static Button dragged(AgentContext context, Button button, SetInput edit) {
        Button now = button;
        if (has(edit.dragOnto())) {
            now = now.dragsOnto(ScreenBook.picture(context.services(), edit.dragOnto().strip()));
        } else if (has(edit.dragInto())) {
            now = now.dragsInto(ScreenEdits.zoneOf(context.services(), edit.dragInto().strip()).orElseThrow());
        } else if (edit.dragOnto() != null || edit.dragInto() != null) {
            now = now.drag(null);
        }
        return edit.dragMs() == null ? now : now.dragTime(Duration.ofMillis(edit.dragMs()));
    }

    /**
     * remove_way ({@code method} null) and way_when: {@code screen}'s way in from {@code typedFrom} by the input
     * named, as the map holds it.
     */
    private static AgentReply way(OfInput named, String typedFrom, String method, ScreenGraph graph, String screen,
                                  StudioServices services) {
        Optional<String> from = ScreenBook.find(graph, typedFrom);
        if (from.isEmpty()) {
            return AgentReply.refused((has(typedFrom) ? "No screen " + typedFrom.strip() + " in the bot's "
                    + "Screens" : "Give from, the screen the way in leaves") + ". Nothing was written.");
        }
        List<Way> ways = graph.screens().get(screen).ways();
        // A way on its own is named by its screen alone.
        if (method == null && !has(named.picture()) && !has(named.key()) && ways.stream()
                .anyMatch(w -> w.isOnItsOwn() && graph.onScreen(w).filter(from.get()::equals).isPresent())) {
            return said(ScreenEdits.removeWay(services, screen, from.get(), null));
        }
        String unnamed = unnamed(named);
        if (unnamed != null) return AgentReply.refused(unnamed);
        List<InputName> leaving = ways.stream().map(w -> graph.nameOf(w.via())).flatMap(Optional::stream)
                .filter(n -> n.screen().equals(from.get())).toList();
        Optional<String> via = leaving.stream()
                .filter(n -> graph.input(n.screen(), n.field()).filter(i -> named(i, named)).isPresent())
                .map(InputName::field).findFirst()
                .or(() -> leaving.stream().map(InputName::field).filter(f -> f.equals(asField(named))).findFirst());
        if (via.isEmpty()) {
            List<String> each = ways.stream().map(w -> graph.nameOf(w.via())).flatMap(Optional::stream)
                    .map(n -> "from " + graph.shown(n.screen()) + " by " + n.field()).toList();
            return AgentReply.refused(graph.shown(screen) + " has no such way in" + (each.isEmpty() ? ""
                    : "; its ways in: " + String.join(", ", each)) + ". Nothing was written.");
        }
        return said(method == null ? ScreenEdits.removeWay(services, screen, from.get(), via.get())
                : ScreenEdits.setWayWhen(services, screen, from.get(), via.get(), method));
    }

    /** add_way: {@code screen} reached from {@code from} by the input of {@code from} named. */
    private static AgentReply addWay(AddWay edit, ScreenGraph graph, String screen, StudioServices services) {
        Optional<String> from = ScreenBook.find(graph, edit.from());
        if (from.isEmpty()) {
            return AgentReply.refused((has(edit.from()) ? "No screen " + edit.from().strip() + " in the bot's "
                    + "Screens" : "Give from, the screen the input is on") + ". Nothing was written.");
        }
        // No input, its seconds given: the screen shows by itself after from, a loading screen or a transition. With
        // no seconds either, it is an input left out, refused below.
        boolean input = has(edit.picture()) || has(edit.key()) || edit.scroll() != null;
        if (!input && edit.seconds() != null) return onItsOwn(edit, graph, screen, from.get(), services);
        if (input && edit.seconds() != null) {
            return AgentReply.refused("seconds go with no input: a way that shows by itself. Nothing was written.");
        }
        return onInput(edit, graph, from.get(), graph.screens().get(from.get()),
                field -> said(ScreenEdits.addWay(services, screen, from.get(), field)));
    }

    /** new_screen: a screen under {@code parent}, opened by the input named, put on {@code parent} when it lacks it. */
    private static AgentReply newScreen(NewScreen edit, ScreenGraph graph, String parent, AgentContext context) {
        String unnamed = unnamed(edit);
        if (unnamed != null) return AgentReply.refused(unnamed);
        Optional<String> held = field(graph.screens().get(parent), edit);
        if (held.isPresent()) {
            return said(ScreenEdits.newScreen(context.services(), parent, edit.name(), held.get(), null));
        }
        if (has(edit.key())) {
            return said(ScreenEdits.newScreen(context.services(), parent, edit.name(), null,
                    Hotkey.of(Combo.named(edit.key()).orElseThrow())));
        }
        String picture = edit.picture().strip();
        if (!ActTools.exists(context, picture)) return AgentReply.refused(Frames.noPicture(picture));
        ImageTemplate cut = ScreenBook.picture(context.services(), picture);
        return said(ScreenEdits.newScreen(context.services(), parent, edit.name(), null,
                edit.scroll() == null ? Button.of(cut) : Strip.of(Scroll.wheel(edit.scroll()), List.of(cut))));
    }

    /** Why {@code edit} names no input as try_input does, or null when it names one. */
    private static String unnamed(OfInput edit) {
        boolean byPicture = has(edit.picture());
        boolean byKey = has(edit.key());
        if (byPicture == byKey) {
            return "Name the input as try_input does: picture (with scroll for a strip) or key, one of them. "
                    + "Nothing was written.";
        }
        if (byKey && Combo.named(edit.key()).isEmpty()) return "\"" + edit.key() + "\" is no key. Nothing was written.";
        if (edit.scroll() != null && (edit.scroll() == 0 || byKey)) {
            return "A scroll is over a picture, by at least one notch. Nothing was written.";
        }
        return null;
    }

    /**
     * Whether {@code input} is the one {@code edit} names: a hotkey by its key, a button by its picture, a strip by its
     * first member and its notches.
     */
    static boolean named(Input input, OfInput edit) {
        if (has(edit.key())) {
            return input instanceof Hotkey hotkey && Combo.named(edit.key()).filter(hotkey::presses).isPresent();
        }
        String picture = edit.picture().strip();
        return switch (input) {
            case Button button -> edit.scroll() == null && button.picture() != null
                    && button.picture().id().equalsIgnoreCase(picture);
            case Strip strip -> edit.scroll() != null && !strip.members().isEmpty()
                    && strip.members().getFirst().id().equalsIgnoreCase(picture)
                    && strip.scroll() != null && strip.scroll().notches() == edit.scroll();
            // A tree is named by its field, never by a picture and a scroll.
            case Tree ignored -> false;
            case Hotkey ignored -> false;
            case TextButton ignored -> false;
        };
    }

    private static String noInput(ScreenGraph graph, String screen, OfInput edit) {
        List<String> all = graph.fields(screen);
        String asked = has(edit.key()) ? "key " + edit.key().strip() : (edit.scroll() == null ? "click " : "scroll ")
                + edit.picture().strip() + (edit.scroll() == null ? "" : " " + edit.scroll());
        return graph.shown(screen) + " has no input " + asked + (all.isEmpty() ? "" : "; its inputs: "
                + String.join(", ", all)) + ". Nothing was written.";
    }

    /** {@code screen} made a screen of {@code kind}: a screen of the tree takes off whichever marks it had. */
    private static String kind(StudioServices services, String screen, Kind kind) {
        return switch (kind) {
            case POPUP -> ScreenEdits.setPopup(services, screen, true);
            case START -> ScreenEdits.setStart(services, screen, true);
            case PANEL -> ScreenEdits.setPanel(services, screen, true);
            case SCREEN -> ScreenEdits.ofTheTree(services, screen);
        };
    }

    /** What {@link ScreenEdits} answered: refused when Studio would not write it. */
    private static AgentReply said(String answer) {
        return answer.startsWith("Not written") ? AgentReply.refused(answer) : AgentReply.text(answer);
    }

    private static boolean has(String typed) {
        return typed != null && !typed.isBlank();
    }
}
