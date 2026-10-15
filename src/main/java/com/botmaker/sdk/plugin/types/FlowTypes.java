package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Held;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.internal.flow.BotConstants;

import java.util.List;
import java.util.Map;

/**
 * The shapes an {@code @SdkValue} value of this plugin's takes: a flow's four records, the run around the
 * flows and the layout's, as the components that go inside the calls that write them.
 *
 * <h2>Why a flow is four declarations and not one type</h2>
 *
 * <p>A {@link ComponentType} is what lets the editor take an expression apart into typed parts, change one
 * of them and put the rest back exactly as written. One opaque {@code FLOW} leaf with a codec would give the
 * editor a string — which is where this whole design started, and what it exists to leave behind. Four
 * declarations give it {@code Flow.of(List.of(Flow.activity(…), …), List.of(…), Activities.Collect.class,
 * Flow.limits(…))} as a tree it can walk, typed at every tip.
 *
 * <p><b>None of them implements {@code PluginType}</b>, and that is why the two interfaces are independent:
 * a {@code Step}, an {@code Edge} and a {@code Limits} are parts of a flow and are
 * never picked on their own, so extending it would owe each a {@code fresh()} and an {@code editor()}
 * nothing would ever call.
 *
 * <p><b>A call with the wrong number of arguments is not this shape</b>, and every {@code build} below says
 * so by answering {@code null} rather than guessing: it is a call to something else, or to a newer version of
 * this factory, and either way lining its parts up with these would be rewriting code on a hunch.
 *
 * <h2>An activity is a class literal, held as its name</h2>
 *
 * <p>A bot names an activity by its enum's class literal, {@code Activities.Collect.class} (2026-10-10). The
 * editor has no class to hold — it never loads the bot — so the host hands that literal over as the
 * {@link Activity} of its name ({@code ManagedValue.groupedAs}), and writes an {@code Activity} back as the
 * literal. No factory here takes an {@code Activity}, so every shape whose factory takes a {@code Class} is taken
 * apart and built back by hand ({@code components} and {@code build}), and so is the step's {@code .whenOff(…)}
 * link: the factories make the same records a bot's run does, from the name instead of the class.
 *
 * <h2>A body is a name, and it crosses as the source it is written as</h2>
 *
 * <p>An activity's body is written {@code Collect::body}, and the run's {@code .home(…)} {@code Gamebot::goHome}.
 * Neither is made into a live object — the editor never runs a bot's code — so each crosses as the
 * {@code String} the file writes, and is written back exactly so. The flow editor checks what a person types
 * ({@code FlowNames.isMethodReference}); this class checks nothing.
 *
 * <p><b>It stays text, and that was decided.</b> This is not a plugin writing Java: the host writes the text
 * through its own source-leaf path, which parses it into a tree, and a rename in the real {@code Sdk.java}
 * already follows the binding. A contract {@code MethodName} type was considered and declined. Do not propose
 * it again without new facts.
 */
public final class FlowTypes {

    private FlowTypes() {}

    /**
     * The parts the host holds as something the factory does not take — an activity's class literal as its
     * {@link Activity}, a method reference as its text — and the maps whose stray entries are dropped. Each is a
     * constant, named where its call is declared and again where {@code builtFrom} reads it.
     */
    private static final class Parts {
        static final Held<Flow, List<Flow.Step>> STEPS = Held.list(Flow.Step.class, Flow::steps);
        static final Held<Flow, List<Flow.Edge>> EDGES = Held.list(Flow.Edge.class, Flow::edges);
        static final Held<Flow, Activity> START = Held.as(Activity.class,
                flow -> flow.start() instanceof Activity activity ? activity : null);
        static final Held<Flow, Flow.Limits> LIMITS = Held.as(Flow.Limits.class, Flow::limits);

        static final Held<Run, String> HOME = Held.as(String.class, run -> sourceOf(run.home()));
        static final Held<Run, String> SETUP = Held.as(String.class, run -> sourceOf(run.setup()));
        static final Held<Run, String> POPUPS = Held.as(String.class, run -> sourceOf(run.popups()));

        static final Held<Flow.Step, Activity> CARD = Held.as(Activity.class, Flow.Step::activity);
        static final Held<Flow.Step, String> BODY = Held.as(String.class,
                step -> bodyLiteral(sourceOf(step.body())));
        static final Held<Flow.Step, Activity> OFF = Held.as(Activity.class, FlowTypes::offActivity);

        static final Held<Flow.Edge, Flow> VIA = Held.as(Flow.class, Flow.Edge::via);
        static final Held<Flow.Edge, Outcome> ON = Held.as(Outcome.class, Flow.Edge::on);
        static final Held<Flow.Edge, Activity> TO = Held.as(Activity.class,
                edge -> edge.to() instanceof Activity activity ? activity : null);

        static final Held<FlowLayout, Map<String, FlowLayout.Cards>> FLOWS =
                Held.map(String.class, FlowLayout.Cards.class, FlowLayout::flows);
        /** Whether a flow goes home by default; one the host reads as no value is the default, on. */
        static final Held<FlowLayout, Boolean> GO_HOME = Held.as(Boolean.class, FlowLayout::goHomeByDefault);
        static final Held<FlowLayout.Cards, Map<String, FlowLayout.Spot>> SPOTS =
                Held.map(String.class, FlowLayout.Spot.class, FlowLayout.Cards::spots);
    }

    // ---- the leaves' codecs ----------------------------------------------------------------------------

    /** How "drawn, not written yet" is spelled in a file — fully qualified, as all of these are. */
    private static final String NO_BODY = ActivityBody.class.getName() + ".none()";

    /** A blank body is the card nobody has written yet, and it is written as the call that says so. */
    private static String bodyLiteral(String reference) {
        return reference == null || reference.isBlank() ? NO_BODY : reference;
    }

    /**
     * The body as the editor draws it: blank for the call that says "not written yet", which is what a card
     * with no method behind it is, and anything else exactly as written.
     */
    private static String bodyOf(String written) {
        String source = written.strip();
        boolean none = source.equals(NO_BODY) || source.equals(ActivityBody.class.getSimpleName() + ".none()");
        return none ? "" : written;
    }

    // ---- the containers --------------------------------------------------------------------------------

    /**
     * {@code Flow.of(List<Step>, List<Edge>, Class start, Limits)}. A flow with nothing in it has no activity to
     * start at, so no class literal to write: it is written {@code Flow.NONE} ({@link #written}).
     */
    public static final DeclaredCall<Flow> FLOW_SHAPE = ComponentType.part(Flow.class)
            .<List<Flow.Step>, List<Flow.Edge>, Class<? extends Outcome>, Flow.Limits, Flow>writtenAs(Flow::of,
                    Parts.STEPS.part(), Parts.EDGES.part(), Parts.START.part(), Parts.LIMITS.part())
            .builtFrom(parts -> flow(parts.get(Parts.STEPS), parts.get(Parts.EDGES), parts.get(Parts.START),
                    parts.get(Parts.LIMITS)))
            .constants(Flow.NONE)
            // A reference to a flow (BotConstants.flow) is written only as that constant; one naming no constant
            // left is no flow to spell out.
            .when(flow -> flow.start() instanceof Activity && BotConstants.flowName(flow) == null);

    /**
     * {@code Flow.of(List<Step>, List<Edge>, Flow start, Limits)}: a flow starting at a sub-flow card. The start is
     * a reference to another of the bot's flows, held by its name ({@code BotConstants.flow}).
     */
    public static final DeclaredCall<Flow> FLOW_FROM_SUBFLOW_SHAPE = ComponentType.part(Flow.class)
            .<List<Flow.Step>, List<Flow.Edge>, Flow, Flow.Limits, Flow>writtenAs(Flow::of,
                    Flow::steps, Flow::edges, flow -> innerOf(flow.start()), Flow::limits)
            .when(flow -> flow.start() instanceof Flow.Sub);

    /** The flow a sub-flow card runs, or null for any other card. */
    private static Flow innerOf(Flow.Node node) {
        return node instanceof Flow.Sub sub ? sub.inner() : null;
    }

    /** {@code flow} as it can be written: {@link Flow#NONE} once it has no activity left to start at. */
    public static Flow written(Flow flow) {
        return flow == null || flow.steps().isEmpty() ? Flow.NONE : flow;
    }

    private static Flow flow(List<Flow.Step> steps, List<Flow.Edge> edges, Activity start, Flow.Limits limits) {
        return start == null || limits == null ? null : new Flow(steps, edges, start, limits);
    }

    /**
     * {@code Run.of()} and its {@code .home(…)} and {@code .setup(…)} links, each written only when the run has
     * one. Each method crosses as the text it is written as, {@code Gamebot::goHome}.
     */
    public static final DeclaredCall<Run> RUN_SHAPE = ComponentType.part(Run.class)
            .writtenAs(Run::of)
            .<Runnable>with(Run::home, Parts.HOME.part(FlowTypes::method))
            .<Runnable>with(Run::setup, Parts.SETUP.part(FlowTypes::method))
            .<Runnable>with(Run::popups, Parts.POPUPS.part(FlowTypes::method))
            .<String>with(Run::notes, Run::notes)
            .constants(Run.NONE);

    /**
     * {@code Flow.activity(Class, ActivityBody)} and its links: {@code .described(String)}, {@code .off()},
     * {@code .goesHome()}, {@code .switchable()}, {@code .whenOff(Class)}, each written
     * only when the step is not as {@code Flow.activity} makes it. A new step is on, so its flag is the negative
     * {@code off()}.
     *
     * <p>A body crosses as the source it is written as, never as the functional object; a live one answers
     * empty, which reads as "written by hand" and is refused rather than guessed at. A card with no method yet is
     * written as {@code ActivityBody.none()}.
     */
    public static final DeclaredCall<Flow.Step> STEP_SHAPE = cardLinks(ComponentType.part(Flow.Step.class)
            .<Class<Outcome>, ActivityBody<Outcome>, Flow.Step>writtenAs(Flow::activity, Parts.CARD.part(),
                    Parts.BODY.part())
            .builtFrom(parts -> card(parts.get(Parts.CARD), parts.get(Parts.BODY))))
            .when(step -> step.node() instanceof Activity);

    /**
     * {@code Flow.subflow(Flow)} and the same links as an activity's card: a card running another of the bot's
     * flows, held by its name ({@code BotConstants.flow}). It has no body.
     */
    public static final DeclaredCall<Flow.Step> SUBFLOW_STEP_SHAPE = cardLinks(ComponentType.part(Flow.Step.class)
            .<Flow, Flow.Step>writtenAs(Flow::subflow, step -> innerOf(step.node())))
            .when(step -> step.node() instanceof Flow.Sub);

    /**
     * {@code call} with every card's links, in the order they are written: one list for both cards, so a link
     * added to one is never missing from the other.
     */
    private static DeclaredCall<Flow.Step> cardLinks(DeclaredCall<Flow.Step> call) {
        return call
                .with(Flow.Step::described, Flow.Step::description)
                .flag(Flow.Step::off, step -> !step.enabled())
                .flag(Flow.Step::goesHome, Flow.Step::goHome)
                // Read in a flow written before 2026-10-14, never written: popups are handled in every activity.
                .flag(Flow.Step::checksPopups, step -> false)
                .flag(Flow.Step::switchable, Flow.Step::runnerSwitch)
                .<Class<? extends Outcome>, Activity>with(Flow.Step::whenOff, Parts.OFF, FlowTypes::whenOff)
                .<Flow>with(Flow.Step::whenOff, step -> innerOf(step.whenOff()));
    }

    /** Where {@code step} goes switched off, when that is an activity: {@link Activity#NONE} for a sub-flow. */
    private static Activity offActivity(Flow.Step step) {
        return step.whenOff() instanceof Activity activity ? activity : Activity.NONE;
    }

    /** The card for {@code activity} running {@code body} as written, or null for no activity or body. */
    private static Flow.Step card(Activity activity, String body) {
        if (activity == null || activity.isNone() || body == null) return null;
        return step(activity, new Named(bodyOf(body)));
    }

    /** {@code step} going on to {@code to} when switched off: {@code .whenOff(…)} for an activity held by name. */
    public static Flow.Step whenOff(Flow.Step step, Flow.Node to) {
        return new Flow.Step(step.node(), step.body(), step.description(), step.enabled(), step.goHome(),
                step.runnerSwitch(), to);
    }

    /** {@code Flow.edge(Outcome, Class)}: the activity it leaves is the outcome's. */
    public static final DeclaredCall<Flow.Edge> EDGE_SHAPE = ComponentType.part(Flow.Edge.class)
            .<Outcome, Class<? extends Outcome>, Flow.Edge>writtenAs(Flow::edge, Parts.ON.part(), Parts.TO.part())
            .builtFrom(parts -> edge(null, parts.get(Parts.ON), parts.get(Parts.TO)))
            .when(edge -> edge.via() == null && edge.to() instanceof Activity);

    /** The wire from {@code via}'s card, or an activity's for null, on {@code on} to {@code to}; null for none. */
    private static Flow.Edge edge(Flow via, Outcome on, Activity to) {
        if (on == null || to == null) return null;
        return via == null ? new Flow.Edge(on, to) : new Flow.Edge(via, on, to);
    }

    /** {@code Flow.edge(Outcome, Flow)}: a wire from an activity into a sub-flow card. */
    public static final DeclaredCall<Flow.Edge> EDGE_INTO_SUBFLOW_SHAPE = ComponentType.part(Flow.Edge.class)
            .<Outcome, Flow, Flow.Edge>writtenAs(Flow::edge, Flow.Edge::on, edge -> innerOf(edge.to()))
            .when(edge -> edge.via() == null && edge.to() instanceof Flow.Sub);

    /**
     * {@code Flow.edge(Flow via, Outcome, Class)}: a wire out of a sub-flow card, on the outcome its flow ended on,
     * to an activity.
     */
    public static final DeclaredCall<Flow.Edge> EDGE_OUT_OF_SUBFLOW_SHAPE = ComponentType.part(Flow.Edge.class)
            .<Flow, Outcome, Class<? extends Outcome>, Flow.Edge>writtenAs(Flow::edge, Parts.VIA.part(),
                    Parts.ON.part(), Parts.TO.part())
            .builtFrom(parts -> parts.get(Parts.VIA) == null ? null
                    : edge(parts.get(Parts.VIA), parts.get(Parts.ON), parts.get(Parts.TO)))
            .when(edge -> edge.via() != null && edge.to() instanceof Activity);

    /** {@code Flow.edge(Flow via, Outcome, Flow)}: a wire from one sub-flow card to another. */
    public static final DeclaredCall<Flow.Edge> EDGE_BETWEEN_SUBFLOWS_SHAPE = ComponentType.part(Flow.Edge.class)
            .<Flow, Outcome, Flow, Flow.Edge>writtenAs(Flow::edge, Flow.Edge::via, Flow.Edge::on,
                    edge -> innerOf(edge.to()))
            .when(edge -> edge.via() != null && edge.to() instanceof Flow.Sub);

    /** {@code Flow.limits(int, int)}. */
    public static final DeclaredCall<Flow.Limits> LIMITS_SHAPE = ComponentType.part(Flow.Limits.class)
            .writtenAs(Flow::limits, Flow.Limits::maxSteps, Flow.Limits::stepDelayMs);

    /**
     * {@code FlowLayout.of(Map<String, Cards>, boolean)}: the card positions, the {@code FLOW_LAYOUT} value beside
     * the run, keyed by flow. The map is the host's to write ({@code Map.ofEntries}); its values are
     * {@link #CARDS_SHAPE}s. A bot nobody has laid out is written {@code FlowLayout.NONE}.
     */
    public static final DeclaredCall<FlowLayout> LAYOUT_SHAPE = ComponentType.part(FlowLayout.class)
            .writtenAs(FlowLayout::of, Parts.FLOWS.part(), Parts.GO_HOME.part(on -> on == null || on))
            .constants(FlowLayout.NONE);

    /** {@code FlowLayout.cards(Map<String, Spot>)}: one flow's cards, each a {@link #SPOT_SHAPE}. */
    public static final DeclaredCall<FlowLayout.Cards> CARDS_SHAPE = ComponentType.part(FlowLayout.Cards.class)
            .writtenAs(FlowLayout::cards, Parts.SPOTS.part());

    /** {@code FlowLayout.at(int, int)}. */
    public static final DeclaredCall<FlowLayout.Spot> SPOT_SHAPE = ComponentType.part(FlowLayout.Spot.class)
            .writtenAs(FlowLayout::at, FlowLayout.Spot::x, FlowLayout.Spot::y);

    /**
     * The flow's four records — a flow, a card and a wire each spelled more than one way, the activity's spelling
     * first — the run and the layout's three, which is what {@code SdkPlugin.componentTypes()} hands the host.
     */
    public static final List<ComponentType<?>> ALL = List.of(FLOW_SHAPE, FLOW_FROM_SUBFLOW_SHAPE, STEP_SHAPE,
            SUBFLOW_STEP_SHAPE, EDGE_SHAPE, EDGE_INTO_SUBFLOW_SHAPE, EDGE_OUT_OF_SUBFLOW_SHAPE,
            EDGE_BETWEEN_SUBFLOWS_SHAPE, LIMITS_SHAPE, RUN_SHAPE, LAYOUT_SHAPE, CARDS_SHAPE, SPOT_SHAPE);


    // ---- what an editor builds a flow with -------------------------------------------------------------

    /**
     * {@code Flow.activity}'s step for an activity the editor holds by name: what the factory makes from the
     * class, switched on and with no links.
     */
    public static Flow.Step step(Activity activity, ActivityBody<?> body) {
        return new Flow.Step(activity, body, "", true, false, false, Activity.NONE);
    }

    /**
     * An {@link ActivityBody} that only knows what it is written as — {@code Collect::body} — for an editor
     * assembling a {@link Flow} it will never run.
     */
    public static ActivityBody<Outcome> body(String source) {
        return new Named(source == null ? "" : source);
    }

    /**
     * The run's home or setup that only knows what it is written as — {@code Gamebot::goHome} — or {@code null}.
     */
    public static Runnable method(String source) {
        return source == null || source.isBlank() ? null : new NamedMethod(source);
    }

    /**
     * How {@code body} is written, or {@code ""} for a real one.
     *
     * <p>A live method reference has nothing to spell it back as, so it answers blank — which the editor
     * reads as "no body named yet" and the initialiser writer refuses, rather than inventing a name.
     */
    public static String sourceOf(ActivityBody<?> body) {
        return body instanceof Named(String source) ? source : "";
    }

    /** How the run's home or setup is written, or {@code ""} for none or a real one. */
    public static String sourceOf(Runnable method) {
        return method instanceof NamedMethod(String source) ? source : "";
    }

    /**
     * An {@link ActivityBody} that is a <em>name</em> and not a body.
     *
     * <p>What the editor reads out of a file is the text {@code Collect::body}; there is no classpath to
     * resolve it against and nothing to call. Running one throws, which is honest: a flow the editor
     * assembled was never meant to be run.
     */
    record Named(String source) implements ActivityBody<Outcome> {

        @Override
        public Outcome run() {
            throw new UnsupportedOperationException(
                    "\"" + source + "\" was read out of a file by the editor and is a name, not a body");
        }
    }

    /** The run's home or setup as a name, {@code Gamebot::goHome}: {@link Named}'s counterpart, and as honest. */
    record NamedMethod(String source) implements Runnable {

        @Override
        public void run() {
            throw new UnsupportedOperationException(
                    "\"" + source + "\" was read out of a file by the editor and is a name, not a method");
        }
    }
}
