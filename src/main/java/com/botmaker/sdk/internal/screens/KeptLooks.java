package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.internal.bot.BotStuckException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The game settings a bot needs, held at a run's start: each button of the map that {@link Button#keeps} a look is
 * pressed until it shows it, its screen walked to first (2026-11-02, the user's call: the map declares, the run fixes).
 * A popup's button is none of them: nothing walks to a popup.
 */
public final class KeptLooks {

    private KeptLooks() {}

    /** A button that keeps a look, and where it is: {@code screen}'s field {@code field}. */
    public record Kept(String screen, String field, Button button) {}

    /** Every button of {@code map} that keeps a look, screen by screen in the map's order. */
    public static List<Kept> of(ScreenGraph map) {
        List<Kept> kept = new ArrayList<>();
        for (Map.Entry<String, MapScreen> screen : map.screens().entrySet()) {
            if (screen.getValue().isPopup()) continue;
            for (Map.Entry<String, Input> input : screen.getValue().inputs().entrySet()) {
                if (input.getValue() instanceof Button button && button.keeps() != null) {
                    kept.add(new Kept(screen.getKey(), input.getKey(), button));
                }
            }
        }
        return List.copyOf(kept);
    }

    /**
     * Presses each button of {@code map} that keeps a look until it shows it, by {@code press} ({@code
     * Navigate.press}); why the run cannot go on when one never shows it, naming it, else empty.
     */
    public static Optional<String> hold(ScreenGraph map, BiFunction<Button, Pick, Pressed> press) {
        for (Kept kept : of(map)) {
            String name = map.shown(kept.screen()) + "'s " + kept.field();
            Pressed pressed;
            try {
                pressed = press.apply(kept.button(), Pick.until(kept.button().keeps()));
            } catch (BotStuckException stuck) {
                // The walk to its screen got stuck: said as the setting not held, not a start-up crash.
                return Optional.of(name + " could not be reached: " + stuck.getMessage());
            }
            if (!pressed.done()) {
                return Optional.of(name + " never showed " + kept.button().keeps().id() + " ("
                        + pressed.result().name().toLowerCase(Locale.ROOT) + "): set it in the game, or give the"
                        + " button each look it shows on the way.");
            }
        }
        return Optional.empty();
    }
}
