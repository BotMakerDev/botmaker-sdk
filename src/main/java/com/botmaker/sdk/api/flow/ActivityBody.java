package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.internal.flow.NoBody;

/**
 * The work one activity does, as a <b>method reference</b> — {@code Collect::body} — returning one of that
 * activity's outcomes.
 *
 * <p>This is the type an activity's body has inside a {@code Flow}. A method reference is a token sequence
 * javac resolves, so renaming or deleting the method is a <b>compile error</b> that names the file it broke;
 * and since {@code Flow.activity(Activities.Collect.class, Collect::body)} ties {@code O} to the activity's enum,
 * so is a body returning another activity's enum.
 *
 * <pre>{@code
 * public final class Collect {
 *     public static Activities.Collect body() {
 *         if (nothingLeft()) return Activities.Collect.NOTHING_LEFT;
 *         clickCollect();
 *         return Activities.Collect.NEXT;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Why a method and not a lambda.</b> BotMaker writes a flow back into your own source, so what it
 * writes has to be something it can also read: {@code Collect::body} is four tokens with one meaning, and a
 * lambda is a body of code nobody should be rewriting. Write the lambda if you like — it compiles and runs —
 * and the flow editor will show that activity read-only, because it will not rewrite code you wrote.
 *
 * <p><b>The name is still yours.</b> The class and the method are named by you, wherever you like. Nothing
 * requires {@code body}, and nothing requires one class per activity.
 *
 * @param <O> the activity's enum, whose constants are what the body can report
 */
@FunctionalInterface
public interface ActivityBody<O extends Outcome> {

    /**
     * The work of an activity that has been <b>drawn but not written</b>: the flow editor writes
     * {@code ActivityBody.none()} where a method reference will go, and the run takes the activity's off-wire,
     * as it does for one switched off. Always the same object, so {@code body == ActivityBody.none()} is the
     * question the flow asks.
     */
    static <O extends Outcome> ActivityBody<O> none() {
        return NoBody.none();
    }

    /**
     * Does the work and says what happened.
     *
     * @return the outcome the flow routes on; {@code null} reports nothing, and the run ends there
     */
    O run();
}
