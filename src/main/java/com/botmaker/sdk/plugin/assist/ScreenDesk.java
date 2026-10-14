package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What 🗺 Screens does with the map, by the very code the assistant's tools run: its ▶ Try is try_input, its Go here
 * go_to_screen, its ✂ Cut crop_picture's note, so the window and the assistant cannot record an input two ways.
 */
public final class ScreenDesk {

    private ScreenDesk() {}

    /** The bot's map as its {@code Screens} class holds it. On the FX thread. */
    public static ScreenGraph read(StudioServices services) {
        return ScreenBook.read(services);
    }

    /**
     * Each screen's stage as its section of the notes says it ({@code surveyed}, {@code built}), by constant; a screen
     * whose section says none is left out. Reads {@code GAME.md}.
     */
    public static Map<String, String> stages(StudioServices services, ScreenGraph graph) {
        Path project = services.projectDir();
        Map<String, String> byName = new LinkedHashMap<>();
        if (project != null) {
            GameNotes.sections(project).forEach((name, text) -> byName.putIfAbsent(name.toLowerCase(Locale.ROOT), text));
        }
        Map<String, String> stages = new LinkedHashMap<>();
        for (String constant : graph.screens().keySet()) {
            Protocol.reached(byName.get(graph.shown(constant).toLowerCase(Locale.ROOT)))
                    .ifPresent(r -> stages.put(constant, r.name().toLowerCase(Locale.ROOT)));
        }
        return stages;
    }

    /** What the notes say of the screen the game calls {@code name}, without its heading; empty when nothing. */
    public static String notes(StudioServices services, String name) {
        Path project = services.projectDir();
        if (project == null) return "";
        return GameNotes.sections(project).entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }

    /**
     * Tries {@code input} on {@code constant} as try_input does, and says what it recorded. Off the FX thread: it
     * acts on the game and waits for it. Acting on the user's own desktop is allowed, since they pressed ▶ Try.
     */
    public static String tryInput(AgentContext context, String constant, Input input) {
        MapTools.Try asked = new MapTools.Try(constant,
                input.picture() == null ? null : input.picture().id(),
                input.act() == Input.Act.SCROLL ? input.notches() : null,
                input.key() == null ? null : input.key().label(),
                null, null, null,
                input.kind() == null ? null : input.kind().name().toLowerCase(Locale.ROOT),
                null, true);
        return said(MapTools.tryInput(asked, context));
    }

    /** Walks the game to {@code constant} as go_to_screen does, and says where it ended. Off the FX thread. */
    public static String goTo(AgentContext context, String constant) {
        return said(MapTools.goToScreen(new MapTools.Goal(constant, true), context));
    }

    /**
     * Notes the picture {@code picture}, just cut, on {@code constant}: its marker, or an input untried until tried.
     * Says where.
     */
    public static String noteCut(StudioServices services, String constant, String picture, boolean marker) {
        MapTools.Placing at;
        try {
            at = MapTools.placing(services, constant, marker, false);
        } catch (IllegalStateException busy) {
            return "Studio did not hand over the bot's Screens: " + busy.getMessage();
        }
        if (at.refusal() != null) return at.refusal();
        return MapTools.noteCut(services, at, picture, marker, false, false).strip();
    }

    /** A reply's text, its pictures aside. */
    private static String said(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n")).strip();
    }
}
