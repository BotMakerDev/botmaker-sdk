package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.internal.input.Hands;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What 🗺 Screens does with the map, by the very code the assistant's tools run: its ▶ Try is try_input, its Go here
 * go_to_screen, its ✂ Cut crop_pictures' note, so the window and the assistant cannot record an input two ways.
 */
public final class ScreenDesk {

    private ScreenDesk() {}

    /** The bot's map as its {@code Screens} class holds it. On the FX thread. */
    public static ScreenGraph read(StudioServices services) {
        return ScreenBook.read(services);
    }

    /** How far each screen got ({@code surveyed}, {@code built}), by constant; a screen not surveyed is left out. */
    public static Map<String, String> stages(ScreenGraph graph) {
        Map<String, String> stages = new LinkedHashMap<>();
        graph.screens().forEach((constant, screen) -> {
            if (screen.reached() != null) stages.put(constant, screen.reached().name().toLowerCase(Locale.ROOT));
        });
        return stages;
    }

    /**
     * Tries {@code input} on {@code constant} as try_input does, and says what it recorded; {@code opens} is the name
     * the screen it opens shows, null to leave it to the picture's name. Off the FX thread: it acts on the game and
     * waits for it. Acting on the user's own desktop is allowed, since they pressed ▶ Try.
     */
    public static String tryInput(AgentContext context, String constant, Input input, String opens) {
        return tryInput(context, constant, input, new Trying(opens, null, null, true));
    }

    /**
     * How ▶ Try tries an input, as try_input's own settings: {@code opens} the name the screen it opens shows (null:
     * the picture's name), {@code match} the copy clicked from 1 in reading order (null: its best match; the button is
     * then repeated), {@code settleMs} the longest it is given to answer (null: try_input's), and whether the game
     * {@code stays} on the screen once it is finished, or is walked to the next screen to explore.
     */
    public record Trying(String opens, Integer match, Integer settleMs, boolean stays) {}

    /** How long try_input gives an input to answer when it is not told, and the longest it may be given. */
    public static final int SETTLE_MS = ActTools.SETTLE_MS;
    public static final int LONGEST_SETTLE_MS = ActTools.LONGEST_SETTLE_MS;

    /** Tries {@code input} on {@code constant} as try_input does, {@code how} it says. Off the FX thread. */
    public static String tryInput(AgentContext context, String constant, Input input, Trying how) {
        String opens = how.opens();
        if (input instanceof Strip strip && strip.scroll() != null && strip.scroll().drag() != null) {
            return "try_input scrolls a strip by the wheel only: a dragged one is scrolled by a run.";
        }
        // A tree is measured toward its end, as try_input scrolls over a node of it with negative notches.
        if (input instanceof Tree tree) return measureTree(context, constant, tree, false);
        String picture = switch (input) {
            case Button button -> button.picture() == null ? null : button.picture().id();
            // Scrolled over a member that shows, wherever the strip carried it, as try_input scrolls one by its field.
            case Strip strip -> strip.members().isEmpty() ? null : MapTools.shownMember(strip, context);
            case Tree ignored -> null;
            case Hotkey ignored -> null;
            // Words have no picture: try_input presses them by their field.
            case TextButton words -> FxCall.call(() -> ScreenBook.read(context.services())).screen(constant)
                    .flatMap(on -> on.inputs().entrySet().stream().filter(e -> e.getValue().equals(words))
                            .map(Map.Entry::getKey).findFirst()).orElse(null);
        };
        Integer notches = input instanceof Strip strip && strip.scroll() != null ? strip.scroll().notches() : null;
        String key = input instanceof Hotkey hotkey && hotkey.combo() != null ? hotkey.combo().caps() : null;
        MapTools.Try asked = new MapTools.Try(constant, List.of(new MapTools.Step(picture, notches,
                key == null ? how.match() : null, key, opens, null, null, input.isInfo() ? true : null)),
                how.settleMs(), how.stays(), true);
        return text(MapTools.tryInput(asked, context));
    }

    /**
     * Measures {@code constant}'s {@code tree} as try_input does over one of its nodes that shows: the offsets of its
     * linked pairs in view, then its view moved once toward its start (left or above) or its end, and measured again.
     * Off the FX thread.
     */
    public static String measureTree(AgentContext context, String constant, Tree tree, boolean towardStart) {
        if (tree.members().isEmpty()) return ScreenEdits.NOT_WRITTEN + "the tree has no node to scroll over.";
        // The sign says which way only: a wheel tree turns by its own notches.
        MapTools.Try asked = new MapTools.Try(constant, List.of(new MapTools.Step(MapTools.shownNode(tree, context),
                towardStart ? 1 : -1, null, null, null, null, null, null)), null, true, true);
        return text(MapTools.tryInput(asked, context));
    }

    /**
     * Whether ▶ Try asks for the name of the screen {@code input} of {@code constant} may open: a hotkey names none,
     * and a picture whose name names no new screen ({@code close_x}, or a screen the map has) is refused without one.
     */
    public static boolean asksWhatItOpens(ScreenGraph graph, String constant, Input input) {
        return MapTools.asksWhatItOpens(graph, constant, input);
    }

    /** Walks the game to {@code constant} as go_to_screen does, and says where it ended. Off the FX thread. */
    public static String goTo(AgentContext context, String constant) {
        return text(MapTools.goToScreen(new MapTools.Goal(constant, true), context));
    }

    /**
     * Notes the picture {@code picture}, just cut, on {@code constant}: one more of its markers (the first, for a
     * screen with none), or an input untried until tried. Says where; a set another screen's shot shows too is put back.
     */
    public static String noteCut(StudioServices services, String constant, String picture, boolean marker) {
        MapTools.Placing at;
        try {
            at = MapTools.placing(services, constant, marker, false);
        } catch (IllegalStateException busy) {
            return "Studio did not hand over the bot's Screens: " + busy.getMessage();
        }
        if (at.refusal() != null) return at.refusal();
        Optional<Markers> before = marker ? MapTools.markers(services, constant) : Optional.empty();
        String noted = MapTools.noteCut(services, at, picture, marker ? MapTools.Mark.ADD : null, false, false);
        return (noted + before.map(b -> MapTools.unique(services, constant, b)).orElse("")).strip();
    }

    /** Which screen of the map a frame shows, by its markers, and the popup over it; each null for none. */
    public record Here(String screen, String popup) {}

    /**
     * What the game shows now, as the look tools read it and in their pixels: the private display, the emulator, else
     * the screen Studio watches. Lossless. Off the FX thread: it reads the game.
     */
    public static Optional<BufferedImage> look(AgentContext context) {
        return Frames.look(context);
    }

    /**
     * {@link #look} for many frames when it reads elsewhere than the context's capture source, the private display or
     * the emulator: where it reads is found once, each call of the answer reads one frame there. Empty for the screen
     * the context watches, which a live feed grabs itself. A live feed asks again now and then, so a private display
     * brought up since is read. Off the FX thread.
     */
    public static Optional<Supplier<Optional<BufferedImage>>> away(AgentContext context) {
        return Frames.away(context);
    }

    /** Which screen {@code frame} shows, as which_screen reads it first; empty with no map. Off the FX thread. */
    public static Optional<Here> here(AgentContext context, BufferedImage frame) {
        return MapTools.here(context, frame);
    }

    /** The hands for where {@link #look} reads: the click, key and scroll tools' own. */
    public static Hands hands(AgentContext context) {
        return ActTools.hands(context);
    }

    /** The screen {@code typed} names, by its constant or its name; empty when the map has none. */
    public static Optional<String> find(ScreenGraph graph, String typed) {
        return ScreenBook.find(graph, typed);
    }

    /** Saves {@code frame} as {@code constant}'s shot, over the one it had. Off the FX thread: it writes a file. */
    public static void keepShot(StudioServices services, String constant, BufferedImage frame) {
        ScreenShots.save(services.resourcesDir(), constant, frame);
    }

    /** A reply's text, its pictures aside: what the window says of a tool it ran. */
    static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n")).strip();
    }
}
