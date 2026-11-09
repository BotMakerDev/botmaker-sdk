package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.plugin.api.palette.ReadsScreen;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.Reach;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.screens.ScreenValues;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.trace.Say;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

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
        String from = ScreenMaps.lastSeen();
        ScreenWalk walk = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, constant -> tell(map, constant));
        ScreenWalk.Result walked = walk.uncovering().doing(DoneRecords.run()).go(target.get(), from);
        if (walked.at() == null) ScreenMaps.lost();
        else ScreenMaps.seen(walked.at());
        if (walked.arrived()) {
            // Where the activity walks back to when one of its steps gets stuck.
            ScreenMaps.reached(target.get());
            return true;
        }
        // Said here, at the bot's line that walked, at its outcome's level; the recovery from a throw says only itself.
        Map<String, String> move = move(map, from, target.get(), walked.at());
        switch (walked.outcome().level()) {
            case INFO -> Say.info(walked.why(), move);
            case WARN -> Say.warn(stuck(map, walk, target.get(), walked), move);
            case ERROR -> Say.error(walked.why(), move);
            default -> Debug.log(walked.why());
        }
        return switch (walked.outcome()) {
            case MISSING, STUCK -> throw Say.told(new BotStuckException(walked.why()));
            default -> false;
        };
    }

    /**
     * A walk that did not arrive, as its line's values say it for the SDK's reports: {@code from} and {@code to}; and,
     * when it ended on no screen of the map, {@code screen=none} with the {@code target} it was going to.
     */
    private static Map<String, String> move(ScreenGraph map, String from, String target, String at) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ScreenValues.FROM, from == null ? ScreenValues.NONE : map.shown(from));
        values.put(ScreenValues.TO, map.shown(target));
        if (at == null) {
            values.put(ScreenValues.SCREEN, ScreenValues.NONE);
            values.put(ScreenValues.TARGET, map.shown(target));
        }
        return values;
    }

    /**
     * A stuck walk as its warning says it: where to, the last screen known, the steps it took, why it stopped, and how
     * well the last screen's markers match what the game shows now.
     */
    private static String stuck(ScreenGraph map, ScreenWalk walk, String target, ScreenWalk.Result walked) {
        String seen = walk.markersSeen(walked.at());
        return "Stuck going to " + map.shown(target) + ": " + walked.why()
                + " Last known screen: " + (walked.at() == null ? "none" : map.shown(walked.at())) + "."
                + (walked.walked().isEmpty() ? "" : " Walked: " + walked.walked().stream().map(map::shown)
                        .collect(Collectors.joining(" → ")) + ".")
                + (seen.isEmpty() ? "" : " Its markers now: " + seen + ".");
    }

    /** {@link #press(Button, Pick)} of its first copy. */
    public static Pressed press(Button button) {
        return pressed(button, null);
    }

    /**
     * Presses {@code button}, an input of a screen of the map ({@code Screens.MARKET.beerBuy}): walks to its screen
     * when the game is elsewhere, as {@link #to} does, scrolls its strip to it or moves its {@link Tree tree}'s view
     * to it along the tree's offsets from a node in view, clicks the copy {@code pick} names
     * (null: the first), and waits for where the map says it leads. {@link Pick#untilNone} presses it again while the
     * game stays there, until it is gone or looks disabled: Buy until the money runs out (2026-10-17); {@link
     * Pick#until} presses a {@link Button#cycling cycling} button until it shows a look. A button not in view yet is
     * waited for a step's time first, so a press follows the one that brings it up. An input of a
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
     * Runs {@code custom}'s method on its screen, walking there first, as {@link #press(Button, Pick)} does; it is never
     * run a second time, though the map says it leads away and the game stayed.
     */
    public static Pressed press(Custom custom) {
        return pressed(custom, null);
    }

    /**
     * Presses {@code action}'s input, as its own {@code press} does, and keeps the action's done-record. A flow says
     * when: nothing here asks whether the action is due or what it needs. {@link Pressed.Result#ABSENT} for one no
     * screen of the map holds; a press that pressed nothing keeps no record.
     *
     * @throws BotStuckException as {@link #to} does, when the walk to its screen gets stuck
     */
    @Hidden("no block can pick an action yet: written in Java")
    public static Pressed press(Do action) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<BotConstants.InputName> name = map.nameOf(action);
        if (name.isEmpty()) {
            Debug.log((action == null ? "No action" : action.input().label()) + " is no action of the bot's screen map.");
            return new Pressed(Pressed.Result.ABSENT, null, 0);
        }
        DoneRecords records = DoneRecords.run();
        // The walk there leaves it alone though it is done on sight: one press, this one.
        boolean mine = records.start(action);
        try {
            Pressed pressed = pressed(action.input(), null);
            if (pressed.result() == Pressed.Result.DONE || pressed.result() == Pressed.Result.LEFT) {
                records.pressed(DoneRecords.key(name.get().screen(), name.get().field()), action,
                        pressed.result() == Pressed.Result.DONE);
            }
            return pressed;
        } finally {
            if (mine) records.finish(action);
        }
    }

    /**
     * Types {@code text} in {@code field}, a text field of the map ({@code Screens.GUILD.search}), in place of its
     * own text: walks to its screen, clicks it, clears it when it {@link Button#clears clears}, types, presses Enter
     * when it {@link Button#submits submits}, and waits for where the map says it leads, as {@link #press(Button,
     * Pick)} does. Its text is usually a {@code @Param} the person running the bot picks. {@link
     * Pressed.Result#NOT_NOW} for a button that is no text field.
     *
     * @throws BotStuckException as {@link #to} does, when the walk to its screen gets stuck
     */
    public static Pressed type(Button field, String text) {
        if (field == null) return pressed(null, null);
        return pressed(field, null, Words.of(text == null ? "" : text));
    }

    /**
     * How {@code button} looks on the game now, by its looks ({@link State#DISABLED} for a greyed-out Buy); empty when
     * it is not in view. Nothing is walked or scrolled to: it is a look at the screen as it stands.
     */
    public static Optional<ButtonState> state(Button button) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<Strip> strip = map.nameOf(button).flatMap(name -> map.screen(name.screen()))
                .flatMap(screen -> screen.stripOf(button));
        RunReach reach = new RunReach();
        return reach.look().flatMap(frame -> reach.seen(button, strip.orElse(null), Pick.first(), frame))
                .map(Reach.Seen::state);
    }

    private static Pressed pressed(Input input, Pick pick) {
        return pressed(input, pick, null);
    }

    private static Pressed pressed(Input input, Pick pick, Words typed) {
        // Refused before any walk: a field with no text to type, or text for a button that is no field.
        boolean field = input instanceof Button button && button.isField();
        if (typed == null ? field && ((Button) input).isTyped() : !field) {
            Debug.log(input.label() + (field ? " types the text its body gives: Navigate.type it."
                    : " is no text field: nothing is typed in it."));
            return new Pressed(Pressed.Result.NOT_NOW, null, 0);
        }
        ScreenGraph map = ScreenMaps.installed();
        Optional<BotConstants.InputName> name = map.nameOf(input);
        if (name.isEmpty()) {
            Debug.log((input == null ? "No input" : input.label()) + " is not in the bot's screen map.");
            return new Pressed(Pressed.Result.ABSENT, null, 0);
        }
        String constant = name.get().screen();
        if (!to(map.instance(constant).orElse(null))) return new Pressed(Pressed.Result.NOT_NOW, null, 0);
        ScreenWalk.Press press = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null)
                .press(constant, name.get().field(), pick, typed);
        if (press.at() != null) tell(map, press.at());
        if (press.result() != Pressed.Result.DONE) Debug.log(press.why());
        return new Pressed(press.result(), press.at() == null ? null : map.instance(press.at()).orElse(null),
                press.times(), press.state());
    }

    /**
     * Whether the game shows {@code screen} now: its markers are on the screen, none of its absent pictures, and no
     * panel of it over it ({@link Setup#panelOf}). Under a panel its markers stay in view, but the game is on the
     * panel: false, and the panel is where the run was last seen.
     */
    public static boolean isOn(Screen screen) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> constant = map.constantOf(screen);
        Optional<Markers> markers = constant.flatMap(map::screen).map(Markers::of).filter(Markers::any);
        if (markers.isEmpty()) return false;
        RunReach reach = new RunReach();
        Optional<BufferedImage> frame = reach.look().filter(f -> markers.get().on(reach, f));
        if (frame.isEmpty()) return false;
        Optional<String> panel = new ScreenWalk(map, reach, ScreenWalk.STEP_MS, null)
                .panelOver(constant.get(), frame.get());
        ScreenMaps.seen(panel.orElse(constant.get()));
        return panel.isEmpty();
    }

    /**
     * What {@code readout} shows now, a readout of a screen of the map ({@code Screens.HOME.gold}): the words read in
     * its zone, or the number its digits spell. Nothing is walked to: empty when the game does not show its screen, a
     * panel of it covers it ({@link Setup#panelOf}), or nothing is read there.
     */
    @Hidden("no block can pick a readout yet: Studio reads Screens.HOME.gold as an input; written in Java")
    @ReadsScreen
    public static Optional<String> read(Readout readout) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<BotConstants.InputName> name = map.nameOf(readout);
        if (name.isEmpty()) {
            Debug.log("That readout is not in the bot's screen map.");
            return Optional.empty();
        }
        return new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null)
                .read(name.get().screen(), name.get().field());
    }

    /**
     * {@link #read} as a number: the digits it shows, every other mark passed over, so {@code 1,250} is 1250 and
     * {@code 350/500} 350500; empty as for {@link #read}, or when it shows no digit.
     */
    @Hidden("as read: no block can pick a readout yet")
    @ReadsScreen
    public static OptionalLong number(Readout readout) {
        String digits = read(readout).map(text -> text.replaceAll("\\D", "")).orElse("");
        if (digits.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(digits));
        } catch (NumberFormatException tooLong) {
            return OptionalLong.empty();
        }
    }

    /**
     * Which one {@code screen} shows, read off its {@link Readout#subject() subject} readout: the hero's name on a
     * hero's page. Empty when it has none, the game does not show it, a panel of it covers it, or nothing is read.
     */
    public static Optional<String> subject(Screen screen) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> constant = map.constantOf(screen);
        Optional<String> field = constant.flatMap(map::screen).flatMap(MapScreen::subjectField);
        if (field.isEmpty()) return Optional.empty();
        return new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null).read(constant.get(), field.get());
    }

    /**
     * The screen that follows {@code from} on its own, once one shows: how a fight ends, its Victory and Defeat each a
     * screen {@code .after(FIGHT)} (2026-10-22). Waits up to {@code from}'s {@link Setup#loads}; popups that show are
     * closed. Empty when none showed by then, or the map has no screen after it: {@code switch} on what it returns.
     */
    public static Optional<Screen> next(Screen from) {
        ScreenGraph map = ScreenMaps.installed();
        Optional<String> constant = map.constantOf(from);
        if (constant.isEmpty()) {
            Debug.log((from == null ? "No screen" : from.getClass().getSimpleName())
                    + " is not in the bot's screen map.");
            return Optional.empty();
        }
        Optional<String> next = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null).next(constant.get());
        if (next.isEmpty()) Debug.log("No screen after " + map.shown(constant.get()) + " showed.");
        next.ifPresent(c -> tell(map, c));
        return next.flatMap(map::instance);
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
