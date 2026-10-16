package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.screens.ScreenWalk;

import java.util.Optional;

/**
 * Goes from screen to screen by the bot's map, its {@code Screens} class: {@code Navigate.to(Screens.CAMPAIGN)}
 * finds where the game is, then does each input on the way and waits for each screen's marker. A popup that shows is
 * closed on the way.
 *
 * <p>A block's screen is picked from the map ({@code ScreenEditors}); one dropped and not picked yet is {@code null},
 * which every method here reads as no screen.
 */
@Palette(category = "interaction", categoryLabel = "Interaction", icon = "🗺")
public final class Navigate {

    private Navigate() {}

    /**
     * Goes to {@code screen}. False when the way there is not open now (through an input that comes and goes, one
     * that is locked, or a way or input whose method says no), when {@code screen} is a popup or not in the map, or
     * when the run was stopped.
     *
     * @throws BotStuckException when the game is on no screen of the map, steps keep landing elsewhere, or the map's
     *                           way is no longer on the game: the run's recovery takes over
     */
    public static boolean to(GameScreen screen) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> target = map.constantOf(screen);
        if (target.isEmpty()) {
            Debug.log((screen == null ? "No screen" : screen.name()) + " is not in the bot's screen map.");
            return false;
        }
        ScreenWalk.Result walked = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, constant -> tell(map, constant))
                .go(target.get(), ScreenMaps.lastSeen());
        ScreenMaps.seen(walked.at());
        return switch (walked.outcome()) {
            case ARRIVED -> true;
            case NOT_NOW, LOCKED, STOPPED -> {
                Debug.log(walked.why());
                yield false;
            }
            case MISSING, STUCK -> throw new BotStuckException(walked.why());
        };
    }

    /** Whether the game shows {@code screen} now: its markers are on the screen, and none of its absent pictures. */
    public static boolean isOn(GameScreen screen) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> constant = map.constantOf(screen);
        if (constant.isEmpty() || !screen.hasMarker()) return false;
        RunReach reach = new RunReach();
        boolean on = reach.look().map(frame -> Markers.of(screen).on(reach, frame)).orElse(false);
        // Remembered, not told: a screen's marker stays in view under a panel opened over it, so this may be its parent.
        if (on) ScreenMaps.seen(constant.get());
        return on;
    }

    /** The screen of the bot's map the game shows now, popups aside; empty when it shows none. */
    public static Optional<GameScreen> where() {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> here = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null).where(ScreenMaps.lastSeen());
        here.ifPresent(constant -> tell(map, constant));
        return here.flatMap(map::screen);
    }

    /** Remembers the game is on {@code constant}, and tells whoever watches the run (the run bar, 🗺 Screens). */
    private static void tell(ScreenGraph map, String constant) {
        if (!constant.equals(ScreenMaps.lastSeen())) Bots.fireScreen(constant, map.shown(constant));
        ScreenMaps.seen(constant);
    }
}
