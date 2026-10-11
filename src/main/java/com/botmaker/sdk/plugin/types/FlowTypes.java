package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            .writtenAs(Flow::of, Flow::steps, Flow::edges, flow -> null, Flow::limits)
            .components(flow -> List.of(flow.steps(), flow.edges(), flow.start(), flow.limits()))
            .build(FlowTypes::flow)
            .constants(Flow.NONE);

    /** {@code flow} as it can be written: {@link Flow#NONE} once it has no activity left to start at. */
    public static Flow written(Flow flow) {
        return flow == null || flow.steps().isEmpty() ? Flow.NONE : flow;
    }

    private static Flow flow(List<Object> parts) {
        if (parts.size() != 4 || !(parts.get(2) instanceof Activity start)
                || !(parts.get(3) instanceof Flow.Limits limits)) {
            return null;
        }
        List<Flow.Step> steps = all(parts.get(0), Flow.Step.class);
        List<Flow.Edge> edges = all(parts.get(1), Flow.Edge.class);
        if (steps == null || edges == null) return null;
        return new Flow(steps, edges, start, limits);
    }

    /**
     * {@code Run.of(Flow)} and its {@code .home(…)} and {@code .setup(…)} links, each written only when the run has
     * one. The flow is a constant of the bot's {@code Flows}, and the host writes it as that constant's name
     * because the value equals it — {@code Run.of(Flows.MAIN)}.
     */
    public static final DeclaredCall<Run> RUN_SHAPE = ComponentType.part(Run.class)
            .writtenAs(Run::of, Run::flow)
            .<Runnable>with(Run::home, run -> sourceOf(run.home()), FlowTypes::withHome)
            .<Runnable>with(Run::setup, run -> sourceOf(run.setup()), FlowTypes::withSetup)
            .constants(Run.NONE);

    private static Run withHome(Run run, Object part) {
        if (!(part instanceof String source)) return null;
        return run.home(method(source));
    }

    private static Run withSetup(Run run, Object part) {
        if (!(part instanceof String source)) return null;
        return run.setup(method(source));
    }

    /**
     * {@code Flow.activity(Class, ActivityBody)} and its links: {@code .described(String)}, {@code .off()},
     * {@code .goesHome()}, {@code .checksPopups()}, {@code .switchable()}, {@code .whenOff(Class)}, each written
     * only when the step is not as {@code Flow.activity} makes it. A new step is on, so its flag is the negative
     * {@code off()}.
     *
     * <p>A body crosses as the source it is written as, never as the functional object; a live one answers
     * empty, which reads as "written by hand" and is refused rather than guessed at. A card with no method yet is
     * written as {@code ActivityBody.none()}.
     */
    public static final DeclaredCall<Flow.Step> STEP_SHAPE = ComponentType.part(Flow.Step.class)
            .<Class<Outcome>, ActivityBody<Outcome>, Flow.Step>writtenAs(Flow::activity, step -> null, step -> null)
            .components(value -> List.of(value.activity(), bodyLiteral(sourceOf(value.body()))))
            .build(FlowTypes::step)
            .with(Flow.Step::described, Flow.Step::description)
            .flag(Flow.Step::off, step -> !step.enabled())
            .flag(Flow.Step::goesHome, Flow.Step::goHome)
            .flag(Flow.Step::checksPopups, Flow.Step::popupCheck)
            .flag(Flow.Step::switchable, Flow.Step::runnerSwitch)
            .<Class<? extends Outcome>>with(Flow.Step::whenOff, Flow.Step::whenOff, FlowTypes::withOff);

    private static Flow.Step step(List<Object> parts) {
        if (parts.size() != 2 || !(parts.get(0) instanceof Activity activity) || activity.isNone()
                || !(parts.get(1) instanceof String body)) {
            return null;
        }
        return step(activity, new Named(bodyOf(body)));
    }

    private static Flow.Step withOff(Flow.Step step, Object part) {
        if (!(part instanceof Activity to)) return null;
        return whenOff(step, to);
    }

    /** {@code step} going on to {@code to} when switched off: {@code .whenOff(…)} for an activity held by name. */
    public static Flow.Step whenOff(Flow.Step step, Activity to) {
        return new Flow.Step(step.activity(), step.body(), step.description(), step.enabled(), step.goHome(),
                step.popupCheck(), step.runnerSwitch(), to);
    }

    /** {@code Flow.edge(Outcome, Class)}: the activity it leaves is the outcome's. */
    public static final DeclaredCall<Flow.Edge> EDGE_SHAPE = ComponentType.part(Flow.Edge.class)
            .writtenAs(Flow::edge, Flow.Edge::on, edge -> null)
            .components(edge -> List.of(edge.on(), edge.to()))
            .build(FlowTypes::edge);

    private static Flow.Edge edge(List<Object> parts) {
        if (parts.size() != 2 || !(parts.get(0) instanceof Outcome on) || !(parts.get(1) instanceof Activity to)) {
            return null;
        }
        return new Flow.Edge(on, to);
    }

    /** {@code Flow.limits(int, int)}. */
    public static final DeclaredCall<Flow.Limits> LIMITS_SHAPE = ComponentType.part(Flow.Limits.class)
            .writtenAs(Flow::limits, Flow.Limits::maxSteps, Flow.Limits::stepDelayMs);

    /**
     * {@code FlowLayout.of(Map<String, Cards>, boolean)}: the card positions, the {@code FLOW_LAYOUT} value beside
     * the run, keyed by flow. The map is the host's to write ({@code Map.ofEntries}); its values are
     * {@link #CARDS_SHAPE}s. A bot nobody has laid out is written {@code FlowLayout.NONE}.
     */
    public static final DeclaredCall<FlowLayout> LAYOUT_SHAPE = ComponentType.part(FlowLayout.class)
            .writtenAs(FlowLayout::of, FlowLayout::flows, FlowLayout::goHomeByDefault)
            .build(FlowTypes::layout)
            .constants(FlowLayout.NONE);

    /** The layout back, keeping every entry that is a name and cards: a stray entry costs one flow, not all. */
    private static FlowLayout layout(List<Object> parts) {
        if (parts.size() != 2) return null;
        Map<String, FlowLayout.Cards> flows = new LinkedHashMap<>();
        if (parts.get(0) instanceof Map<?, ?> map) {
            map.forEach((name, cards) -> {
                if (name instanceof String n && cards instanceof FlowLayout.Cards c) flows.put(n, c);
            });
        }
        return FlowLayout.of(flows, !(parts.get(1) instanceof Boolean b) || b);
    }

    /** {@code FlowLayout.cards(Map<String, Spot>)}: one flow's cards, each a {@link #SPOT_SHAPE}. */
    public static final DeclaredCall<FlowLayout.Cards> CARDS_SHAPE = ComponentType.part(FlowLayout.Cards.class)
            .writtenAs(FlowLayout::cards, FlowLayout.Cards::spots)
            .build(FlowTypes::cards);

    /** One flow's cards back, keeping every entry that is a name and a spot: a stray one costs one card. */
    private static FlowLayout.Cards cards(List<Object> parts) {
        if (parts.size() != 1) return null;
        Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
        if (parts.getFirst() instanceof Map<?, ?> map) {
            map.forEach((name, spot) -> {
                if (name instanceof String n && spot instanceof FlowLayout.Spot s) spots.put(n, s);
            });
        }
        return FlowLayout.cards(spots);
    }

    /** {@code FlowLayout.at(int, int)}. */
    public static final DeclaredCall<FlowLayout.Spot> SPOT_SHAPE = ComponentType.part(FlowLayout.Spot.class)
            .writtenAs(FlowLayout::at, FlowLayout.Spot::x, FlowLayout.Spot::y);

    /** The flow's four, the run and the layout's three, which is what {@code SdkPlugin.componentTypes()} hands the host. */
    public static final List<ComponentType<?>> ALL = List.of(FLOW_SHAPE, STEP_SHAPE, EDGE_SHAPE, LIMITS_SHAPE,
            RUN_SHAPE, LAYOUT_SHAPE, CARDS_SHAPE, SPOT_SHAPE);

    /** Every element of {@code list} as a {@code type}, or {@code null} when one is not. */
    private static <E> List<E> all(Object list, Class<E> type) {
        if (!(list instanceof List<?> given)) return null;
        List<E> out = new ArrayList<>(given.size());
        for (Object element : given) {
            if (!type.isInstance(element)) return null;
            out.add(type.cast(element));
        }
        return out;
    }

    // ---- what an editor builds a flow with -------------------------------------------------------------

    /**
     * {@code Flow.activity}'s step for an activity the editor holds by name: what the factory makes from the
     * class, switched on and with no links.
     */
    public static Flow.Step step(Activity activity, ActivityBody<?> body) {
        return new Flow.Step(activity, body, "", true, false, false, false, Activity.NONE);
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
