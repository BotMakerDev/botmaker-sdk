package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

/**
 * The work one activity does, as a <b>method reference</b> — {@code Collect::body}.
 *
 * <p>This is the type an activity's body has inside a {@code Flow}, and the shape is the whole reason it
 * exists. An activity's body used to be found by <em>name</em>: {@code Activities.define("Collect", …)}
 * matched a string in a JSON file against a string in a Java call, so renaming either one compiled fine and
 * the flow quietly took the {@code DISABLED} wire three screens into a run. A method reference is a token
 * sequence javac resolves, so the same rename is a <b>compile error</b> that names the file it broke.
 *
 * <p>It is an ordinary functional interface and nothing about it is special-cased:
 *
 * <pre>{@code
 * public final class Collect {
 *     public static Outcome body(ActivityContext ctx) {
 *         if (nothingLeft()) return ctx.outcome("NOTHING_LEFT");
 *         clickCollect();
 *         return ctx.done();
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
@Palette(category = "flow", categoryLabel = "Flow", order = 106)
@Hidden("a value type: a flow names one as a method reference, it is not built from a menu")
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
    ActivityBody NONE = ctx -> null;

    /**
     * Does the work and says what happened.
     *
     * @param ctx this activity, from the inside — {@code ctx.outcome("…")} or {@code ctx.done()}
     * @return what the flow routes on; {@code null} is read as {@code ctx.done()}, for the same reason a
     *         lambda whose last statement fell through has nothing special to report
     */
    Outcome run(ActivityContext ctx);
}
