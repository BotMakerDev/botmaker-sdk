package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.Reach;
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
 * <p>An activity runs on the map (2026-10-16): its body opens with {@code if (!Navigate.to(Screens.TAVERN)) return
 * Activities.Tavern.NOT_NOW;}, then does its chores by pressing the screen's own inputs, {@code
 * Navigate.press(Screens.MARKET.beerBuy, Pick.untilNone())}, never by picture chains (2026-10-17). A press walks to
 * its input's screen first, so a chore that leaves the screen walks back by the map, never by hand. When a step of the
 * activity gets stuck, the run walks back to the screen its last {@code Navigate.to} reached and runs the activity
 * once more, before the run's home.
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
    public static boolean to(Screen screen) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> target = map.constantOf(screen);
        if (target.isEmpty()) {
            Debug.log((screen == null ? "No screen" : screen.getClass().getSimpleName())
                    + " is not in the bot's screen map.");
            return false;
        }
        // Forgotten while walking: a walk that gets stuck is no step to walk back from to where it set out.
        ScreenMaps.reached(null);
        ScreenWalk.Result walked = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, constant -> tell(map, constant))
                .uncovering().go(target.get(), ScreenMaps.lastSeen());
        ScreenMaps.seen(walked.at());
        return switch (walked.outcome()) {
            case ARRIVED -> {
                // Where the activity walks back to when one of its steps gets stuck.
                ScreenMaps.reached(target.get());
                yield true;
            }
            case NOT_NOW, LOCKED, STOPPED -> {
                Debug.log(walked.why());
                yield false;
            }
            case MISSING, STUCK -> throw new BotStuckException(walked.why());
        };
    }

    /** {@link #press(Button, Pick)} of its first copy. */
    public static Pressed press(Button button) {
        return pressed(button, null);
    }

    /**
     * Presses {@code button}, an input of a screen of the map ({@code Screens.MARKET.beerBuy}): walks to its screen
     * when the game is elsewhere, as {@link #to} does, scrolls its strip to it, clicks the copy {@code pick} names
     * (null: the first), and waits for where the map says it leads. {@link Pick#untilNone} presses it again while the
     * game stays there, until it is gone or looks disabled: Buy until the money runs out (2026-10-17). An input of a
     * popup is {@link Pressed.Result#NOT_NOW}: nothing walks to a popup, and the run closes the ones it shows.
     *
     * @throws BotStuckException as {@link #to} does, when the walk to its screen gets stuck
     */
    public static Pressed press(Button button, Pick pick) {
        return pressed(button, pick);
    }

    /** Presses {@code hotkey} on its screen, walking there first, as {@link #press(Button, Pick)} does. */
    public static Pressed press(Hotkey hotkey) {
        return pressed(hotkey, null);
    }

    /**
     * How {@code button} looks on the game now, by its looks ({@link State#DISABLED} for a greyed-out Buy); empty when
     * it is not in view. Nothing is walked or scrolled to: it is a look at the screen as it stands.
     */
    public static Optional<State> state(Button button) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<Strip> strip = map.nameOf(button).flatMap(name -> map.screen(name.screen()))
                .flatMap(screen -> screen.stripOf(button));
        RunReach reach = new RunReach();
        return reach.look().flatMap(frame -> reach.seen(button, strip.orElse(null), Pick.first(), frame))
                .map(Reach.Seen::state);
    }

    private static Pressed pressed(Input input, Pick pick) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<BotConstants.InputName> name = map.nameOf(input);
        if (name.isEmpty()) {
            Debug.log((input == null ? "No input" : input.label()) + " is not in the bot's screen map.");
            return new Pressed(Pressed.Result.ABSENT, null, 0);
        }
        String constant = name.get().screen();
        if (!to(map.instance(constant).orElse(null))) return new Pressed(Pressed.Result.NOT_NOW, null, 0);
        ScreenWalk.Press press = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null)
                .press(constant, name.get().field(), pick);
        if (press.at() != null) tell(map, press.at());
        if (press.result() != Pressed.Result.DONE) Debug.log(press.why());
        return new Pressed(press.result(), press.at() == null ? null : map.instance(press.at()).orElse(null),
                press.times());
    }

    /** Whether the game shows {@code screen} now: its markers are on the screen, and none of its absent pictures. */
    public static boolean isOn(Screen screen) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> constant = map.constantOf(screen);
        Optional<Markers> markers = constant.flatMap(map::screen).map(Markers::of).filter(Markers::any);
        if (markers.isEmpty()) return false;
        RunReach reach = new RunReach();
        boolean on = reach.look().map(frame -> markers.get().on(reach, frame)).orElse(false);
        // Remembered, not told: a screen's marker stays in view under a panel opened over it, so this may be its parent.
        if (on) ScreenMaps.seen(constant.get());
        return on;
    }

    /** The screen of the bot's map the game shows now, popups aside; empty when it shows none. */
    public static Optional<Screen> where() {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> here = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null).where(ScreenMaps.lastSeen());
        here.ifPresent(constant -> tell(map, constant));
        return here.flatMap(map::instance);
    }

    /** Remembers the game is on {@code constant}, and tells whoever watches the run (the run bar, 🗺 Screens). */
    private static void tell(ScreenGraph map, String constant) {
        if (!constant.equals(ScreenMaps.lastSeen())) Bots.fireScreen(constant, map.shown(constant));
        ScreenMaps.seen(constant);
    }
}
