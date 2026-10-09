package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.flow.Labelled;
import com.botmaker.sdk.internal.flow.BuiltinOutcome;

/**
 * What an activity reports when it finishes — {@code Outcomes.BAG_FULL}, {@code Outcomes.WON} — and what the
 * flow drawn in BotMaker Studio routes on.
 *
 * <p><b>An outcome is a constant of your bot's {@code Outcomes} enum</b> (2026-10-10), the file BotMaker keeps
 * beside {@code Sdk.java}:
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.OUTCOMES)
 * public enum Outcomes implements Outcome {
 *     BAG_FULL, WON
 * }
 *
 * public static Outcome body() {
 *     if (bagFull()) return Outcomes.BAG_FULL;
 *     mineOnce();
 *     return Outcome.NEXT;
 * }
 * }</pre>
 *
 * <p>A body, a wire and the list of outcomes an activity declares all name the same constant, so a typo is a
 * compile error, and renaming an outcome on the Activity Flow canvas renames the constant and every use of it
 * by binding.
 *
 * <p><b>The name is the outcome.</b> What the canvas draws on the wire and the run's trace prints is the name
 * read as words ({@link Labelled#label()}) — {@code BAG_FULL} is "Bag full" — so the constant holds nothing
 * else, and a new outcome is one more name in the enum. The constant used to hold its label,
 * {@code Outcome.named("Bag full")}.
 */
public interface Outcome extends Labelled {

    /**
     * "Nothing special to report, carry on" — the outcome every activity has whether it declares one or not,
     * and the plain output wire on its card.
     */
    Outcome NEXT = BuiltinOutcome.NEXT;

    /**
     * "This activity is switched off, go here instead" — the other outcome every activity has. A body never
     * reports it, since it did not run: the flow takes this wire for an activity switched off or with no body
     * yet. Unwired, it ends the run.
     */
    Outcome DISABLED = BuiltinOutcome.DISABLED;
}
