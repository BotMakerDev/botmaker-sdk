package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Labelled;

/**
 * What an activity reports when it finishes — {@code Activities.Collect.NOTHING_LEFT},
 * {@code Activities.Battle.WON} — and what the flow drawn in BotMaker Studio routes on.
 *
 * <p><b>An outcome is a constant of its activity's enum</b> (2026-10-10), one of the enums nested in the
 * {@code Activities} class BotMaker keeps beside {@code Sdk.java}:
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.ACTIVITIES)
 * public final class Activities {
 *     public enum Collect implements Outcome { NEXT, NOTHING_LEFT }
 * }
 *
 * public static Activities.Collect body() {
 *     if (nothingLeft()) return Activities.Collect.NOTHING_LEFT;
 *     clickCollect();
 *     return Activities.Collect.NEXT;
 * }
 * }</pre>
 *
 * <p>The body returns its activity's enum, so a body reporting another activity's outcome does not compile, and
 * renaming an outcome on the Activity Flow canvas renames the constant and every use of it by binding.
 * {@code NEXT} is an ordinary constant Studio puts in each new activity, not a built-in one: rename it, or remove
 * it once the activity has another.
 *
 * <p><b>The name is the outcome.</b> What the canvas draws on the wire and the run's trace prints is the name
 * read as words ({@link Labelled#label()}) — {@code NOTHING_LEFT} is "Nothing left".
 */
public interface Outcome extends Labelled {

    /**
     * No outcome: what a fresh {@code Outcome} holds until one is picked, labelled blank. It belongs to no
     * activity, so no wire leaves on it, and a run reaching it ends there as on {@code null}.
     *
     * <p>A lambda, not one of the SDK's {@code Outcome} classes: a constant of a class implementing this interface
     * would make the two classes' initialisation wait on each other, and two threads could deadlock on it.
     */
    Outcome NONE = () -> "";

    /** The activity this outcome belongs to: the enum that declares it. */
    default Activity activity() {
        return this instanceof Enum<?> constant ? Activity.of(owner(constant)) : Activity.NONE;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Outcome> owner(Enum<?> constant) {
        return (Class<? extends Outcome>) constant.getDeclaringClass();
    }
}
