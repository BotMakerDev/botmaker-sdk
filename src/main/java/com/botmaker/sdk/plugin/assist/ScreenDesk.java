package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        MapTools.Try asked = new MapTools.Try(constant, List.of(new MapTools.Step(
                input.picture() == null ? null : input.picture().id(),
                input.act() == Input.Act.SCROLL ? input.notches() : null,
                input.key() == null ? null : input.key().label(),
                opens, null, null, input.kind())), null, true, true);
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

    /** A reply's text, its pictures aside: what the window says of a tool it ran. */
    static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n")).strip();
    }
}
