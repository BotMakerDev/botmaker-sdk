package com.botmaker.sdk.api.bot;


/**
 * The work one activity does, as a <b>method reference</b> — {@code Collect::body}.
 *
 * <p>This is the type an activity's body has inside a {@code Flow}. A method reference is a token sequence
 * javac resolves, so renaming or deleting the method is a <b>compile error</b> that names the file it broke.
 *
 * <p>It is an ordinary functional interface and nothing about it is special-cased:
 *
 * <pre>{@code
 * public final class Collect {
 *     public static Outcome body() {
 *         if (nothingLeft()) return Activities.outcome("NOTHING_LEFT");
 *         clickCollect();
 *         return Activities.next();
 *     }
 * }
 *
 * ActivityBody collect = Collect::body;   // compiles, which is the point
 * }</pre>
 *
 * <p><b>Why a method and not a lambda.</b> BotMaker writes a flow back into your own source, so what it
 * writes has to be something it can also read: {@code Collect::body} is four tokens with one meaning, and a
 * lambda is a body of code nobody should be rewriting. Write the lambda if you like — it compiles and runs —
 * and the flow editor will show that activity read-only, because it will not rewrite code you wrote.
 *
 * <p><b>The name is still yours.</b> The class and the method are named by you, wherever you like. Nothing
 * requires {@code body}, nothing requires one class per activity, and an activity's <em>label</em> on the
 * canvas is a separate string precisely so that renaming one does not force the other.
 */
@FunctionalInterface
public interface ActivityBody {

    /**
     * The work of an activity that has been <b>drawn but not written</b>.
     *
     * <p>Drawing the flow first and writing the bodies afterwards is an ordinary way to work, so a card on
     * the canvas with no method behind it yet has to be sayable. This is how it is said: the flow editor
     * writes {@code ActivityBody.NONE} where a method reference will go, and the activity is a node like any
     * other — it simply does nothing, and the run takes the wire it would take for one switched off.
     *
     * <p>Comparable by identity on purpose, and the one place in this vocabulary where that is true: it is a
     * constant, not a value read back out of a file, so {@code body == ActivityBody.NONE} is exactly the
     * question the flow asks. Calling it is harmless and reports nothing in particular.
     */
    ActivityBody NONE = () -> null;

    /**
     * Does the work and says what happened — {@link Activities#outcome(String)} or {@link Activities#next()}.
     *
     * <p>No argument since SDK 2.0.0. An {@code ActivityContext} was handed in so {@code ctx.outcome("…")}
     * had a receiver the editor could key a picker on; the picker is keyed on the parameter's
     * {@code @OutcomeName} now, and the flow knows which activity is running without being told.
     *
     * @return what the flow routes on; {@code null} is read as {@link Activities#next()}, for the same reason
     *         a lambda whose last statement fell through has nothing special to report
     */
    Outcome run();
}
