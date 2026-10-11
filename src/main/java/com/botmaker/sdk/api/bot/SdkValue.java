package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.managed.ManagedMarker;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one of the SDK's values in your bot: a method whose returned expression BotMaker Studio's windows
 * rewrite, or a class of constants they add to, rename in and remove from.
 *
 * <pre>{@code
 * public final class Sdk {
 *
 *     @SdkValue(SdkValue.Id.RUN)
 *     public static Run run() {
 *         return Run.of(Flows.MAIN).home(Gamebot::goHome);
 *     }
 * }
 *
 * @SdkValue(SdkValue.Id.PICTURES)
 * public final class Pictures {
 *     public static final ImageTemplate COLLECT = new ImageTemplate("src/main/resources/images/collect.png");
 * }
 * }</pre>
 *
 * <p>{@code Bot.runFlow(Sdk.class)} reads each marked method off the class it is given and applies the value
 * before the first click. Everywhere else a marked member is ordinary Java: a misspelt {@link Id} is a
 * compile error, and a developer with no BotMaker installed reads the whole bot by opening its files.
 */
@Documented
@ManagedMarker
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SdkValue {

    /** Which of the SDK's values this is. */
    Id value();

    /** The SDK's values, one constant each. */
    enum Id {
        /**
         * The bot's activity flows, one per way of playing: what runs, in what order, and what stops it — a class
         * of {@code Flow} constants.
         */
        FLOWS,
        /**
         * Which flow runs when nobody picks one, and the game's home and setup, which no flow owns — a {@code Run}
         * method.
         */
        RUN,
        /** Where each flow's cards sit in the Activity Flow — a {@code FlowLayout} method a run ignores. */
        FLOW_LAYOUT,
        /** Where pixels are read from — a {@code CaptureSource} method. */
        CAPTURE,
        /** How the bot clicks and looks — a {@link BotSettings} method. */
        SETTINGS,
        /** The pictures the bot looks for — a class of {@code ImageTemplate} constants. */
        PICTURES,
        /** The cards of the Activity Flow — a class of enums, one per activity, whose {@link Outcome} constants are its exits. */
        ACTIVITIES,
        /** Retired 2026-10-11: named spots as desktop {@code Point}s, which a bot's upgrade turns into {@link #SPOTS}. */
        POINTS,
        /** Retired 2026-10-11: named areas as source {@code Rect}s, which a bot's upgrade turns into {@link #ZONES}. */
        REGIONS,
        /** Named spots of the game, in the bot's pixels — a class of {@code Spot} constants. */
        SPOTS,
        /** Named areas of the game, in the bot's pixels — a class of {@code Zone} constants. */
        ZONES,
        /** A game font's digits as pictures, for {@code Text.readDigits} — a class of {@code DigitSet} constants. */
        DIGITS,
        /**
         * The bot's screen size: the frame its pictures were cut from, which its private display is made and its
         * game window is sized to — a {@code Size} method.
         */
        SCREEN
    }
}
