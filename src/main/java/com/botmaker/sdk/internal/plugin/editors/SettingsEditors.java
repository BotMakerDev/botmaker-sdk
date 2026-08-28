package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Editors;
import javafx.scene.Node;

/**
 * The editor for a {@code BotSettings} setter's one argument — a number that has a range, shown as that
 * range instead of as a place to type any number at all.
 *
 * <p>Every one of these settings is a number whose <em>scale</em> is the thing nobody knows. A confidence of
 * {@code 0.8} means something only once you can see where it sits between 0 and 1; a found-delay of
 * {@code 500} means something only next to the fact that it is milliseconds. A free-typed literal says
 * neither, and accepts {@code 80} for a confidence — a value the SDK will clamp and the author will never
 * find out about.
 *
 * <p><b>This file is now the table and nothing else.</b> The pill, the dialog, the spinner-or-slider
 * division, the clamping and the label all moved to {@link Editors#boundedPill} and {@link Editors#flag} on
 * 2026-08-28, because none of them knows what a confidence is — they know what a bounded number is. What
 * cannot move is which setters have a range and what each range is: that is the SDK's own knowledge about its
 * own API, and it is exactly the kind of thing the toolkit's purity rule keeps out of the toolkit.
 *
 * <p><b>The table is the dispatch.</b> {@link #bounds} is asked twice: once by {@link CallSites#BOT_SETTING}
 * to decide whether this editor applies at all, and once here to build it. A setter with no entry is not
 * claimed, so adding one is one row rather than a row and a predicate that must agree with it.
 */
final class SettingsEditors {

    private SettingsEditors() {}

    /**
     * One setting: a bounded number, or a tick.
     *
     * <p>Exactly one field is set. They are one record rather than two tables because the dispatch must have
     * a single answer to "is this setter claimed" — two tables is two answers, and the day they disagree the
     * predicate claims a slot the builder then draws nothing for.
     */
    record Setting(Editors.NumberRange range, String flagLabel) {}

    private static Setting number(String label, String prompt, String unit, boolean whole,
                                  double min, double max, double step, double fallback) {
        return new Setting(new Editors.NumberRange(label, prompt, unit, whole, min, max, step, fallback), null);
    }

    /**
     * The bounded setters, and everything known about each.
     *
     * <p>The ceiling on the two delays is ten minutes rather than {@code Integer.MAX_VALUE}: a spinner whose
     * range is the whole int is a spinner with no scale, and a bot waiting longer than that between checks is
     * not tuning a delay, it is writing a different bot.
     */
    static Setting bounds(String setter) {
        return switch (setter == null ? "" : setter) {
            case "setFoundDelay" -> number("Delay after a match",
                    "Milliseconds (≥ 0):", " ms", true, 0, 600_000, 50, 500);
            case "setNotFoundDelay" -> number("Delay after no match",
                    "Milliseconds (≥ 0):", " ms", true, 0, 600_000, 50, 200);
            case "setMaxRetryAttempts" -> number("Max stuck checks",
                    "Checks before considered stuck (≥ 1):", "", true, 1, 600_000, 1, 20);
            case "setDefaultConfidence" -> number("Match confidence",
                    "Confidence (0.0 – 1.0):", "", false, 0, 1, 0.05, 0.8);
            case "setCompareMargin" -> number("Compare margin",
                    "How far the right template must beat a look-alike (0.0 – 1.0):", "", false,
                    0, 1, 0.01, 0.05);
            case "enableRandomClicks" -> new Setting(null, "Randomize click points");
            case "enableDebugMode" -> new Setting(null, "Debug logging");
            default -> null;
        };
    }

    /** The editor for whichever setter this slot sits in. */
    static Node setting(ValueContext ctx) {
        Setting bound = bounds(ctx.asSlot() == null ? null : ctx.asSlot().enclosingMethod());
        if (bound == null) return null;
        return bound.flagLabel() != null
                ? Editors.flag(ctx, bound.flagLabel())
                : Editors.boundedPill(ctx, bound.range());
    }
}
