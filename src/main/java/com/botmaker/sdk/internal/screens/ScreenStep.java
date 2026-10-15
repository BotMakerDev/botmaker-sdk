package com.botmaker.sdk.internal.screens;

import java.util.Optional;

/**
 * How a run tells Studio which screen of its map it reached: a telemetry {@code Step} whose action is
 * {@code on screen CAMPAIGN Campaign}, the constant then the name the game shows. The runtime writes it and this
 * plugin reads it back (the run bar, 🗺 Screens), so the wire learns no word of the map's. The constant is what a
 * window finds the screen by, since two screens may show the same name; the name is what the run bar shows without
 * reading the map.
 */
public final class ScreenStep {

    private static final String PREFIX = "on screen ";

    private ScreenStep() {}

    /** A screen a run reached: its constant ({@code CAMPAIGN}) and the name the game shows ({@code Campaign}). */
    public record Shown(String constant, String name) {}

    /** The step action saying the game shows the screen {@code constant}, called {@code name}; none with no constant. */
    public static String action(String constant, String name) {
        if (constant == null || constant.isBlank()) return PREFIX;
        String shown = name == null || name.isBlank() ? constant.strip() : name.strip();
        return PREFIX + constant.strip() + " " + shown;
    }

    /** The screen {@code action} says the game shows; empty for any other action. */
    public static Optional<Shown> screen(String action) {
        if (action == null || !action.startsWith(PREFIX)) return Optional.empty();
        String rest = action.substring(PREFIX.length()).strip();
        if (rest.isEmpty()) return Optional.empty();
        int space = rest.indexOf(' ');
        return Optional.of(space < 0 ? new Shown(rest, rest)
                : new Shown(rest.substring(0, space), rest.substring(space + 1).strip()));
    }
}
