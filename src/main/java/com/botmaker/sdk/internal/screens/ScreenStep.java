package com.botmaker.sdk.internal.screens;

import java.util.Optional;

/**
 * How a run tells Studio which screen of its map it reached: a telemetry {@code Step} whose action is
 * {@code on screen Campaign}. The runtime writes it and this plugin reads it back (the run bar, 🗺 Screens), so the
 * wire learns no word of the map's.
 */
public final class ScreenStep {

    private static final String PREFIX = "on screen ";

    private ScreenStep() {}

    /** The step action saying the game shows the screen called {@code name}. */
    public static String action(String name) {
        return PREFIX + (name == null ? "" : name);
    }

    /** The screen {@code action} says the game shows; empty for any other action. */
    public static Optional<String> screen(String action) {
        if (action == null || !action.startsWith(PREFIX) || action.length() == PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(action.substring(PREFIX.length()));
    }
}
