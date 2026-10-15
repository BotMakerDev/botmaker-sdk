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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * edit_map: the map fixed by hand, as 🗺 Screens' menus fix it, by the same {@link ScreenEdits} methods (the user's
 * call, 2026-10-15). What try_input recorded wrong (a panel taken for a way back, a result, a way in) or what the game
 * changed since is set here, nothing clicked. An input is named as try_input names it.
 */
final class MapEdits {

    /** What edit_map does to the screen. */
    enum Action {
        RENAME, DELETE, POPUP, SET_INPUT, RESET_INPUT, REMOVE_INPUT, USE_AS_BACK, REMOVE_BACK, REMOVE_WAY, WAY_WHEN,
        INPUT_WHEN, LOOK_WHEN, NEW_SCREEN, UNKNOWN;

        /** How the tool takes it: {@code set_input}. */
        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Action fromId(String id) {
            if (id == null) return UNKNOWN;
            String wanted = id.strip();
            return Arrays.stream(values()).filter(a -> a != UNKNOWN && a.id().equalsIgnoreCase(wanted)).findFirst()
                    .orElse(UNKNOWN);
        }

        static String ids() {
            return String.join(", ", Arrays.stream(values()).filter(a -> a != UNKNOWN).map(Action::id).toList());
        }
    }

    record Edit(@Describe("the screen edited, as the map names it: Home, or its constant HOME; for new_screen the "
                        + "screen it is opened from") String screen,
                @Describe("what to do: rename, delete, popup, set_input, reset_input, remove_input, use_as_back, "
                        + "remove_back, remove_way, way_when, input_when, look_when or new_screen") String action,
                @Describe(value = "rename's new name, or new_screen's: the name the game shows", optional = true)
                String name,
                @Describe(value = "the input, by its picture, as try_input names it", optional = true) String picture,
                @Describe(value = "with picture, the input that scrolls it by these notches", optional = true)
                Integer scroll,
                @Describe(value = "the input, by its hotkey: Q", optional = true) String key,
                @Describe(value = "remove_way and way_when: the screen the way in leaves", optional = true)
                String from,
                @Describe(value = "look_when: the screen's look, by its name", optional = true) String look,
                @Describe(value = "way_when, input_when and look_when: a method of the bot answering true while it "
                        + "holds (Firestone::eventOn); left out or blank, always", optional = true) String method,
                @Describe(value = "set_input: what it does, opening nothing: in_place, none, leaves, or untried",
                        optional = true) String did,
                @Describe(value = "set_input: what it is for: chore, shortcut, choice, info, or none",
                        optional = true) String kind,
                @Describe(value = "set_input: why it is never clicked; blank to clear it", optional = true)
                String never,
                @Describe(value = "set_input: the picture proving it locked; blank to clear it", optional = true)
                String locked,
                @Describe(value = "set_input: true when it shows only at times", optional = true) Boolean sometimes,
                @Describe(value = "popup: true makes the screen a popup the game opens on its own, false a screen of "
                        + "the tree again", optional = true) Boolean popup) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("edit_map")
                    .describedAs("Fixes the bot's Screens by hand, as the 🗺 Screens window's menus do, when "
                            + "try_input recorded something wrong or the game changed; nothing is clicked. action: "
                            + "rename (name); delete; popup (popup); set_input (the input, then did, kind, never, "
                            + "locked or sometimes); reset_input (untried again); remove_input; use_as_back; "
                            + "remove_back (its way back becomes an untried input again: a panel taken for it); "
                            + "remove_way (from, and the input of from that opens it); way_when (from, the input, "
                            + "method); input_when (the input, method); look_when (look, method); new_screen (name, "
                            + "and the input of screen that opens it, added untried). An input is named as try_input "
                            + "names it: picture, with scroll for a scroll, or key")
                    .takes(Edit.class).handledBy(MapEdits::edit));

    private MapEdits() {}

    static AgentReply edit(Edit edit, AgentContext context) {
        if (context.services().projectDir() == null) return AgentReply.refused(Frames.noProject());
        Action action = Action.fromId(edit.action());
        if (action == Action.UNKNOWN) {
            return AgentReply.refused("No action \"" + edit.action() + "\": " + Action.ids() + ". Nothing was written.");
        }
        try {
            return FxCall.call(() -> {
                ScreenGraph before = ScreenBook.read(context.services());
                AgentReply reply = edited(action, edit, context);
                // What ScreenEdits said without writing (a name taken, no such way) is a refusal: the map is as it was.
                return reply.isRefused() || !before.screens().equals(ScreenBook.read(context.services()).screens())
                        ? reply : AgentReply.refused(ScreenDesk.text(reply) + " Nothing was written.");
            });
        } catch (IllegalStateException busy) {
            return AgentReply.refused("Studio did not answer while editing the bot's Screens: " + busy.getMessage()
                    + " Read the map (read_map) before trying again.");
        }
    }

    /** {@code action} done on the FX thread. */
    private static AgentReply edited(Action action, Edit edit, AgentContext context) {
        StudioServices services = context.services();
        ScreenGraph graph = ScreenBook.read(services);
        Optional<String> found = ScreenBook.find(graph, edit.screen());
        if (found.isEmpty()) {
            return AgentReply.refused("No screen " + edit.screen() + " in the bot's Screens. Nothing was written.");
        }
        String screen = found.get();
        GameScreen on = graph.screens().get(screen);
        String method = edit.method() == null ? "" : edit.method();
        return switch (action) {
            case RENAME -> said(ScreenEdits.rename(services, screen, edit.name()));
            case DELETE -> said(ScreenEdits.delete(services, screen));
            case POPUP -> edit.popup() == null ? AgentReply.refused("Give popup: true or false. Nothing was written.")
                    : said(ScreenEdits.setPopup(services, screen, edit.popup()));
            case REMOVE_BACK -> said(ScreenEdits.removeBack(services, screen));
            case LOOK_WHEN -> said(ScreenEdits.setLookWhen(services, screen, edit.look(), method));
            case REMOVE_WAY, WAY_WHEN -> way(action, edit, graph, screen, method, services);
            case NEW_SCREEN -> newScreen(edit, graph, screen, context);
            case SET_INPUT, RESET_INPUT, REMOVE_INPUT, USE_AS_BACK, INPUT_WHEN -> {
                String unnamed = unnamed(edit);
                if (unnamed != null) yield AgentReply.refused(unnamed);
                Optional<Input> held = on.allInputs().stream().filter(i -> named(i, edit)).findFirst();
                if (held.isEmpty()) yield AgentReply.refused(noInput(graph, screen, edit));
                Input in = held.get();
                yield switch (action) {
                    case SET_INPUT -> setInput(context, screen, in, edit);
                    case RESET_INPUT -> said(ScreenEdits.setInput(services, screen, in, ScreenEdits.untried(in)));
                    case REMOVE_INPUT -> said(ScreenEdits.setInput(services, screen, in, null));
                    case USE_AS_BACK -> said(ScreenEdits.useAsBack(services, screen, in));
                    default -> said(ScreenEdits.setInputWhen(services, screen, in, method));
                };
            }
            case UNKNOWN -> AgentReply.refused("No action: " + Action.ids() + ".");
        };
    }

    /** set_input: the result, never or lock given (one of them, each replacing the others), the kind, the coming and going. */
    private static AgentReply setInput(AgentContext context, String screen, Input in, Edit edit) {
        long settles = Stream.of(edit.did(), edit.never(), edit.locked()).filter(v -> v != null).count();
        if (settles > 1) {
            return AgentReply.refused("Give one of did, never and locked: each replaces the others. Nothing was "
                    + "written.");
        }
        if (settles == 0 && !has(edit.kind()) && edit.sometimes() == null) {
            return AgentReply.refused("Say what to set: did, kind, never, locked or sometimes. Nothing was written.");
        }
        Input now = in;
        if (edit.did() != null) {
            String typed = edit.did().strip();
            Optional<Did> did = Arrays.stream(Did.values()).filter(d -> d.name().equalsIgnoreCase(typed)).findFirst();
            if (did.isEmpty() && !typed.equalsIgnoreCase("untried")) {
                return AgentReply.refused("No result \"" + typed + "\": in_place, none, leaves or untried. An input "
                        + "that opens a screen the map lacks: new_screen. Nothing was written.");
            }
            now = did.map(ScreenEdits.untried(in)::did).orElse(ScreenEdits.untried(in));
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
        if (has(edit.kind())) {
            String typed = edit.kind().strip();
            Optional<Kind> kind = MapTools.kindNamed(typed);
            if (kind.isEmpty() && !typed.equalsIgnoreCase("none")) {
                return AgentReply.refused("No kind \"" + typed + "\": chore, shortcut, choice, info or none. Nothing "
                        + "was written.");
            }
            now = kind.map(now::kind).orElse(ScreenEdits.unkinded(now));
        }
        if (edit.sometimes() != null) now = ScreenEdits.sometimes(now, edit.sometimes());
        return said(ScreenEdits.setInput(context.services(), screen, in, now));
    }

    /** remove_way and way_when: {@code screen}'s way in from {@code from} by the input named, as the map holds it. */
    private static AgentReply way(Action action, Edit edit, ScreenGraph graph, String screen, String method,
                                  StudioServices services) {
        Optional<String> from = ScreenBook.find(graph, edit.from());
        if (from.isEmpty()) {
            return AgentReply.refused((has(edit.from()) ? "No screen " + edit.from().strip() + " in the bot's "
                    + "Screens" : "Give from, the screen the way in leaves") + ". Nothing was written.");
        }
        String unnamed = unnamed(edit);
        if (unnamed != null) return AgentReply.refused(unnamed);
        GameScreen on = graph.screens().get(screen);
        List<Way> ways = Stream.concat(Stream.ofNullable(on.from()), on.alsoFrom().stream()).toList();
        Optional<Input> via = ways.stream()
                .filter(w -> w.via() != null && named(w.via(), edit))
                .filter(w -> graph.constantOf(w.from()).filter(from.get()::equals).isPresent())
                .map(Way::via).findFirst();
        if (via.isEmpty()) {
            List<String> each = ways.stream().filter(w -> w.via() != null).map(w -> "from " + graph.constantOf(
                    w.from()).map(graph::shown).orElse("?") + " by " + w.via().label()).toList();
            return AgentReply.refused(graph.shown(screen) + " has no such way in" + (each.isEmpty() ? ""
                    : "; its ways in: " + String.join(", ", each)) + ". Nothing was written.");
        }
        return said(action == Action.REMOVE_WAY ? ScreenEdits.removeWay(services, screen, from.get(), via.get())
                : ScreenEdits.setWayWhen(services, screen, from.get(), via.get(), method));
    }

    /** new_screen: a screen under {@code parent}, opened by the input named, put on {@code parent} when it lacks it. */
    private static AgentReply newScreen(Edit edit, ScreenGraph graph, String parent, AgentContext context) {
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
    private static String unnamed(Edit edit) {
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
    private static boolean named(Input input, Edit edit) {
        if (has(edit.key())) {
            return input.act() == Input.Act.KEY && Key.named(edit.key()).filter(k -> k == input.key()).isPresent();
        }
        if (input.picture() == null || !input.picture().id().equalsIgnoreCase(edit.picture().strip())) return false;
        return edit.scroll() == null ? input.act() == Input.Act.CLICK
                : input.act() == Input.Act.SCROLL && input.notches() == edit.scroll();
    }

    private static String noInput(ScreenGraph graph, String screen, Edit edit) {
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
