package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shapes an {@code @SdkValue} value of this plugin's takes: the flow's five records, as the components
 * that go inside the calls that write them.
 *
 * <h2>Why a flow is five declarations and not one type</h2>
 *
 * <p>A {@link ComponentType} is what lets the editor take an expression apart into typed parts, change one
 * of them and put the rest back exactly as written. One opaque {@code FLOW} leaf with a codec would give the
 * editor a string — which is where this whole design started, and what it exists to leave behind. Five
 * declarations give it {@code Flow.of(List.of(Flow.activity(…), …), List.of(…), Activities.COLLECT,
 * Flow.limits(…))} as a tree it can walk, typed at every tip. The tips that name an activity or an outcome are
 * the {@code ACTIVITY} and {@code OUTCOME} types of {@code SdkTypes}, read and written as the bot's constants.
 *
 * <p><b>None of them implements {@code PluginType}</b>, and that is why the two interfaces are independent:
 * a {@code Step}, an {@code Edge}, a {@code Preset} and a {@code Limits} are parts of a flow and are
 * never picked on their own, so extending it would owe each a {@code fresh()} and an {@code editor()}
 * nothing would ever call.
 *
 * <p><b>A call with the wrong number of arguments is not this shape</b>, and every {@code build} below says
 * so by answering {@code null} or the record's own empty value rather than guessing: it is a call to
 * something else, or to a newer version of this factory, and either way lining its parts up with these would
 * be rewriting code on a hunch. Each factory is a method reference and each part an accessor, and the value
 * is built back by invoking the factory, so the call and its parts cannot drift apart.
 *
 * <h2>A body is a name, and it crosses as the source it is written as</h2>
 *
 * <p>An activity's body is written {@code Collect::body}. It is not made into a live object — the editor
 * never runs a bot's code, and has no classpath to resolve one against — so it crosses as the
 * {@code String} the file writes, and is written back exactly so. The flow editor checks what a person
 * types into the field ({@code FlowNames.isMethodReference}); this class checks nothing.
 *
 * <p><b>It stays text, and that was decided.</b> This is not a plugin writing Java: the host
 * writes the text through its own source-leaf path (Studio's {@code ValueWriter.ofClass}), which parses it into
 * a tree, and a rename in the real {@code Sdk.java} already follows the binding. A contract {@code MethodName}
 * type was considered and declined — one contract type, grammar changes and a flow-editor rewrite to save
 * about forty lines. Do not propose it again without new facts.
 */
public final class FlowTypes {

    private FlowTypes() {}

    // ---- the leaves' codecs ----------------------------------------------------------------------------

    /** How the constant for "drawn, not written yet" is spelled in a file — fully qualified, as all of these are. */
    private static final String NO_BODY = ActivityBody.class.getName() + ".NONE";

    /** A blank body is the card nobody has written yet, and it is written as the constant that says so. */
    private static String bodyLiteral(String reference) {
        return reference == null || reference.isBlank() ? NO_BODY : reference;
    }

    /**
     * The body as the editor draws it: blank for the constant that says "not written yet", which is what a
     * card with no method behind it is, and anything else exactly as written.
     */
    private static String bodyOf(String written) {
        String source = written.strip();
        boolean none = source.equals(NO_BODY) || source.equals(ActivityBody.class.getSimpleName() + ".NONE");
        return none ? "" : written;
    }

    // ---- the five containers ---------------------------------------------------------------------------

    /** {@code Flow.of(List<Step>, List<Edge>, List<Preset>, Activity, Limits)}. */
    public static final DeclaredCall<Flow> FLOW_SHAPE = ComponentType.part(Flow.class)
            .writtenAs(Flow::of, Flow::steps, Flow::edges, Flow::presets, Flow::start, Flow::limits);

    /**
     * {@code Flow.activity(Activity, ActivityBody)} and its links: {@code .described(String)}, {@code .off()},
     * {@code .goesHome()}, {@code .checksPopups()}, {@code .reports(List<Outcome>)}, each written only when the
     * step is not as {@code Flow.activity} makes it (2026-10-09). A new step is on, so its flag is the negative
     * {@code off()}: a flag only turns a thing on.
     *
     * <p>A body crosses as the source it is written as, never as the functional object. An
     * {@link ActivityBody} the editor read out of a file is a {@code String} — it was never instantiated,
     * because the editor has no classpath for the bot it is drawing — and one a running bot holds is a real
     * method reference with nothing to spell it back as. Both answer the text, and a live one answers empty,
     * which reads as "written by hand" and is refused rather than guessed at. A card with no method yet is
     * written as the constant that says so: the host writes a component nothing declares verbatim, and an
     * empty one has no Java at all.
     *
     * <p>So this is the one part here taken apart and built back by hand ({@code components} and
     * {@code build}): the text is not an {@code ActivityBody}, and no accessor or factory could hand it over.
     * It is the last place a plugin's value carries Java text, and it is known. The links' parts follow these
     * two, as every wither's does.
     */
    public static final DeclaredCall<Flow.Step> STEP_SHAPE = ComponentType.part(Flow.Step.class)
            .writtenAs(Flow::activity, Flow.Step::activity, Flow.Step::body)
            .components(value -> List.of(value.activity(), bodyLiteral(sourceOf(value.body()))))
            .build(FlowTypes::step)
            .with(Flow.Step::described, Flow.Step::description)
            .flag(Flow.Step::off, step -> !step.enabled())
            .flag(Flow.Step::goesHome, Flow.Step::goHome)
            .flag(Flow.Step::checksPopups, Flow.Step::popupCheck)
            .with(Flow.Step::reports, Flow.Step::outcomes);

    private static Flow.Step step(List<Object> parts) {
        if (parts.size() != 2 || !(parts.get(0) instanceof Activity activity)
                || !(parts.get(1) instanceof String body)) {
            return null;
        }
        return Flow.activity(activity, new Named(bodyOf(body)));
    }

    /** {@code Flow.preset(String, List<Activity>)}. */
    public static final DeclaredCall<Flow.Preset> PRESET_SHAPE = ComponentType.part(Flow.Preset.class)
            .writtenAs(Flow::preset, Flow.Preset::name, Flow.Preset::activities);

    /** {@code Flow.edge(Activity, Activity, Outcome)}. */
    public static final DeclaredCall<Flow.Edge> EDGE_SHAPE = ComponentType.part(Flow.Edge.class)
            .writtenAs(Flow::edge, Flow.Edge::from, Flow.Edge::to, Flow.Edge::outcome);

    /** {@code Flow.limits(int, int)}. */
    public static final DeclaredCall<Flow.Limits> LIMITS_SHAPE = ComponentType.part(Flow.Limits.class)
            .writtenAs(Flow::limits, Flow.Limits::maxSteps, Flow.Limits::stepDelayMs);

    /**
     * {@code FlowLayout.of(Map<String, Spot>, boolean)}: the card positions, the {@code FLOW_LAYOUT}
     * value beside the flow. The map is the host's to write ({@code Map.ofEntries}); its values
     * are {@link #SPOT_SHAPE}s. A flow nobody has laid out is written {@code FlowLayout.NONE}.
     */
    public static final DeclaredCall<FlowLayout> LAYOUT_SHAPE = ComponentType.part(FlowLayout.class)
            .writtenAs(FlowLayout::of, FlowLayout::spots, FlowLayout::goHomeByDefault)
            .build(FlowTypes::layout)
            .constants(FlowLayout.NONE);

    /** The layout back, keeping every entry that is a name and a spot: a stray entry costs one card, not all. */
    private static FlowLayout layout(List<Object> parts) {
        if (parts.size() != 2) return null;
        Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
        if (parts.get(0) instanceof Map<?, ?> map) {
            map.forEach((name, spot) -> {
                if (name instanceof String n && spot instanceof FlowLayout.Spot s) spots.put(n, s);
            });
        }
        return FlowLayout.of(spots, !(parts.get(1) instanceof Boolean b) || b);
    }

    /** {@code FlowLayout.at(int, int)}. */
    public static final DeclaredCall<FlowLayout.Spot> SPOT_SHAPE = ComponentType.part(FlowLayout.Spot.class)
            .writtenAs(FlowLayout::at, FlowLayout.Spot::x, FlowLayout.Spot::y);

    /** The flow's five and the layout's two, which is what {@code SdkPlugin.componentTypes()} hands the host. */
    public static final List<ComponentType<?>> ALL = List.of(FLOW_SHAPE, STEP_SHAPE, PRESET_SHAPE,
            EDGE_SHAPE, LIMITS_SHAPE, LAYOUT_SHAPE, SPOT_SHAPE);

    /**
     * An {@link ActivityBody} that only knows what it is written as — {@code Collect::body} — for an editor
     * assembling a {@link Flow} it will never run.
     */
    public static ActivityBody body(String source) {
        return new Named(source == null ? "" : source);
    }

    /**
     * How {@code body} is written, or {@code ""} for a real one.
     *
     * <p>A live method reference has nothing to spell it back as, so it answers blank — which the editor
     * reads as "no body named yet" and the initialiser writer refuses, rather than inventing a name.
     */
    public static String sourceOf(ActivityBody body) {
        return body instanceof Named(String source) ? source : "";
    }

    /**
     * An {@link ActivityBody} that is a <em>name</em> and not a body.
     *
     * <p>What the editor reads out of a file is the text {@code Collect::body}; there is no classpath to
     * resolve it against and nothing to call. Running one throws, which is honest: a flow the editor
     * assembled was never meant to be run, and a body that silently did nothing would be a bot that walks
     * its flow reporting nothing.
     */
    record Named(String source) implements ActivityBody {

        @Override
        public com.botmaker.sdk.api.bot.Outcome run() {
            throw new UnsupportedOperationException(
                    "\"" + source + "\" was read out of a file by the editor and is a name, not a body");
        }
    }
}
