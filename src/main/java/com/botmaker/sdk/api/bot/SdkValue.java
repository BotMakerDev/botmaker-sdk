package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.managed.ManagedId;
import com.botmaker.plugin.api.managed.ManagedMarker;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one of the SDK's values in your bot: a method whose returned expression BotMaker Studio's windows
 * rewrite, a class of constants they add to, rename in and remove from, or a method of a {@code @Param} group the
 * SDK declared, whose value the person running the bot sets.
 *
 * <pre>{@code
 * public final class Sdk {
 *
 *     @SdkValue(SdkValue.Id.RUN)
 *     public static Run run() {
 *         return Run.of().home(MyBot::goHome);
 *     }
 * }
 *
 * @Param
 * public class Running {
 *
 *     @SdkValue(SdkValue.Id.DO_SETUP)
 *     @Param.Shown(description = "Set the game up first")
 *     public boolean doSetup() {
 *         return true;
 *     }
 * }
 *
 * @SdkValue(SdkValue.Id.PICTURES)
 * public final class Pictures {
 *     public static final ImageTemplate COLLECT = new ImageTemplate("images/collect.png");
 * }
 * }</pre>
 *
 * <p>{@code Bot.run(Sdk.class)} reads each marked member off the class it is given and applies the value
 * before the first click. Everywhere else a marked member is ordinary Java: a misspelt {@link Id} is a
 * compile error, and a developer with no BotMaker installed reads the whole bot by opening its files.
 */
@Documented
@ManagedMarker
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface SdkValue {

    /** Which of the SDK's values this is. */
    Id value();

    /** The SDK's values, one constant each. */
    enum Id implements ManagedId<Holder> {
        /**
         * The bot's plans, what matters most as tiers of goals — a class of {@code Plan} constants, {@code Plans.java}
         * beside {@code Goals.java}.
         */
        PLANS,
        /**
         * The bot's goals, each what is wanted as a check or as an action done — a class of {@code Goal} constants,
         * {@code Goals.java}, named by the actions that serve them and the plans that order them.
         */
        GOALS,
        /**
         * The game's home and setup, which no plan owns — a {@link Run} method.
         */
        RUN,
        /** Where pixels are read from: the game's window — the {@code CaptureSource} parameter {@code captureSource}. */
        CAPTURE,
        /** The pictures the bot looks for — a class of {@code ImageTemplate} constants. */
        PICTURES,
        /** The groups 🗺 Screens ▸ 🖼 Pictures files pictures under — a class of {@code PictureTag} constants. */
        TAGS,
        /** A game font's digits as pictures, for {@code Text.readDigits} — a class of {@code DigitSet} constants. */
        DIGITS,
        /** The texts the bot types, one a text field of its map names — a class of {@code Words} constants. */
        TEXTS,
        /** An app's inputs found in the screen's tree, not by picture — a class of {@code Element} constants. */
        ELEMENTS,
        /**
         * What a button's looks mean beyond normal, selected and disabled — a class of two enums of
         * {@code ButtonState} constants, {@code Blocks} never pressed and {@code Presses} pressed.
         */
        STATES,
        /**
         * How this game shows a button changed by colour alone — dimmed, greyed, tinted — learned on one button and
         * applied to all: a {@code Shades} method, none for a bot that learned none.
         */
        SHADES,
        /**
         * The game's screens and the inputs between them, as Discovery found them — {@code Screens}, a class of
         * {@code Screen} classes each with its constant, that a run walks to reach a screen.
         */
        SCREEN_MAP,
        /**
         * The bot's screen size: the frame its pictures were cut from, which its private display is made and its
         * game window is sized to — a {@code Size} method.
         */
        SCREEN,
        /**
         * What a run runs, one of the {@code Plans} — the {@code Plan} parameter {@code plan}, set in the Runner;
         * {@code Plan.NONE} runs nothing.
         */
        PLAN,
        /** Whether a run sets the game up first — the {@code boolean} parameter {@code doSetup}. */
        DO_SETUP,
        /**
         * The launchers the bot supports, preferred first — the {@code List<Launcher>} parameter {@code launchTargets}
         * ({@code Launcher.of("steam:1145350")}), which its author lists. A run takes {@link #LAUNCH_TARGET}'s pick
         * when it is one of them, else the first one its computer's system can launch.
         */
        LAUNCH_TARGETS,
        /**
         * Which of {@link #LAUNCH_TARGETS} this run starts — the {@link LaunchChoice} parameter {@code launchTarget},
         * picked by the person running the bot; {@code LaunchChoice.of("")} for the first one this computer can launch.
         */
        LAUNCH_TARGET,
        /** The game VM the game runs in, by name — the {@link GameVm} {@code vm()}; blank for the one there is. */
        VM,
        /**
         * The game server's time zone, which a daily's reset is read in — the {@code ZoneId} parameter
         * {@code gameZone}, such as {@code ZoneId.of("Europe/Paris")}.
         */
        GAME_ZONE,
        /**
         * Words that stop a run exploring on its own from pressing what they are written beside, unless the input
         * spends — the {@code List<String>} {@code guardWords()} in {@code Sdk.java}, matched case-blind as whole words.
         * Currency signs stop it without being listed.
         */
        GUARD_WORDS,
        /*
         * How the bot clicks, looks and recovers, and where its game runs: one parameter each in the Settings group
         * (2026-11-09, the user's call; was one BotSettings method, SETTINGS). Bot.run reads them into
         * BotSettings.current(); a bot that marks none runs on BotSettings.DEFAULTS.
         */
        /** The pause after a match is found, in ms — the {@code int} parameter {@code foundDelay}. */
        FOUND_DELAY,
        /** The pause after a match is not found, in ms — the {@code int} parameter {@code notFoundDelay}. */
        NOT_FOUND_DELAY,
        /** Whether a click lands on a random point of the match — the {@code boolean} parameter {@code randomClicks}. */
        RANDOM_CLICKS,
        /** How sure a match has to be, 0..1 — the {@code double} parameter {@code confidence}. */
        CONFIDENCE,
        /** How far a good picture must beat a look-alike, 0..1 — the {@code double} parameter {@code compareMargin}. */
        COMPARE_MARGIN,
        /** The rounds a vision loop runs before the bot is stuck — the {@code int} parameter {@code maxRounds}. */
        MAX_ROUNDS,
        /** How long, in ms, a loop's round gives its action to show — the {@code int} parameter {@code patience}. */
        PATIENCE,
        /** How long, in ms, the screen may stay still while the bot acts — the {@code int} parameter {@code frozenAfter}. */
        FROZEN_AFTER,
        /** How long a plan run may act with no progress — the {@code Duration} parameter {@code stuckAfter}. */
        STUCK_AFTER,
        /** The game restarts allowed within {@link #RESTARTS_WITHIN} — the {@code int} parameter {@code restartCap}. */
        RESTART_CAP,
        /** The time {@link #RESTART_CAP} counts over — the {@code Duration} parameter {@code restartsWithin}. */
        RESTARTS_WITHIN,
        /** Where the game runs — the {@link BotSettings.Where} parameter {@code gameRunsIn}. */
        GAME_RUNS_IN,
        /** On the desktop, drive the real mouse and keyboard — the {@code boolean} parameter {@code takeOver}. */
        TAKE_OVER,
        /** Which private display hosts the game — the {@link BotSettings.DisplayBackend} parameter {@code displayBackend}. */
        DISPLAY_BACKEND,
        /** Which Linux backend delivers a take-over — the {@link BotSettings.InputBackend} parameter {@code inputBackend}. */
        INPUT_BACKEND
    }

    /**
     * The bot's classes the SDK's values live in, one constant each, named by its parts: {@code SDK} is
     * {@code Sdk.java}, {@code SCREENS} is {@code Screens.java}. Studio writes the class when a bot lacks it; a bot
     * never names this enum. {@code RUNNING}, {@code GAME} and {@code SETTINGS} are the SDK's parameter groups,
     * {@code Running.java}, {@code Game.java} and {@code Settings.java} in the bot's private {@code params/}, each a
     * category of the Parameters window.
     */
    enum Holder {
        SDK,
        RUNNING,
        GAME,
        SETTINGS,
        PLANS,
        GOALS,
        PICTURES,
        TAGS,
        DIGITS,
        TEXTS,
        ELEMENTS,
        STATES,
        SCREENS
    }
}
