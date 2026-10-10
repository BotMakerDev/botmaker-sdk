package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;

/**
 * The bot's own activities and outcomes by name — what an editor that cannot load the bot's classes holds
 * instead of {@code Activities.Collect.class} and {@code Activities.Collect.WON} (2026-10-10).
 *
 * <p>The host reads each enum nested in the bot's {@code Activities} class as {@link #activity} of its name
 * ({@code ManagedValue.groupedAs}) and each of its constants as {@link #outcome} of the two names
 * ({@code ManagedValue.ofEnums}), and writes a value equal to one back as that class literal or constant. An
 * outcome carries its enum's name, so {@code Battle.NEXT} and {@code Collect.NEXT} stay two outcomes. A bot's own
 * constants never meet one at run time, where a flow holds the enums'.
 */
public final class BotConstants {

    private BotConstants() {}

    /** The bot's constant {@code name} of the activity enum {@code activity}, as an editor holds it. */
    public record OutcomeConstant(String activityName, String name) implements Outcome {
        @Override
        public Activity activity() {
            return new Activity(activityName);
        }

        @Override
        public String toString() {
            return activityName + "." + name;
        }
    }

    /** The constant {@code name} of the activity enum {@code activity}. */
    public static Outcome outcome(String activity, String name) {
        return new OutcomeConstant(activity == null ? "" : activity, name == null ? "" : name);
    }

    /** The activity enum {@code name}: {@link Activity#NONE} for none. */
    public static Activity activity(String name) {
        return name == null || name.isBlank() ? Activity.NONE : new Activity(name);
    }
}
