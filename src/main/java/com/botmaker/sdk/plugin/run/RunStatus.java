package com.botmaker.sdk.plugin.run;

import com.botmaker.sdk.internal.screens.ScreenStep;
import com.botmaker.shared.ipc.TelemetryEvent;

/**
 * What the run bar says the bot is doing: the activity it is in, the screen of its map it last reached, and the last
 * thing it did there, from the run's telemetry. A value; {@link #after} answers the next one.
 *
 * @param activity the flow's current activity, empty before the first or in a bot with no flow
 * @param screen   the screen of the bot's map a walk last reached, as the game names it; empty before the first
 * @param action   the last thing done ({@code found (93%)}, {@code clicked}), empty before the first
 * @param line     the bot's source line of that action, or {@code -1}
 */
record RunStatus(String activity, String screen, String action, int line) {

    static final RunStatus START = new RunStatus("", "", "", -1);

    /**
     * This status after {@code event}; unchanged by an event that says nothing about where the bot is. A screen
     * reached keeps what was done; a new activity keeps the screen, since the game is still on it.
     */
    RunStatus after(TelemetryEvent event) {
        return switch (event) {
            case TelemetryEvent.Step s -> ScreenStep.screen(s.action())
                    .map(reached -> new RunStatus(s.activity().isEmpty() ? activity : s.activity(), reached.name(),
                            action, line))
                    .orElseGet(() -> new RunStatus(s.activity(), screen, s.action(), s.line()));
            case TelemetryEvent.Match m -> new RunStatus(activity, screen,
                    m.found() ? "found (" + percent(m.confidence()) + ")" : "looked, not found", m.line());
            case TelemetryEvent.Click c -> new RunStatus(activity, screen, "clicked", c.line());
            case TelemetryEvent.Swipe s -> new RunStatus(activity, screen, "swiped", s.line());
            case TelemetryEvent.Region _, TelemetryEvent.Log _, TelemetryEvent.Ask _, TelemetryEvent.Answer _ -> this;
        };
    }

    /** One line for the bar: {@code Collect · 🗺 Campaign · clicked · line 42}, or empty when nothing is known yet. */
    String text() {
        StringBuilder out = new StringBuilder(activity);
        if (!screen.isEmpty()) out.append(out.isEmpty() ? "" : " · ").append("🗺 ").append(screen);
        if (!action.isEmpty()) out.append(out.isEmpty() ? "" : " · ").append(action);
        if (line > 0 && !out.isEmpty()) out.append(" · line ").append(line);
        return out.toString();
    }

    private static String percent(double confidence) {
        return Double.isFinite(confidence) ? Math.round(confidence * 100) + "%" : "?";
    }
}
