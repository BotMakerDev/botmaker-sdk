package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * edit_map: the map fixed by hand, as 🗺 Screens' menus fix it, by the same {@link ScreenEdits} methods (the user's
 * call, 2026-10-15). What try_input recorded wrong (a panel taken for a way back, a result, a way in) or what the game
 * changed since is set here, nothing clicked. An input is named as try_input names it.
 */
final class MapEdits {

    private static final String SCREEN = "the screen edited, as the map names it: Home, or its constant HOME; for "
            + "new_screen the screen it is opened from";
    private static final String PICTURE = "the input, by its picture, as try_input names it";
    private static final String SCROLL = "with picture, the input that scrolls it by these notches";
    private static final String KEY = "the input, by its hotkey: Q";
    private static final String FROM = "the screen the way in leaves";
    private static final String METHOD = "a method of the bot answering true while it holds (Firestone::eventOn); "
            + "left out or blank, always";

    /** What set_input records an input did, opening nothing; untried forgets what it did. */
    enum Result {
        IN_PLACE, NONE, LEAVES, UNTRIED
    }

    /** What set_input records an input is for; none forgets it. */
    enum Purpose {
        CHORE, SHORTCUT, CHOICE, INFO, NONE
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
                  @Describe("the new name, or new_screen's: the name the game shows") String name) implements Edit {
    }

    @Describe("delete the screen")
    record Delete(String screen) implements Edit {
    }

    @Describe("make the screen a popup the game opens on its own, or a screen of the tree again")
    record Popup(String screen, @Describe("true for a popup, false for a screen of the tree") boolean popup)
            implements Edit {
    }

    @Describe("set what an input did or is for, or why it is never clicked or what proves it locked")
    record SetInput(String screen,
                    @Describe(value = PICTURE, optional = true) String picture,
                    @Describe(value = SCROLL, optional = true) Integer scroll,
                    @Describe(value = KEY, optional = true) String key,
                    @Describe(value = "what it does, opening nothing (one that opens a screen the "
                            + "map lacks: new_screen)", optional = true) Result did,
                    @Describe(value = "what it is for", optional = true) Purpose kind,
                    @Describe(value = "why it is never clicked; blank to clear it", optional = true) String never,
                    @Describe(value = "the picture proving it locked; blank to clear it", optional = true)
                    String locked,
                    @Describe(value = "true when it shows only at times", optional = true) Boolean sometimes)
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

    @Describe("its way back becomes an untried input again: a panel taken for it")
    record RemoveBack(String screen) implements Edit {
    }

    @Describe("remove a way in: from, and the input of from that opens it")
    record RemoveWay(String screen, @Describe(FROM) String from,
                     @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    @Describe("a way in (from, and the input of from that opens it) taken only while method holds")
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

    @Describe("a look of the screen that shows only while method holds")
    record LookWhen(String screen, @Describe("the screen's look, by its name") String look,
                    @Describe(value = "", optional = true) String method) implements Edit {
    }

    @Describe("add a picture the bot has to what tells the screen apart: a marker, all of which show, or with absent "
            + "true one it never shows; refused when another screen's shot shows the set too")
    record AddMarker(String screen, @Describe("a picture the bot has") String picture,
                     @Describe(value = "true for a picture the screen never shows, where a screen like it does",
                             optional = true) Boolean absent) implements Edit {
    }

    @Describe("take a picture off what tells the screen apart, a marker or one it never shows")
    record RemoveMarker(String screen, @Describe("the picture, by its name") String picture) implements Edit {
    }

    @Describe("a screen the map lacks, named as the game shows it, opened by an input of screen (added untried)")
    record NewScreen(String screen, String name,
                     @Describe(value = "", optional = true) String picture,
                     @Describe(value = "", optional = true) Integer scroll,
                     @Describe(value = "", optional = true) String key) implements Edit, OfInput {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            ToolTiming.actions("edit_map", "Fixes the bot's Screens by hand, as the 🗺 Screens window's menus do, "
                    + "when try_input recorded something wrong or the game changed; nothing is clicked", Edit.class,
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
        GameScreen on = graph.screens().get(screen);
        return switch (edit) {
            case Rename rename -> said(ScreenEdits.rename(services, screen, rename.name()));
            case Delete ignored -> said(ScreenEdits.delete(services, screen));
            case Popup popup -> said(ScreenEdits.setPopup(services, screen, popup.popup()));
            case RemoveBack ignored -> said(ScreenEdits.removeBack(services, screen));
            case LookWhen look -> said(ScreenEdits.setLookWhen(services, screen, look.look(), method(look.method())));
            case RemoveWay way -> way(way, way.from(), null, graph, screen, services);
            case WayWhen way -> way(way, way.from(), method(way.method()), graph, screen, services);
            case NewScreen opened -> newScreen(opened, graph, screen, context);
            case AddMarker ignored -> marker(edit, context);
            case RemoveMarker ignored -> marker(edit, context);
            case SetInput set -> onInput(set, graph, screen, on, in -> setInput(context, screen, in, set));
            case ResetInput reset -> onInput(reset, graph, screen, on,
                    in -> said(ScreenEdits.setInput(services, screen, in, ScreenEdits.untried(in))));
            case RemoveInput remove -> onInput(remove, graph, screen, on,
                    in -> said(ScreenEdits.setInput(services, screen, in, null)));
            case UseAsBack back -> onInput(back, graph, screen, on, in -> said(ScreenEdits.useAsBack(services, screen,
                    in)));
            case InputWhen when -> onInput(when, graph, screen, on,
                    in -> said(ScreenEdits.setInputWhen(services, screen, in, method(when.method()))));
        };
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

    /** {@code then} on the input of {@code on} that {@code named} names; refused when it names none. */
    private static AgentReply onInput(OfInput named, ScreenGraph graph, String screen, GameScreen on,
                                      Function<Input, AgentReply> then) {
        String unnamed = unnamed(named);
        if (unnamed != null) return AgentReply.refused(unnamed);
        Optional<Input> held = on.allInputs().stream().filter(i -> named(i, named)).findFirst();
        return held.map(then).orElseGet(() -> AgentReply.refused(noInput(graph, screen, named)));
    }

    private static String method(String method) {
        return method == null ? "" : method;
    }

    /** set_input: the result, never or lock given (one of them, each replacing the others), the kind, the coming and going. */
    private static AgentReply setInput(AgentContext context, String screen, Input in, SetInput edit) {
        long settles = Stream.of(edit.did(), edit.never(), edit.locked()).filter(v -> v != null).count();
        if (settles > 1) {
            return AgentReply.refused("Give one of did, never and locked: each replaces the others. Nothing was "
                    + "written.");
        }
        if (settles == 0 && edit.kind() == null && edit.sometimes() == null) {
            return AgentReply.refused("Say what to set: did, kind, never, locked or sometimes. Nothing was written.");
        }
        Input now = in;
        if (edit.did() != null) {
            now = switch (edit.did()) {
                case IN_PLACE -> ScreenEdits.untried(in).did(Did.IN_PLACE);
                case NONE -> ScreenEdits.untried(in).did(Did.NONE);
                case LEAVES -> ScreenEdits.untried(in).did(Did.LEAVES);
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
        if (edit.kind() != null) {
            now = switch (edit.kind()) {
                case CHORE -> now.kind(Kind.CHORE);
                case SHORTCUT -> now.kind(Kind.SHORTCUT);
                case CHOICE -> now.kind(Kind.CHOICE);
                case INFO -> now.kind(Kind.INFO);
                case NONE -> ScreenEdits.unkinded(now);
            };
        }
        if (edit.sometimes() != null) now = ScreenEdits.sometimes(now, edit.sometimes());
        return said(ScreenEdits.setInput(context.services(), screen, in, now));
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
        GameScreen on = graph.screens().get(screen);
        List<Way> ways = Stream.concat(Stream.ofNullable(on.from()), on.alsoFrom().stream()).toList();
        Optional<Input> via = ways.stream()
                .filter(w -> w.via() != null && named(w.via(), named))
                .filter(w -> graph.constantOf(w.from()).filter(from.get()::equals).isPresent())
                .map(Way::via).findFirst();
        if (via.isEmpty()) {
            List<String> each = ways.stream().filter(w -> w.via() != null).map(w -> "from " + graph.constantOf(
                    w.from()).map(graph::shown).orElse("?") + " by " + w.via().label()).toList();
            return AgentReply.refused(graph.shown(screen) + " has no such way in" + (each.isEmpty() ? ""
                    : "; its ways in: " + String.join(", ", each)) + ". Nothing was written.");
        }
        return said(method == null ? ScreenEdits.removeWay(services, screen, from.get(), via.get())
                : ScreenEdits.setWayWhen(services, screen, from.get(), via.get(), method));
    }

    /** new_screen: a screen under {@code parent}, opened by the input named, put on {@code parent} when it lacks it. */
    private static AgentReply newScreen(NewScreen edit, ScreenGraph graph, String parent, AgentContext context) {
        String unnamed = unnamed(edit);
        if (unnamed != null) return AgentReply.refused(unnamed);
        Optional<Input> held = graph.screens().get(parent).allInputs().stream().filter(i -> named(i, edit))
                .findFirst();
        if (held.isPresent()) return said(ScreenEdits.newScreen(context.services(), parent, edit.name(), held.get()));
        if (has(edit.key())) {
            return said(ScreenEdits.newScreen(context.services(), parent, edit.name(),
                    Input.key(Key.named(edit.key()).orElseThrow())));
        }
        String picture = edit.picture().strip();
        if (!ActTools.exists(context, picture)) return AgentReply.refused(Frames.noPicture(picture));
        ImageTemplate cut = ScreenBook.picture(context.services(), picture);
        return said(ScreenEdits.newScreen(context.services(), parent, edit.name(),
                edit.scroll() == null ? Input.click(cut) : Input.scroll(cut, edit.scroll())));
    }

    /** Why {@code edit} names no input as try_input does, or null when it names one. */
    private static String unnamed(OfInput edit) {
        boolean byPicture = has(edit.picture());
        boolean byKey = has(edit.key());
        if (byPicture == byKey) {
            return "Name the input as try_input does: picture (with scroll for a scroll) or key, one of them. "
                    + "Nothing was written.";
        }
        if (byKey && Key.named(edit.key()).isEmpty()) return "\"" + edit.key() + "\" is no key. Nothing was written.";
        if (edit.scroll() != null && (edit.scroll() == 0 || byKey)) {
            return "A scroll is over a picture, by at least one notch. Nothing was written.";
        }
        return null;
    }

    /** Whether {@code input} is the one {@code edit} names. */
    private static boolean named(Input input, OfInput edit) {
        if (has(edit.key())) {
            return input.act() == Input.Act.KEY && Key.named(edit.key()).filter(k -> k == input.key()).isPresent();
        }
        if (input.picture() == null || !input.picture().id().equalsIgnoreCase(edit.picture().strip())) return false;
        return edit.scroll() == null ? input.act() == Input.Act.CLICK
                : input.act() == Input.Act.SCROLL && input.notches() == edit.scroll();
    }

    private static String noInput(ScreenGraph graph, String screen, OfInput edit) {
        List<Input> all = graph.screens().get(screen).allInputs();
        String asked = has(edit.key()) ? "key " + edit.key().strip() : (edit.scroll() == null ? "click " : "scroll ")
                + edit.picture().strip() + (edit.scroll() == null ? "" : " " + edit.scroll());
        return graph.shown(screen) + " has no input " + asked + (all.isEmpty() ? "" : "; its inputs: "
                + ScreenGraph.labels(all)) + ". Nothing was written.";
    }

    /** What {@link ScreenEdits} answered: refused when Studio would not write it. */
    private static AgentReply said(String answer) {
        return answer.startsWith("Not written") ? AgentReply.refused(answer) : AgentReply.text(answer);
    }

    private static boolean has(String typed) {
        return typed != null && !typed.isBlank();
    }
}
