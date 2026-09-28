package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shapes a {@code @Managed} value of this plugin's takes: the flow's five records, as the components
 * that go inside the calls that write them.
 *
 * <h2>Why a flow is five declarations and not one type</h2>
 *
 * <p>A {@link ComponentType} is what lets the editor take an expression apart into typed parts, change one
 * of them and put the rest back exactly as written. One opaque {@code FLOW} leaf with a codec would give the
 * editor a string — which is where this whole design started, and what it exists to leave behind. Five
 * declarations give it {@code Flow.of(List.of(Flow.activity(…), …), List.of(…), "Collect", Flow.limits(…))}
 * as a tree it can walk, typed at every tip.
 *
 * <p><b>None of them implements {@code PluginType}</b>, and that is why the two interfaces are independent:
 * an {@code Activity}, an {@code Edge}, a {@code Preset} and a {@code Limits} are parts of a flow and are
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

    /** {@code Flow.of(List<Activity>, List<Edge>, List<Preset>, String, Limits)}. */
    public static final DeclaredCall<Flow> FLOW_SHAPE = ComponentType.part(Flow.class)
            .writtenAs(Flow::of, Flow::activities, Flow::edges, Flow::presets, Flow::start, Flow::limits);

    /**
     * {@code Flow.activity(ActivityBody, String, String, boolean, boolean, boolean, List<String>)}.
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
     * It is the last place a plugin's value carries Java text, and it is known.
     */
    public static final DeclaredCall<Flow.Activity> ACTIVITY_SHAPE = ComponentType.part(Flow.Activity.class)
            .writtenAs(Flow::activity, Flow.Activity::body, Flow.Activity::name, Flow.Activity::description,
                    Flow.Activity::enabled, Flow.Activity::goHome, Flow.Activity::popupCheck, Flow.Activity::outcomes)
            .components(value -> List.of(bodyLiteral(sourceOf(value.body())), value.name(), value.description(),
                    value.enabled(), value.goHome(), value.popupCheck(), value.outcomes()))
            .build(FlowTypes::activity);

    private static Flow.Activity activity(List<Object> parts) {
        if (parts.size() != 7 || !(parts.get(0) instanceof String body) || !(parts.get(1) instanceof String name)
                || !(parts.get(2) instanceof String description) || !(parts.get(3) instanceof Boolean enabled)
                || !(parts.get(4) instanceof Boolean goHome) || !(parts.get(5) instanceof Boolean popupCheck)) {
            return null;
        }
        return new Flow.Activity(new Named(bodyOf(body)), name, description, enabled, goHome, popupCheck,
                list(parts.get(6)));
    }

    /** {@code Flow.preset(String, List<String>)}. */
    public static final DeclaredCall<Flow.Preset> PRESET_SHAPE = ComponentType.part(Flow.Preset.class)
            .writtenAs(Flow::preset, Flow.Preset::name, Flow.Preset::activities);

    /** {@code Flow.edge(String, String, String)}. */
    public static final DeclaredCall<Flow.Edge> EDGE_SHAPE = ComponentType.part(Flow.Edge.class)
            .writtenAs(Flow::edge, Flow.Edge::from, Flow.Edge::to, Flow.Edge::outcome);

    /** {@code Flow.limits(int, int)}. */
    public static final DeclaredCall<Flow.Limits> LIMITS_SHAPE = ComponentType.part(Flow.Limits.class)
            .writtenAs(Flow::limits, Flow.Limits::maxSteps, Flow.Limits::stepDelayMs);

    /**
     * {@code FlowLayout.of(Map<String, Spot>, boolean)}: the card positions, the {@code @Managed("flow.layout")}
     * value beside the flow (2026-09-27). The map is the host's to write ({@code Map.ofEntries}); its values
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
    public static final List<ComponentType<?>> ALL = List.of(FLOW_SHAPE, ACTIVITY_SHAPE, PRESET_SHAPE,
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
        return body instanceof Named named ? named.source() : "";
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
        public com.botmaker.sdk.api.bot.Outcome run(com.botmaker.sdk.api.bot.ActivityContext ctx) {
            throw new UnsupportedOperationException(
                    "\"" + source + "\" was read out of a file by the editor and is a name, not a body");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object part) {
        return part instanceof List<?> items ? List.copyOf((List<T>) items) : List.of();
    }
}
