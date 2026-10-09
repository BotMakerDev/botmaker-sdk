package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;

/**
 * The two outcomes every activity has, {@link Outcome#NEXT} and {@link Outcome#DISABLED}: an enum of the SDK's
 * own, as a bot's {@code Outcomes} is the bot's. Each is labelled with its name as written, the port name the
 * canvas draws.
 *
 * <p>Initialising it initialises {@code Labelled}, which holds the default {@code label()}, and never
 * {@code Outcome}, which declares none: so {@code Outcome.NEXT} reads this enum's constant whichever of the two
 * is touched first.
 */
public enum BuiltinOutcome implements Outcome {
    NEXT, DISABLED;

    @Override
    public String label() {
        return name();
    }
}
