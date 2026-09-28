package com.botmaker.sdk.api.bot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes one of the bot's settings, and says how Studio shows it.
 *
 * <p>Nothing reads it while a bot runs. For a {@code boolean} parameter Studio draws a tick box labelled
 * {@link #label()}; for a number it draws the value within {@link #min()} and {@link #max()}, with a spinner
 * for a whole number and a slider for a fraction, so a confidence of {@code 0.8} is seen as a place between 0
 * and 1 and a delay of {@code 500} is seen as milliseconds.
 *
 * <pre>{@code
 * public BotSettings confidence(@Setting(label = "Match confidence", prompt = "Confidence (0.0 – 1.0):",
 *         max = 1, step = 0.05, fallback = 0.8) double confidence)
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Setting {

    /** What the setting is called, as a person reads it: {@code "Match confidence"}. */
    String label();

    /** The question the edit dialog asks; empty for none. Numbers only. */
    String prompt() default "";

    /** What follows the number on the pill, with its own leading space: {@code " ms"}. Numbers only. */
    String unit() default "";

    /** The smallest value offered. Numbers only. */
    double min() default 0;

    /** The largest value offered. Numbers only. */
    double max() default 1;

    /** How far one step of the spinner or slider moves. Numbers only. */
    double step() default 1;

    /** The value shown when the argument cannot be read as a number. Numbers only. */
    double fallback() default 0;
}
