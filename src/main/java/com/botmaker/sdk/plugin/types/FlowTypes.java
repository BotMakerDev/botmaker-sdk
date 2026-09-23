package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;

import java.util.List;

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

    private static String strip(String wire) {
        return wire == null ? "" : wire.strip();
    }

    /** A blank body is the card nobody has written yet, and it is written as the constant that says so. */
    private static String bodyLiteral(String reference) {
        return strip(reference).isEmpty() ? NO_BODY : reference;
    }

    /**
     * The body as the editor draws it: blank for the constant that says "not written yet", which is what a
     * card with no method behind it is, and anything else exactly as written.
     */
    private static String bodyOf(String written) {
        String source = strip(written);
        boolean none = source.equals(NO_BODY) || source.equals(ActivityBody.class.getSimpleName() + ".NONE");
        return none ? "" : written;
    }

    // ---- the five containers ---------------------------------------------------------------------------

    /** {@code Flow.of(List<Activity>, List<Edge>, List<Preset>, String, Limits)}. */
    public static final ComponentType<Flow> FLOW_SHAPE = new Fixed<>(Flow.class, "of") {
        @Override
        public List<Object> components(Flow value) {
            return value == null ? componentsOfNone()
                    : List.of(value.activities(), value.edges(), value.presets(), value.start(),
                    value.limits());
        }

        @Override
        public Flow build(List<Object> parts) {
            return parts.size() != 5 ? Flow.NONE
                    : new Flow(list(parts.get(0)), list(parts.get(1)), list(parts.get(2)), text(parts.get(3)),
                    parts.get(4) instanceof Flow.Limits l ? l : Flow.Limits.DEFAULT);
        }

        @Override
        public List<Class<?>> componentTypes() {
            return List.of(List.class, List.class, List.class, String.class, Flow.Limits.class);
        }

        private List<Object> componentsOfNone() {
            return List.of(List.of(), List.of(), List.of(), "", Flow.Limits.DEFAULT);
        }
    };

    /** {@code Flow.activity(ActivityBody, String, String, boolean, boolean, boolean, List<String>)}. */
    public static final ComponentType<Flow.Activity> ACTIVITY_SHAPE =
            new Fixed<>(Flow.Activity.class, "activity", Flow.class) {
                @Override
                public List<Object> components(Flow.Activity value) {
                    return value == null ? List.of("", "", "", true, false, false, List.of())
                            : List.of(body(value.body()), value.name(), value.description(), value.enabled(),
                            value.goHome(), value.popupCheck(), value.outcomes());
                }

                /**
                 * A body crosses as the source it is written as, never as the functional object.
                 *
                 * <p>An {@link ActivityBody} the editor read out of a file is a {@code String} — it was never
                 * instantiated, because the editor has no classpath for the bot it is drawing — and one a
                 * running bot holds is a real method reference with nothing to spell it back as. Both answer
                 * the text, and a live one answers empty, which reads as "written by hand" and is refused
                 * rather than guessed at.
                 */
                private Object body(ActivityBody body) {
                    // A card with no method yet is written as the constant that says so: the host writes a
                    // component nothing declares verbatim, and an empty one has no Java at all.
                    return bodyLiteral(sourceOf(body));
                }

                @Override
                public Flow.Activity build(List<Object> parts) {
                    if (parts.size() != 7) return null;
                    return new Flow.Activity(new Named(bodyOf(text(parts.get(0)))), text(parts.get(1)),
                            text(parts.get(2)), flag(parts.get(3)), flag(parts.get(4)), flag(parts.get(5)),
                            list(parts.get(6)));
                }

                @Override
                public List<Class<?>> componentTypes() {
                    return List.of(ActivityBody.class, String.class, String.class,
                            boolean.class, boolean.class, boolean.class, List.class);
                }
            };

    /** {@code Flow.preset(String, List<String>)}. */
    public static final ComponentType<Flow.Preset> PRESET_SHAPE =
            new Fixed<>(Flow.Preset.class, "preset", Flow.class) {
                @Override
                public List<Object> components(Flow.Preset value) {
                    return value == null ? List.of("", List.of())
                            : List.of(value.name(), value.activities());
                }

                @Override
                public Flow.Preset build(List<Object> parts) {
                    return parts.size() != 2 ? null
                            : new Flow.Preset(text(parts.get(0)), list(parts.get(1)));
                }

                @Override
                public List<Class<?>> componentTypes() {
                    return List.of(String.class, List.class);
                }
            };

    /** {@code Flow.edge(String, String, String)}. */
    public static final ComponentType<Flow.Edge> EDGE_SHAPE =
            new Fixed<>(Flow.Edge.class, "edge", Flow.class) {
                @Override
                public List<Object> components(Flow.Edge value) {
                    return value == null ? List.of("", "", "")
                            : List.of(value.from(), value.to(), value.outcome());
                }

                @Override
                public Flow.Edge build(List<Object> parts) {
                    return parts.size() != 3 ? null
                            : new Flow.Edge(text(parts.get(0)), text(parts.get(1)), text(parts.get(2)));
                }

                @Override
                public List<Class<?>> componentTypes() {
                    return List.of(String.class, String.class, String.class);
                }
            };

    /** {@code Flow.limits(int, int)}. */
    public static final ComponentType<Flow.Limits> LIMITS_SHAPE =
            new Fixed<>(Flow.Limits.class, "limits", Flow.class) {
                @Override
                public List<Object> components(Flow.Limits value) {
                    Flow.Limits limits = value == null ? Flow.Limits.DEFAULT : value;
                    return List.of(limits.maxSteps(), limits.stepDelayMs());
                }

                @Override
                public Flow.Limits build(List<Object> parts) {
                    return parts.size() != 2 ? Flow.Limits.DEFAULT
                            : new Flow.Limits(number(parts.get(0)), number(parts.get(1)));
                }

                @Override
                public List<Class<?>> componentTypes() {
                    return List.of(int.class, int.class);
                }
            };

    /** All five, which is what {@code SdkPlugin.componentTypes()} hands the host. */
    public static final List<ComponentType<?>> ALL =
            List.of(FLOW_SHAPE, ACTIVITY_SHAPE, PRESET_SHAPE, EDGE_SHAPE, LIMITS_SHAPE);

    /**
     * An {@link ActivityBody} that is a <em>name</em> and not a body.
     *
     * <p>What the editor reads out of a file is the text {@code Collect::body}; there is no classpath to
     * resolve it against and nothing to call. Running one throws, which is honest: a flow the editor
     * assembled was never meant to be run, and a body that silently did nothing would be a bot that walks
     * its flow reporting nothing.
     */
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

    record Named(String source) implements ActivityBody {

        @Override
        public com.botmaker.sdk.api.bot.Outcome run(com.botmaker.sdk.api.bot.ActivityContext ctx) {
            throw new UnsupportedOperationException(
                    "\"" + source + "\" was read out of a file by the editor and is a name, not a body");
        }
    }

    // ---- plumbing --------------------------------------------------------------------------------------

    /**
     * The three things all five of these say the same way: the record, the factory that writes it, and the
     * class that factory is declared on.
     *
     * <p>A call with the wrong number of arguments is <b>not this shape</b>, and every {@code build} below
     * says so by answering the record's own empty value rather than guessing: it is a call to something
     * else, or to a newer version of this factory, and either way lining its parts up with these would be
     * rewriting code on a hunch. The host's answer to an expression it cannot read whole is to show it
     * read-only and leave it exactly as written.
     */
    private abstract static class Fixed<C> implements ComponentType<C> {

        private final Class<C> type;
        private final String factory;
        private final Class<?> owner;

        Fixed(Class<C> type, String factory) {
            this(type, factory, type);
        }

        Fixed(Class<C> type, String factory, Class<?> owner) {
            this.type = type;
            this.factory = factory;
            this.owner = owner;
        }

        @Override
        public final Class<C> type() {
            return type;
        }

        @Override
        public final Class<?> factoryOwner() {
            return owner;
        }

        @Override
        public final String factory() {
            return factory;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object part) {
        return part instanceof List<?> items ? List.copyOf((List<T>) items) : List.of();
    }

    private static String text(Object part) {
        return part instanceof String s ? s : "";
    }

    private static boolean flag(Object part) {
        return part instanceof Boolean b && b;
    }

    private static int number(Object part) {
        return part instanceof Number n ? n.intValue() : 0;
    }
}
