package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants.InputName;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;

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

    private static final String SCREEN = "the screen edited (Home or HOME); for new_screen the one it opens from";
    private static final String PICTURE = "the input: its field (beerBuy) or picture";
    private static final String SCROLL = "with picture, a strip scrolled over it by these notches";
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
                  @Describe("the new name, or new_screen's, as the game shows it") String name) implements Edit {
    }

    @Describe("delete the screen")
    record Delete(String screen) implements Edit {
    }

    @Describe("a popup the game opens on its own, or a screen of the tree again")
    record Popup(String screen, boolean popup)
            implements Edit {
    }

    @Describe("set an input's result, info, never, lock, sometimes or repeated")
    record SetInput(String screen,
                    @Describe(value = PICTURE, optional = true) String picture,
                    @Describe(value = SCROLL, optional = true) Integer scroll,
                    @Describe(value = KEY, optional = true) String key,
                    @Describe(value = "what it does, opening nothing (one opening a new screen: new_screen)",
                            optional = true) Result did,
                    @Describe(value = "true when it only tells (help, rules): tried last",
                            optional = true) Boolean info,
                    @Describe(value = "why it is never clicked; blank to clear it", optional = true) String never,
                    @Describe(value = "the picture proving it locked; blank to clear it", optional = true)
                    String locked,
                    @Describe(value = "true when it shows only at times", optional = true) Boolean sometimes,
                    @Describe(value = "true for a button shown more than once", optional = true)
                    Boolean repeated)
            implements Edit, OfInput {
    }

    @Describe("make an input untried again")
    record ResetInput(String screen, @Describe(value = "", optional = true) String picture,
                      @Describe(value = "", optional = true) Integer scroll,
                      @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("remove an input")
    record RemoveInput(String screen, @Describe(value = "", optional = true) String picture,
                       @Describe(value = "", optional = true) Integer scroll,
                       @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("make an input the screen's way back")
    record UseAsBack(String screen, @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("its way back made an untried input again")
    record RemoveBack(String screen) implements Edit {
    }

    @Describe("remove a way in: from, and its input")
    record RemoveWay(String screen, @Describe(FROM) String from,
                     @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("add a way in: from's input leads here")
    record AddWay(String screen, String from,
                  @Describe(value = "", optional = true) String picture,
                  @Describe(value = "", optional = true) Integer scroll,
                  @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("a way in (from, and its input) taken only while method holds")
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
                 @Describe(value = "wheel notches a scroll, negative down", optional = true) Integer notches)
            implements Edit {
    }

    @Describe("a tree node's parents (lines lead to it)")
    record Link(String screen, String picture,
                @Describe(value = "their pictures; none clears them", optional = true) List<String> after)
            implements Edit {
    }

    @Describe("a look of the screen that shows only while method holds")
    record LookWhen(String screen, @Describe("the look's name") String look,
                    @Describe(value = "", optional = true) String method) implements Edit {
    }

    @Describe("add a marker (all show), or with absent one it never shows where a look-alike does; refused when "
            + "another shot shows the set")
    record AddMarker(String screen, @Describe("a picture the bot has") String picture,
                     @Describe(value = "", optional = true) Boolean absent) implements Edit {
    }

    @Describe("take a marker, or one it never shows, off")
    record RemoveMarker(String screen, String picture) implements Edit {
    }

    @Describe("a screen the map lacks, opened by an input of screen (added untried)")
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
        if (found.isEmpty()) {
            return AgentReply.refused("No screen " + edit.screen() + " in the bot's Screens. Nothing was written.");
        }
        String screen = found.get();
        MapScreen on = graph.screens().get(screen);
        return switch (edit) {
            case Rename rename -> said(ScreenEdits.rename(services, screen, rename.name()));
            case Delete ignored -> said(ScreenEdits.delete(services, screen));
            case Popup popup -> said(ScreenEdits.setPopup(services, screen, popup.popup()));
            case RemoveBack ignored -> said(ScreenEdits.removeBack(services, screen));
            case LookWhen look -> said(ScreenEdits.setLookWhen(services, screen, look.look(), method(look.method())));
            case AddWay way -> addWay(way, graph, screen, services);
            case RemoveWay way -> way(way, way.from(), null, graph, screen, services);
            case WayWhen way -> way(way, way.from(), method(way.method()), graph, screen, services);
            case NewScreen opened -> newScreen(opened, graph, screen, context);
            case AddMarker ignored -> marker(edit, context);
            case RemoveMarker ignored -> marker(edit, context);
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
        if (settles == 0 && edit.info() == null && edit.sometimes() == null && edit.repeated() == null) {
            return AgentReply.refused("Say what to set: did, info, never, locked, sometimes or repeated. Nothing "
                    + "was written.");
        }
        Input in = on.inputs().get(field);
        if (edit.repeated() != null && !(in instanceof Button)) {
            return AgentReply.refused(field + " is no button: only a button has copies. Nothing was written.");
        }
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
        return said(ScreenEdits.setInput(context.services(), screen, field, now));
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
        String unnamed = unnamed(named);
        if (unnamed != null) return AgentReply.refused(unnamed);
        List<Way> ways = graph.screens().get(screen).ways();
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
                    Hotkey.of(Key.named(edit.key()).orElseThrow())));
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
        if (byKey && Key.named(edit.key()).isEmpty()) return "\"" + edit.key() + "\" is no key. Nothing was written.";
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
            return input instanceof Hotkey hotkey && Key.named(edit.key()).filter(k -> k == hotkey.key()).isPresent();
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
        };
    }

    private static String noInput(ScreenGraph graph, String screen, OfInput edit) {
        List<String> all = graph.fields(screen);
        String asked = has(edit.key()) ? "key " + edit.key().strip() : (edit.scroll() == null ? "click " : "scroll ")
                + edit.picture().strip() + (edit.scroll() == null ? "" : " " + edit.scroll());
        return graph.shown(screen) + " has no input " + asked + (all.isEmpty() ? "" : "; its inputs: "
                + String.join(", ", all)) + ". Nothing was written.";
    }

    /** What {@link ScreenEdits} answered: refused when Studio would not write it. */
    private static AgentReply said(String answer) {
        return answer.startsWith("Not written") ? AgentReply.refused(answer) : AgentReply.text(answer);
    }

    private static boolean has(String typed) {
        return typed != null && !typed.isBlank();
    }
}
