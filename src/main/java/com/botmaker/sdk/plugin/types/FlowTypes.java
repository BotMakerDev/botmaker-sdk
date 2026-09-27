package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.toolkit.Types;
import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.botmaker.plugin.toolkit.Types.flag;
import static com.botmaker.plugin.toolkit.Types.method;
import static com.botmaker.plugin.toolkit.Types.text;
import static com.botmaker.plugin.toolkit.Types.whole;

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
 * be rewriting code on a hunch. The parts are each factory's parameters ({@link Types#call}), so the two
 * cannot drift apart.
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
    public static final ComponentType<Flow> FLOW_SHAPE = Types.call(Flow.class,
            method(Flow.class, "of", List.class, List.class, List.class, String.class, Flow.Limits.class),
            value -> value == null ? List.of(List.of(), List.of(), List.of(), "", Flow.Limits.DEFAULT)
                    : List.of(value.activities(), value.edges(), value.presets(), value.start(), value.limits()),
            parts -> parts.size() != 5 ? Flow.NONE
                    : new Flow(list(parts.get(0)), list(parts.get(1)), list(parts.get(2)), text(parts, 3),
                    parts.get(4) instanceof Flow.Limits l ? l : Flow.Limits.DEFAULT));

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
     */
    public static final ComponentType<Flow.Activity> ACTIVITY_SHAPE = Types.call(Flow.Activity.class,
            method(Flow.class, "activity", ActivityBody.class, String.class, String.class, boolean.class,
                    boolean.class, boolean.class, List.class),
            value -> value == null ? List.of("", "", "", true, false, false, List.of())
                    : List.of(bodyLiteral(sourceOf(value.body())), value.name(), value.description(),
                    value.enabled(), value.goHome(), value.popupCheck(), value.outcomes()),
            parts -> parts.size() != 7 ? null
                    : new Flow.Activity(new Named(bodyOf(text(parts, 0))), text(parts, 1), text(parts, 2),
                    flag(parts, 3), flag(parts, 4), flag(parts, 5), list(parts.get(6))));

    /** {@code Flow.preset(String, List<String>)}. */
    public static final ComponentType<Flow.Preset> PRESET_SHAPE = Types.call(Flow.Preset.class,
            method(Flow.class, "preset", String.class, List.class),
            value -> value == null ? List.of("", List.of()) : List.of(value.name(), value.activities()),
            parts -> parts.size() != 2 ? null : new Flow.Preset(text(parts, 0), list(parts.get(1))));

    /** {@code Flow.edge(String, String, String)}. */
    public static final ComponentType<Flow.Edge> EDGE_SHAPE = Types.call(Flow.Edge.class,
            method(Flow.class, "edge", String.class, String.class, String.class),
            value -> value == null ? List.of("", "", "") : List.of(value.from(), value.to(), value.outcome()),
            parts -> parts.size() != 3 ? null : new Flow.Edge(text(parts, 0), text(parts, 1), text(parts, 2)));

    /** {@code Flow.limits(int, int)}. */
    public static final ComponentType<Flow.Limits> LIMITS_SHAPE = Types.call(Flow.Limits.class,
            method(Flow.class, "limits", int.class, int.class),
            value -> {
                Flow.Limits limits = value == null ? Flow.Limits.DEFAULT : value;
                return List.of(limits.maxSteps(), limits.stepDelayMs());
            },
            parts -> parts.size() != 2 ? Flow.Limits.DEFAULT : new Flow.Limits(whole(parts, 0), whole(parts, 1)));

    /**
     * {@code FlowLayout.of(Map<String, Spot>, boolean)}: the card positions, the {@code @Managed("flow.layout")}
     * value beside the flow (2026-09-27). The map is the host's to write ({@code Map.ofEntries}); its values
     * are {@link #SPOT_SHAPE}s. A flow nobody has laid out is written {@code FlowLayout.NONE}.
     */
    public static final ComponentType<FlowLayout> LAYOUT_SHAPE = Types.call(FlowLayout.class,
                    method(FlowLayout.class, "of", Map.class, boolean.class),
                    value -> {
                        FlowLayout layout = value == null ? FlowLayout.NONE : value;
                        return List.of(layout.spots(), layout.goHomeByDefault());
                    },
                    FlowTypes::layout)
            .constants(Types.constant(FlowLayout.class, "NONE"));

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
    public static final ComponentType<FlowLayout.Spot> SPOT_SHAPE = Types.call(FlowLayout.Spot.class,
            method(FlowLayout.class, "at", int.class, int.class),
            value -> {
                FlowLayout.Spot spot = value == null ? new FlowLayout.Spot(0, 0) : value;
                return List.of(spot.x(), spot.y());
            },
            parts -> parts.size() != 2 ? null : FlowLayout.at(whole(parts, 0), whole(parts, 1)));

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
