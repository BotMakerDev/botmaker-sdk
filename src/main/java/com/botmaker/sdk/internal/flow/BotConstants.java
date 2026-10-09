package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;

/**
 * A constant of the bot's own {@code Activities} or {@code Outcomes} enum, by its name — what an editor that
 * cannot load the bot's classes holds instead of {@code Outcomes.WON} (2026-10-10).
 *
 * <p>The host reads each constant of those enums as {@link #outcome} or {@link #activity} of its name
 * ({@code ManagedValue.byName}), and writes a value equal to one back as that constant. Two of these are equal
 * when their names are; a bot's own constants never meet one at run time, where a flow holds the enum's.
 */
public final class BotConstants {

    private BotConstants() {}

    /** The bot's outcome constant {@code name}, as an editor holds it. */
    public record OutcomeConstant(String name) implements Outcome {
        @Override
        public String toString() {
            return name;
        }
    }

    /** The bot's activity constant {@code name}, as an editor holds it. */
    public record ActivityConstant(String name) implements Activity {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * The outcome the constant {@code name} stands for: {@link Outcome#NEXT} for none or {@code "NEXT"},
     * {@link Outcome#DISABLED} for {@code "DISABLED"}, else the bot's constant of that name.
     */
    public static Outcome outcome(String name) {
        if (name == null || name.isBlank() || name.equals(Outcome.NEXT.name())) return Outcome.NEXT;
        if (name.equals(Outcome.DISABLED.name())) return Outcome.DISABLED;
        return new OutcomeConstant(name);
    }

    /** The activity the constant {@code name} stands for: {@link Activity#NONE} for none. */
    public static Activity activity(String name) {
        return name == null || name.isBlank() ? Activity.NONE : new ActivityConstant(name);
    }
}
