package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.config.Wire;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.internal.bot.ActivityRegistry;
import com.botmaker.sdk.internal.trace.Trace;

import java.util.function.Function;

/**
 * Where you say what an activity <em>does</em>.
 *
 * <p>An activity is created on the Activity Flow canvas, not in code — it lives in the project's own
 * {@code activities.json}, along with the outcomes it can report and where each of them leads. What the
 * canvas cannot hold is the work itself, and this is where that goes:
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     Activities.define("Mining", ctx -> {
 *         if (bagFull()) return ctx.outcome("BAG_FULL");
 *         mineOnce();
 *         return ctx.done();
 *     });
 *
 *     Bot.start(() -> FlowGraph.run(Main.class, Main::goHome), Main::goHome);
 * }
 * }</pre>
 *
 * <h2>Why a call and not a class</h2>
 *
 * <p>Until 2026-08-29 BotMaker wrote a {@code class Mining extends Activity<Mining.Outcome>} into your
 * project for every activity on the canvas, and then kept editing it — renaming the type when you renamed
 * the activity, adding constants to its enum when you added outcomes. That made a file inside your own
 * source tree one that you could not freely rename, move or delete. **A project's structure belongs to
 * you.** So nothing is written: you write this call wherever you like, in whatever file you like, and it is
 * the only thing that connects the canvas to your code.
 *
 * <p>Two consequences worth knowing. An activity with no {@code define} call is <b>not an error</b> — it
 * behaves exactly as one you switched off, following its {@code DISABLED} wire, so a flow drawn ahead of the
 * code still runs. And an activity's name is a string here: renaming it on the canvas does not rename it in
 * your code, and the two stop matching until you change it. That is the same trade an outcome makes, and it
 * is why the editor draws a dropdown of the project's activities on this call rather than a text box.
 *
 * <h2>The older way still works</h2>
 *
 * <p>Subclassing {@link Activity} does everything it always did, and a bot that has such classes keeps
 * running unchanged. Both kinds land in the same registry, so {@link Activity#disable(String)} finds either.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "◎", order = 35)
public final class Activities {

    private Activities() {}

    /**
     * Says what the named activity does.
     *
     * <p>Call it before the flow starts — from your bot's {@code main}, above {@code Bot.start}. Defining
     * the same name twice keeps the later body, which is what constructing the same {@link Activity}
     * subclass twice has always done.
     *
     * @param name the activity's name, exactly as it reads on the Activity Flow canvas
     * @param body the work, handed the activity's own {@link ActivityContext}; it reports what happened with
     *             {@code ctx.outcome("…")} or {@code ctx.done()}
     */
    public static void define(String name, Function<ActivityContext, Outcome> body) {
        if (name == null || name.isBlank()) {
            Debug.error("[Activity] define: an activity needs a name. Ignoring.");
            return;
        }
        if (body == null) {
            Debug.error("[Activity] define: '" + name + "' was given no body. Ignoring.");
            return;
        }
        ActivityRegistry.register(new Defined(name, body));
    }

    /**
     * Whether the named activity is switched on right now — its value in Project ▸ Set Activity Values, plus
     * any {@link Activity#enable(String)} / {@link Activity#disable(String)} made during this run.
     *
     * <p>The flow consults this itself before running anything, so a body does not need to. It is here for
     * the case the flow cannot answer: one activity asking about another.
     */
    public static boolean active(String name) {
        ActivityRegistry.Runner runner = ActivityRegistry.get(name);
        return runner == null ? Wire.enabled(name) : runner.active();
    }

    /**
     * A defined activity: its name, its body, and whatever a running bot has since said about whether it
     * should run.
     *
     * <p>Not a record, for the one reason a record cannot serve: the override is genuinely mutable state —
     * the whole point of {@code ctx.disable()} is that a body can switch its own activity off mid-run.
     *
     * <p>{@code active()} reads {@link Wire#enabled} rather than caching it, and {@code null} means nothing
     * has been said, which is a different answer from {@code false}. So a value changed in the editor is
     * picked up on the next run without the definition knowing anything about files, and an override made
     * during a run outranks it.
     */
    private static final class Defined implements ActivityRegistry.Runner {

        private final String name;
        private final Function<ActivityContext, Outcome> body;
        private Boolean override;

        Defined(String name, Function<ActivityContext, Outcome> body) {
            this.name = name;
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean active() {
            return override != null ? override : Wire.enabled(name);
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.override = enabled;
        }

        /**
         * Runs the body and reports what it said.
         *
         * <p>One line on the console per activity, because the activity and its outcome are the coarsest
         * unit of "what is the bot doing" — it is what makes a debug log read as a story rather than as
         * vision events. A body that answers {@code null} is treated as {@code ctx.done()}: a lambda whose
         * last statement fell through has nothing special to report, which is exactly what that means.
         */
        @Override
        public Outcome execute() {
            long startedAt = System.currentTimeMillis();
            Outcome outcome = body.apply(new ActivityContext(name));
            if (outcome == null) outcome = Outcome.of(null);
            Debug.log("[Activity] " + name + " → " + outcome
                    + " (" + Trace.elapsed(System.currentTimeMillis() - startedAt) + ")");
            return outcome;
        }
    }
}
