package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.meta.ReplacedBy;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.flow.Labels;

import java.util.List;

/**
 * One of a bot's activity flows, as a value: what its activities are, how they are wired, where a run starts
 * and what stops it.
 *
 * <p><b>A flow is Java, so a renamed activity method is a compile error</b> rather than a silently empty
 * value three screens into a run. A bot holds its flows as constants of its {@code Flows} class, one per way
 * of playing, and the editor rewrites one constant's expression, leaving everything around it exactly as you
 * wrote it. Which one runs is the bot's {@code @Param}, which {@code Bot.runFlow} reads off {@code Sdk.java}; the
 * game's home and setup, which no flow owns, are {@link Run}'s, in {@code Sdk.run()}.
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.FLOWS)
 * public final class Flows {
 *     public static final Flow MAIN = Flow.of(
 *             List.of(Flow.activity(Activities.Collect.class, Collect::body)
 *                             .described("Click collect while there is one."),
 *                     Flow.activity(Activities.Rest.class, Rest::body).switchable()),
 *             List.of(Flow.edge(Activities.Collect.NEXT, Activities.Collect.class),
 *                     Flow.edge(Activities.Collect.NOTHING_LEFT, Activities.Rest.class),
 *                     Flow.edge(Activities.Rest.NEXT, Activities.Collect.class)),
 *             Activities.Collect.class,
 *             Flow.limits(1000, 1000));
 * }
 * }</pre>
 *
 * <h2>An activity is its outcome enum</h2>
 *
 * <p>Each activity is an enum nested in the bot's {@code Activities} class, and its constants are its exits
 * (2026-10-10). A step, the start and an {@code ActivitySwitch} call name the enum by its class literal; a wire
 * names the outcome it leaves on, whose enum is the activity it leaves. A body returns its activity's enum, and
 * {@link #activity} ties the two, so javac refuses a body reporting another activity's outcome. The canvas
 * renames them by binding. Two flows may run the same activity, each with its own step.
 *
 * <h2>A step names what it sets</h2>
 *
 * <p>{@link #activity} takes the two things every step has, the activity and its work, and each other part is a
 * named link after it, written only when it is not the default: {@code .described("…")}, {@code .off()},
 * {@code .goesHome()}, {@code .switchable()}, {@code .whenOff(…)}. Popups are screens of the bot's map, closed
 * in every activity, not a step's.
 *
 * <h2>A flow inside a flow</h2>
 *
 * <p>A card can be another flow, a sub-flow ({@link #subflow}): the run walks that flow to its end, under its own
 * limits and switches, and the card counts as one step of the outer flow. Its exits are the inner flow's outcomes
 * that nothing inside it wires; a wire out names the card and the outcome, a wire in names the flow:
 *
 * <pre>{@code
 * public static final Flow DAILY = Flow.of(…);   // declared above MAIN, which reads it
 * public static final Flow MAIN = Flow.of(
 *         List.of(Flow.activity(Activities.Collect.class, Collect::body),
 *                 Flow.subflow(DAILY).described("Claim the daily rewards."),
 *                 Flow.activity(Activities.Rest.class, Rest::body)),
 *         List.of(Flow.edge(Activities.Collect.NOTHING_LEFT, DAILY),
 *                 Flow.edge(DAILY, Activities.Claim.DONE, Activities.Rest.class)),
 *         Activities.Collect.class,
 *         Flow.limits(1000, 1000));
 * }</pre>
 *
 * <p>The inner flow is named as it is declared, {@code DAILY}, so it must be declared above the flow that runs it:
 * javac refuses a flow inside itself, and a flow read before its declaration is null, which a run refuses at
 * start-up. Hitting the inner flow's step limit stops the run.
 *
 * <h2>Four parts, fixed, and no varargs</h2>
 *
 * <p>Each of the four has a type the editor knows, which is what lets it take the expression apart, change
 * one activity and put it back without re-writing the rest.
 *
 * <p><b>Card positions are not here.</b> They change without the bot changing, so they are a value of their
 * own, the {@link FlowLayout} {@code Sdk.flowLayout()} returns, which nothing at runtime reads.
 *
 * @param steps  every activity in the flow with its work and its switches, in the order the editor lists them
 * @param edges  where each outcome leads; an edge naming a card that is not here is ignored
 * @param start  the card a run begins at; {@link Activity#NONE} for a flow that runs nothing
 * @param limits what stops a run that never finishes
 */
public record Flow(List<Step> steps, List<Edge> edges, Node start, Limits limits) {

    /** The empty flow: nothing to run, which is what a project with nothing drawn should do. */
    public static final Flow NONE = new Flow(List.of(), List.of(), Activity.NONE, Limits.DEFAULT);

    public Flow {
        steps = steps == null ? List.of() : List.copyOf(steps);
        edges = edges == null ? List.of() : List.copyOf(edges);
        start = start == null ? Activity.NONE : start;
        limits = limits == null ? Limits.DEFAULT : limits;
    }

    /** The flow, spelled the way the editor writes it. */
    public static Flow of(List<Step> steps, List<Edge> edges, Class<? extends Outcome> start, Limits limits) {
        return new Flow(steps, edges, Activity.of(start), limits);
    }

    /** The flow, starting at its sub-flow card {@code start}. */
    public static Flow of(List<Step> steps, List<Edge> edges, Flow start, Limits limits) {
        return new Flow(steps, edges, new Sub(start), limits);
    }

    /**
     * A card of a flow: an {@link Activity}, or another flow run as one step ({@link Sub}).
     */
    public sealed interface Node extends Labelled permits Activity, Sub {

        /** Whether this is no card at all, {@link Activity#NONE}. */
        default boolean isNone() {
            return false;
        }
    }

    /**
     * A sub-flow card: {@code inner} walked to its end as one step of the flow that holds the card.
     *
     * <p>Its name is the constant the bot declares it as, {@code DAILY}, read off the bot's {@code Flows} class when
     * the run starts; blank before then.
     *
     * @param inner the flow it runs; null only when it was read before its declaration, which a run refuses
     */
    public record Sub(Flow inner) implements Node {

        @Override
        public String name() {
            return InstalledRun.nameOf(inner);
        }

        /**
         * {@code ▸ DAILY}: the flow's constant as the bot names it, marked so that no activity's label is ever one
         * (2026-10-12, the canvas keys a card by its label).
         */
        @Override
        public String label() {
            String name = name();
            return name.isEmpty() ? "a sub-flow" : Labels.SUBFLOW + name;
        }

        /**
         * The same card when it runs the same flow — the same constant, not an equal one: two flows declared alike
         * are two cards, and comparing whole flow trees on every step would cost a walk of each. An editor's
         * reference to a flow by its name ({@code BotConstants.flow}) is the same card as another reference to
         * the same name.
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof Sub sub && (sub.inner == inner
                    || BotConstants.flowName(inner) != null && inner.equals(sub.inner));
        }

        @Override
        public int hashCode() {
            return BotConstants.flowName(inner) != null ? inner.hashCode() : System.identityHashCode(inner);
        }

        @Override
        public String toString() {
            return name().isEmpty() ? "Sub[…]" : name();
        }
    }

    /**
     * A sub-flow card running {@code inner}, switched on, with nothing else set; {@link Step#described},
     * {@link Step#off}, {@link Step#goesHome} and {@link Step#whenOff} link after it as
     * after an activity.
     *
     * @param inner the flow to run, declared above this one — {@code DAILY}
     */
    public static Step subflow(Flow inner) {
        return new Step(new Sub(inner), null, "", true, false, false, Activity.NONE);
    }

    /**
     * One activity's step, switched on, with no description, no go-home, not switchable by the runner and no
     * off-wire; each of those is a link after it ({@link Step#described}, {@link Step#off}, {@link Step#goesHome},
     * {@link Step#switchable}, {@link Step#whenOff}).
     *
     * @param activity which activity this is — {@code Activities.Collect.class}
     * @param body     the work, as a method reference returning that enum — {@code Collect::body}; or
     *                 {@link ActivityBody#none()} for a card not written yet
     * @param <O>      the activity's enum, which the body returns
     */
    public static <O extends Outcome> Step activity(Class<O> activity, ActivityBody<O> body) {
        return new Step(Activity.of(activity), body, "", true, false, false, Activity.NONE);
    }

    /** One wire: leaving {@code on}'s activity when it reports {@code on}, arriving at {@code to}. */
    public static Edge edge(Outcome on, Class<? extends Outcome> to) {
        return new Edge(on, Activity.of(to));
    }

    /** One wire: leaving {@code on}'s activity when it reports {@code on}, arriving at the sub-flow card {@code to}. */
    public static Edge edge(Outcome on, Flow to) {
        return new Edge(on, new Sub(to));
    }

    /** One wire out of the sub-flow card {@code via}: when its flow ends on {@code on}, go on to {@code to}. */
    public static Edge edge(Flow via, Outcome on, Class<? extends Outcome> to) {
        return new Edge(via, on, Activity.of(to));
    }

    /** One wire out of the sub-flow card {@code via} into the sub-flow card {@code to}. */
    public static Edge edge(Flow via, Outcome on, Flow to) {
        return new Edge(via, on, new Sub(to));
    }

    /** What stops a run that never finishes. */
    public static Limits limits(int maxSteps, int stepDelayMs) {
        return new Limits(maxSteps, stepDelayMs);
    }

    /** The step of the card {@code node}, or {@code null}. */
    public Step step(Node node) {
        for (Step step : steps) {
            if (step.node().equals(node)) return step;
        }
        return null;
    }

    /**
     * One card of a flow: an activity and its work, or a sub-flow, and everything the canvas says about it.
     *
     * @param node        which card this is: an {@link Activity}, or a {@link Sub}
     * @param body        the work, as a method reference — {@code Collect::body}; null for a sub-flow
     * @param description one line, shown on the canvas and nowhere else
     * @param enabled     whether it runs; a disabled card takes its off-wire rather than its work
     * @param goHome      whether the bot's {@link Run#home} runs before this card
     * @param runnerSwitch whether the person running the bot may switch it off (or back on) for their machine,
     *                    without opening the project ({@link #switchable()})
     * @param whenOff     where the run goes when this card is switched off or has no body yet;
     *                    {@link Activity#NONE} ends the run there
     */
    public record Step(Node node, ActivityBody<?> body, String description, boolean enabled,
                       boolean goHome, boolean runnerSwitch, Node whenOff) {

        public Step {
            node = node == null ? Activity.NONE : node;
            description = description == null ? "" : description;
            whenOff = whenOff == null ? Activity.NONE : whenOff;
        }

        /** The activity this card is, or {@link Activity#NONE} for a sub-flow card. */
        public Activity activity() {
            return node instanceof Activity activity ? activity : Activity.NONE;
        }

        /** This step with {@code description}, the one line the canvas shows. */
        public Step described(String description) {
            return new Step(node, body, description, enabled, goHome, runnerSwitch, whenOff);
        }

        /** This step switched off: the run takes its off-wire rather than its work. */
        public Step off() {
            return new Step(node, body, description, false, goHome, runnerSwitch, whenOff);
        }

        /** This step with the bot's {@link Run#home} run before it. */
        public Step goesHome() {
            return new Step(node, body, description, enabled, true, runnerSwitch, whenOff);
        }

        /**
         * This step, unchanged.
         *
         * @deprecated since 2026-10-14 popups are closed before every vision call of every activity (since 2026-10-16
         * by the map's popup screens), so there is nothing to switch on per card. Kept so a flow written with it
         * still reads; Studio no longer writes it.
         */
        @Deprecated
        @ReplacedBy(note = "Popups are screens of the bot's map, closed in every activity.")
        public Step checksPopups() {
            return this;
        }

        /** This step offered to the person running the bot, who may switch it off or back on for their machine. */
        public Step switchable() {
            return new Step(node, body, description, enabled, goHome, true, whenOff);
        }

        /** This step going on to {@code to} when it is switched off or has no body yet. */
        public Step whenOff(Class<? extends Outcome> to) {
            return new Step(node, body, description, enabled, goHome, runnerSwitch, Activity.of(to));
        }

        /** This step going on to the sub-flow card {@code to} when it is switched off or has no body yet. */
        public Step whenOff(Flow to) {
            return new Step(node, body, description, enabled, goHome, runnerSwitch, new Sub(to));
        }

        /** The card's label, which is what the canvas and the trace show. */
        public String label() {
            return node.label();
        }
    }

    /**
     * One wire: a card reporting {@code on} goes on to {@code to}. The card it leaves is {@code on}'s activity, or
     * the sub-flow card {@code via} whose flow ended on {@code on} ({@link #from()}).
     *
     * @param via the sub-flow this wire leaves, or null for an activity's own wire
     */
    public record Edge(Flow via, Outcome on, Node to) {

        public Edge {
            to = to == null ? Activity.NONE : to;
        }

        /** An activity's own wire. */
        public Edge(Outcome on, Node to) {
            this(null, on, to);
        }

        /** The card this wire leaves: the sub-flow card {@link #via()}, else the enum that declares {@link #on()}. */
        public Node from() {
            if (via != null) return new Sub(via);
            return on == null ? Activity.NONE : on.activity();
        }
    }

    /**
     * What stops a run that never finishes: how many activities may run, and how long to wait between them.
     *
     * @param maxSteps    how many activities may run before the bot gives up; {@code 0} for no limit
     * @param stepDelayMs how long to wait between activities, in milliseconds
     */
    public record Limits(int maxSteps, int stepDelayMs) {

        /** A thousand steps a second apart — the numbers a new project starts with. */
        public static final Limits DEFAULT = new Limits(1000, 1000);

        public Limits {
            maxSteps = Math.max(0, maxSteps);
            stepDelayMs = Math.max(0, stepDelayMs);
        }
    }
}
