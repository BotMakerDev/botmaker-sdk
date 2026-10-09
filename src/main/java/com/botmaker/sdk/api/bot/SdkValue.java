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
 *     @SdkValue(SdkValue.Id.FLOW)
 *     public static Flow flow() {
 *         return Flow.of(…);
 *     }
 * }
 *
 * @SdkValue(SdkValue.Id.PICTURES)
 * public final class Pictures {
 *     public static final ImageTemplate COLLECT = new ImageTemplate("src/main/resources/images/collect.png");
 * }
 * }</pre>
 *
 * <p>{@code Bot.run(…, Sdk.class)} reads each marked method off the class it is given and applies the value
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
        /** The activity flow: what runs, in what order, and what stops it — a {@code Flow} method. */
        FLOW,
        /** Where the Activity Flow's cards sit — a {@code FlowLayout} method a run ignores. */
        FLOW_LAYOUT,
        /** Where pixels are read from — a {@code CaptureSource} method. */
        CAPTURE,
        /** How the bot clicks and looks — a {@link BotSettings} method. */
        SETTINGS,
        /** The pictures the bot looks for — a class of {@code ImageTemplate} constants. */
        PICTURES,
        /** The cards of the Activity Flow — a class of {@code Activity} constants. */
        ACTIVITIES,
        /** What activities report — a class of {@link Outcome} constants. */
        OUTCOMES,
        /** Named spots — a class of {@code Point} constants. */
        POINTS,
        /** Named areas — a class of {@code Rect} constants. */
        REGIONS
    }
}
