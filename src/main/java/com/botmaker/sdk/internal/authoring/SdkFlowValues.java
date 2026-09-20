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
 * The shapes a {@code @Managed} value of this plugin's takes: the flow's four records as containers, and the
 * two leaves whose value is a <b>name</b> rather than data.
 *
 * <h2>Why a flow is four containers and not one type</h2>
 *
 * <p>A {@link ValueContainer} is what lets the editor take an expression apart into typed parts, change one
 * of them and put the rest back exactly as written. One opaque {@code FLOW} leaf with a codec would give the
 * editor a string — which is where this whole design started, and what it exists to leave behind. Four
 * containers give it {@code Flow.of(List.of(Flow.activity(…), …), List.of(…), "Collect", Flow.limits(…))} as
 * a tree it can walk, with a leaf codec at every tip.
 *
 * <p><b>All four have arity zero</b>, which the contract allows since 2026-09-20: they take no type
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
            SdkFlowValues::strip, s -> s, s -> s, SdkFlowValues::methodReference);

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

    private static Optional<String> methodReference(String java) {
        String source = strip(java);
        int arrow = source.indexOf("::");
        if (arrow <= 0 || arrow + 2 >= source.length()) return Optional.empty();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != ':' && c != '.' && !Character.isJavaIdentifierPart(c)) return Optional.empty();
        }
        return Optional.of(source);
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

    // ---- the four containers ---------------------------------------------------------------------------

    /** {@code Flow.of(List<Activity>, List<Edge>, String, Limits)}. */
    public static final ValueContainer<Flow> FLOW_SHAPE = new Fixed<>(Flow.class, "of") {
        @Override
        public List<Object> parts(Flow value) {
            return value == null ? partsOfNone()
                    : List.of(value.activities(), value.edges(), value.start(), value.limits());
        }

        @Override
        public Flow build(List<Object> parts) {
            return parts.size() != 4 ? Flow.NONE : new Flow(list(parts.get(0)), list(parts.get(1)),
                    text(parts.get(2)), parts.get(3) instanceof Flow.Limits l ? l : Flow.Limits.DEFAULT);
        }

        @Override
        public List<ValueForm> fixedForms() {
            return List.of(ValueForm.listOf(new ValueForm.Of(ACTIVITY_SHAPE, List.of())),
                    ValueForm.listOf(new ValueForm.Of(EDGE_SHAPE, List.of())),
                    ValueForm.of(BasicsValueTypes.TEXT),
                    new ValueForm.Of(LIMITS_SHAPE, List.of()));
        }

        private List<Object> partsOfNone() {
            return List.of(List.of(), List.of(), "", Flow.Limits.DEFAULT);
        }
    };

    /** {@code Flow.activity(ActivityBody, String, String, boolean, boolean, List<String>)}. */
    public static final ValueContainer<Flow.Activity> ACTIVITY_SHAPE =
            new Fixed<>(Flow.Activity.class, "activity", Flow.class) {
                @Override
                public List<Object> parts(Flow.Activity value) {
                    return value == null ? List.of("", "", "", false, false, List.of())
                            : List.of(body(value.body()), value.name(), value.description(),
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
                    return body instanceof Named named ? named.source() : "";
                }

                @Override
                public Flow.Activity build(List<Object> parts) {
                    if (parts.size() != 6) return null;
                    return new Flow.Activity(new Named(text(parts.get(0))), text(parts.get(1)),
                            text(parts.get(2)), flag(parts.get(3)), flag(parts.get(4)), list(parts.get(5)));
                }

                @Override
                public List<ValueForm> fixedForms() {
                    return List.of(ValueForm.of(ACTIVITY_BODY),
                            ValueForm.of(BasicsValueTypes.TEXT),
                            ValueForm.of(BasicsValueTypes.TEXT),
                            ValueForm.of(BasicsValueTypes.YES_NO),
                            ValueForm.of(BasicsValueTypes.YES_NO),
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
     * <p>{@link #partForms} ignores the arguments — there are none — and answers {@link #fixedForms()},
     * truncated or padded to however many parts were actually written. Padding rather than refusing is what
     * lets a file written by an older SDK, with one component fewer, still be read and shown: the host's
     * own rule is that a form it cannot read whole is displayed and left alone, and a length mismatch is
     * caught there rather than thrown here.
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
