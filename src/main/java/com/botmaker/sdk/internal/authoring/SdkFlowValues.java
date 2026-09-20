package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueContainer;
import com.botmaker.plugin.api.value.ValueForm;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.basics.values.BasicsValueTypes;
import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Flow;

import java.util.List;
import java.util.Optional;

/**
 * The shapes a {@code @Managed} value of this plugin's takes: the flow's five records as containers, and the
 * two leaves whose value is a <b>name</b> rather than data.
 *
 * <h2>Why a flow is five containers and not one type</h2>
 *
 * <p>A {@link ValueContainer} is what lets the editor take an expression apart into typed parts, change one
 * of them and put the rest back exactly as written. One opaque {@code FLOW} leaf with a codec would give the
 * editor a string — which is where this whole design started, and what it exists to leave behind. Four
 * containers give it {@code Flow.of(List.of(Flow.activity(…), …), List.of(…), "Collect", Flow.limits(…))} as
 * a tree it can walk, with a leaf codec at every tip.
 *
 * <p><b>All five have arity zero</b>, which the contract allows since 2026-09-20: they take no type
 * arguments and still have parts, so {@link ValueContainer#partForms} answers a fixed list and ignores the
 * (empty) arguments. That is the difference between a record a plugin registers and one the <em>bot</em>
 * declares, which is {@code ValueForm.Declared} and read out of the project's own source instead.
 *
 * <h2>The two leaves are names, and their codecs say so</h2>
 *
 * <p>{@link #ACTIVITY_BODY} is written {@code Collect::body} and {@link #CAPTURE_SOURCE} is written
 * {@code CaptureSource.desktop()}. Neither codec parses into a live object — the editor never runs a bot's
 * code — so both read as the {@code String} they are written as, exactly as {@code IMAGE_TEMPLATE} reads as
 * a base name. What each {@code valueOfLiteral} does is <b>refuse anything it did not write</b>: a lambda, a
 * conditional, a call into the user's own code all come back empty, and the editor then shows that value
 * read-only rather than replacing code somebody wrote on purpose.
 */
public final class SdkFlowValues {

    private SdkFlowValues() {}

    /** The heading a picker files these under. */
    private static final String FLOW = "Flow";

    /** An activity's work, named by method reference — {@code Collect::body}. */
    public static final ValueType ACTIVITY_BODY =
            SdkValueTypes.sdk("ACTIVITY_BODY", "Activity body", FLOW, ActivityBody.class, false);

    /** Where pixels are read from — {@code CaptureSource.desktop()}. */
    public static final ValueType CAPTURE_SOURCE =
            SdkValueTypes.sdk("CAPTURE_SOURCE", "Capture source", FLOW, CaptureSource.class, false);

    // ---- the leaves' codecs ----------------------------------------------------------------------------

    /**
     * A method reference, and nothing else.
     *
     * <p>{@code Collect::body} and {@code com.mybot.Collect::body} are both accepted; a lambda, a call and a
     * bare name are not. The rule is purely syntactic because it has to be: the editor has no classpath for
     * the bot it is drawing, and a reference to a class that does not exist is javac's to report, in the
     * user's own file, where it is a compile error naming the line.
     */
    static final com.botmaker.plugin.api.value.ValueCodec<String> BODY_CODEC = SdkValueTypes.codec(
            SdkFlowValues::strip, s -> s, SdkFlowValues::bodyLiteral, SdkFlowValues::methodReference);

    /** How the constant for "drawn, not written yet" is spelled in a file — fully qualified, as all of these are. */
    private static final String NO_BODY = ActivityBody.class.getName() + ".NONE";

    /**
     * One of {@link CaptureSource}'s three canonical factories, written as the SDK writes them.
     *
     * <p>{@code region(…)} narrowings are deliberately not read back: they compose, so a source may be
     * narrowed twice, and a picker that could show the first narrowing and not the second would be worse
     * than one that shows the whole expression read-only and says so.
     */
    static final com.botmaker.plugin.api.value.ValueCodec<String> SOURCE_CODEC = SdkValueTypes.codec(
            SdkFlowValues::strip, s -> s, s -> s, SdkFlowValues::captureSource);

    private static String strip(String wire) {
        return wire == null ? "" : wire.strip();
    }

    /** A blank body is the card nobody has written yet, and it is written as the constant that says so. */
    private static String bodyLiteral(String reference) {
        return strip(reference).isEmpty() ? NO_BODY : reference;
    }

    private static Optional<String> methodReference(String java) {
        String source = strip(java);
        // The one accepted expression that is not a method reference. It reads back as blank, which is what
        // the editor draws as a card with no method behind it — and what it writes out again unchanged.
        if (source.equals(NO_BODY) || source.equals(ActivityBody.class.getSimpleName() + ".NONE")) {
            return Optional.of("");
        }
        return isMethodReference(source) ? Optional.of(source) : Optional.empty();
    }

    /**
     * Whether {@code source} is a method reference — {@code Collect::body}, or {@code com.mybot.Collect::body}.
     *
     * <p>Public because the flow editor asks the same question of what the user types, and one rule about
     * what may be written into a bot's Java is the difference between a field that refuses a form and a
     * codec that then declines to read it back.
     */
    public static boolean isMethodReference(String source) {
        if (source == null) return false;
        int arrow = source.indexOf("::");
        if (arrow <= 0 || arrow + 2 >= source.length()) return false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != ':' && c != '.' && !Character.isJavaIdentifierPart(c)) return false;
        }
        return true;
    }

    private static Optional<String> captureSource(String java) {
        String source = strip(java);
        String simple = CaptureSource.class.getSimpleName() + ".";
        String qualified = CaptureSource.class.getName() + ".";
        String rest = source.startsWith(qualified) ? source.substring(qualified.length())
                : source.startsWith(simple) ? source.substring(simple.length()) : null;
        if (rest == null || !source.endsWith(")")) return Optional.empty();
        for (String factory : List.of("desktop(", "monitor(", "window(")) {
            // The call has to *be* the whole expression: the bracket the factory opens must be closed by
            // the last character. `desktop().region(top)` starts the same way and is a narrowing, which
            // composes — a picker showing the first narrowing and not the second would be worse than one
            // that shows the whole expression read-only.
            if (rest.startsWith(factory) && closes(rest, factory.length() - 1) == rest.length() - 1) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    /** The index of the {@code )} closing the bracket at {@code open}, or {@code -1}. String-literal aware. */
    private static int closes(String source, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '(' -> depth++;
                case ')' -> {
                    if (--depth == 0) return i;
                }
                default -> { }
            }
        }
        return -1;
    }

    // ---- the five containers ---------------------------------------------------------------------------

    /** {@code Flow.of(List<Activity>, List<Edge>, List<Preset>, String, Limits)}. */
    public static final ValueContainer<Flow> FLOW_SHAPE = new Fixed<>(Flow.class, "of") {
        @Override
        public List<Object> parts(Flow value) {
            return value == null ? partsOfNone()
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
        public List<ValueForm> fixedForms() {
            return List.of(ValueForm.listOf(new ValueForm.Of(ACTIVITY_SHAPE, List.of())),
                    ValueForm.listOf(new ValueForm.Of(EDGE_SHAPE, List.of())),
                    ValueForm.listOf(new ValueForm.Of(PRESET_SHAPE, List.of())),
                    ValueForm.of(BasicsValueTypes.TEXT),
                    new ValueForm.Of(LIMITS_SHAPE, List.of()));
        }

        private List<Object> partsOfNone() {
            return List.of(List.of(), List.of(), List.of(), "", Flow.Limits.DEFAULT);
        }
    };

    /** {@code Flow.activity(ActivityBody, String, String, boolean, boolean, boolean, List<String>)}. */
    public static final ValueContainer<Flow.Activity> ACTIVITY_SHAPE =
            new Fixed<>(Flow.Activity.class, "activity", Flow.class) {
                @Override
                public List<Object> parts(Flow.Activity value) {
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
                    return sourceOf(body);
                }

                @Override
                public Flow.Activity build(List<Object> parts) {
                    if (parts.size() != 7) return null;
                    return new Flow.Activity(new Named(text(parts.get(0))), text(parts.get(1)),
                            text(parts.get(2)), flag(parts.get(3)), flag(parts.get(4)), flag(parts.get(5)),
                            list(parts.get(6)));
                }

                @Override
                public List<ValueForm> fixedForms() {
                    return List.of(ValueForm.of(ACTIVITY_BODY),
                            ValueForm.of(BasicsValueTypes.TEXT),
                            ValueForm.of(BasicsValueTypes.TEXT),
                            ValueForm.of(BasicsValueTypes.YES_NO),
                            ValueForm.of(BasicsValueTypes.YES_NO),
                            ValueForm.of(BasicsValueTypes.YES_NO),
                            ValueForm.listOf(ValueForm.of(BasicsValueTypes.TEXT)));
                }
            };

    /** {@code Flow.preset(String, List<String>)}. */
    public static final ValueContainer<Flow.Preset> PRESET_SHAPE =
            new Fixed<>(Flow.Preset.class, "preset", Flow.class) {
                @Override
                public List<Object> parts(Flow.Preset value) {
                    return value == null ? List.of("", List.of())
                            : List.of(value.name(), value.activities());
                }

                @Override
                public Flow.Preset build(List<Object> parts) {
                    return parts.size() != 2 ? null
                            : new Flow.Preset(text(parts.get(0)), list(parts.get(1)));
                }

                @Override
                public List<ValueForm> fixedForms() {
                    return List.of(ValueForm.of(BasicsValueTypes.TEXT),
                            ValueForm.listOf(ValueForm.of(BasicsValueTypes.TEXT)));
                }
            };

    /** {@code Flow.edge(String, String, String)}. */
    public static final ValueContainer<Flow.Edge> EDGE_SHAPE =
            new Fixed<>(Flow.Edge.class, "edge", Flow.class) {
                @Override
                public List<Object> parts(Flow.Edge value) {
                    return value == null ? List.of("", "", "")
                            : List.of(value.from(), value.to(), value.outcome());
                }

                @Override
                public Flow.Edge build(List<Object> parts) {
                    return parts.size() != 3 ? null
                            : new Flow.Edge(text(parts.get(0)), text(parts.get(1)), text(parts.get(2)));
                }

                @Override
                public List<ValueForm> fixedForms() {
                    return List.of(ValueForm.of(BasicsValueTypes.TEXT),
                            ValueForm.of(BasicsValueTypes.TEXT),
                            ValueForm.of(BasicsValueTypes.TEXT));
                }
            };

    /** {@code Flow.limits(int, int)}. */
    public static final ValueContainer<Flow.Limits> LIMITS_SHAPE =
            new Fixed<>(Flow.Limits.class, "limits", Flow.class) {
                @Override
                public List<Object> parts(Flow.Limits value) {
                    Flow.Limits limits = value == null ? Flow.Limits.DEFAULT : value;
                    return List.of(limits.maxSteps(), limits.stepDelayMs());
                }

                @Override
                public Flow.Limits build(List<Object> parts) {
                    return parts.size() != 2 ? Flow.Limits.DEFAULT
                            : new Flow.Limits(number(parts.get(0)), number(parts.get(1)));
                }

                @Override
                public List<ValueForm> fixedForms() {
                    return List.of(ValueForm.of(BasicsValueTypes.WHOLE_NUMBER),
                            ValueForm.of(BasicsValueTypes.WHOLE_NUMBER));
                }
            };

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
     * A container of {@linkplain ValueContainer#arity() arity zero}: a record with fixed components, taken
     * apart and put back positionally.
     *
     * <p>{@link #partForms} ignores the arguments — there are none — and answers {@link #fixedForms()} when
     * the expression has exactly that many parts, and nothing when it has any other number. A call with the
     * wrong number of arguments is not this shape, and the host's answer to a form it cannot read whole is
     * to show the expression read-only and leave it exactly as written. That is the right answer here too:
     * it is a call to something else, or to a newer version of this factory, and either way guessing which
     * of its parts line up with which of these would be rewriting code on a hunch.
     */
    private abstract static class Fixed<C> implements ValueContainer<C> {

        private final Class<?> type;
        private final String factory;
        private final Class<?> owner;

        Fixed(Class<?> type, String factory) {
            this(type, factory, type);
        }

        Fixed(Class<?> type, String factory, Class<?> owner) {
            this.type = type;
            this.factory = factory;
            this.owner = owner;
        }

        /** The static type of each component, in the order the factory takes them. */
        abstract List<ValueForm> fixedForms();

        @Override
        public final Class<?> type() {
            return type;
        }

        @Override
        public final Class<?> factoryOwner() {
            return owner;
        }

        @Override
        public final int arity() {
            return 0;
        }

        @Override
        public final String factory() {
            return factory;
        }

        @Override
        public final List<ValueForm> partForms(List<ValueForm> arguments, int parts) {
            List<ValueForm> forms = fixedForms();
            return parts == forms.size() ? forms : List.of();
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
