package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.sdk.api.bot.BotSettings;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Optional;

/**
 * How each {@link BotSettings} setter's argument is shown: what it is called and the range it lives in.
 *
 * <p>A confidence and a delay are both a bare number, so the type cannot say that one is a place between 0 and 1
 * and the other is milliseconds. This table says it, keyed by the setter as a method reference, so renaming one
 * is a compile error here rather than a setting that quietly falls back to a free-typed number. It is not an
 * annotation on the parameter: an editor's hint is the plugin's business, not the API's. No JavaFX here: the
 * host asks {@link #claims} headless.
 */
public final class SettingHints {

    /**
     * One setting's presentation. {@code prompt}, {@code unit}, {@code min}, {@code max}, {@code step} and
     * {@code fallback} are for numbers; a flag reads only its label.
     */
    public record Hint(String label, String prompt, String unit, double min, double max, double step,
                       double fallback) {

        static Hint flag(String label) {
            return new Hint(label, "", "", 0, 1, 1, 0);
        }
    }

    // The ceiling on the two delays is ten minutes rather than Integer.MAX_VALUE: a spinner whose range is the
    // whole int has no scale, and a bot waiting longer than that between checks is writing a different bot.
    private static final Map<Executable, Hint> HINTS = Map.of(
            whole(BotSettings::foundDelay), new Hint("Delay after a match", "Milliseconds (≥ 0):", " ms",
                    0, 600_000, 50, 500),
            whole(BotSettings::notFoundDelay), new Hint("Delay after no match", "Milliseconds (≥ 0):", " ms",
                    0, 600_000, 50, 200),
            flag(BotSettings::randomizeClicks), Hint.flag("Randomize click points"),
            fraction(BotSettings::confidence), new Hint("Match confidence", "Confidence (0.0 – 1.0):", "",
                    0, 1, 0.05, 0.8),
            fraction(BotSettings::compareMargin), new Hint("Compare margin",
                    "How far the right template must beat a look-alike (0.0 – 1.0):", "", 0, 1, 0.01, 0.05),
            whole(BotSettings::maxRetryAttempts), new Hint("Max stuck checks",
                    "Checks before considered stuck (≥ 1):", "", 1, 600_000, 1, 20),
            flag(BotSettings::takeOver), Hint.flag("Take over the mouse and keyboard"),
            flag(BotSettings::debug), Hint.flag("Debug logging"));

    private SettingHints() {}

    // One helper per argument type: each setter shares its name with a no-argument getter, which makes the
    // reference inexact, and javac infers nothing from an inexact reference.
    private static Executable whole(Ref.Of2<BotSettings, Integer, BotSettings> setter) {
        return Ref.resolve(setter);
    }

    private static Executable fraction(Ref.Of2<BotSettings, Double, BotSettings> setter) {
        return Ref.resolve(setter);
    }

    private static Executable flag(Ref.Of2<BotSettings, Boolean, BotSettings> setter) {
        return Ref.resolve(setter);
    }

    /** The hint for the setter this slot's argument is passed to, or empty. */
    public static Optional<Hint> of(ValueContext ctx) {
        return ctx.slot().flatMap(SlotContext::parameter).map(SettingHints::of);
    }

    /** The hint for {@code parameter}, or {@code null} when it is not a setter's argument. */
    static Hint of(Parameter parameter) {
        return HINTS.get(parameter.getDeclaringExecutable());
    }

    /** Whether this slot is a setting's argument. */
    public static boolean claims(ValueContext ctx) {
        return of(ctx).isPresent();
    }
}
