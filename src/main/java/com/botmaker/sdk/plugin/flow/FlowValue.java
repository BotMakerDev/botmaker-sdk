package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.flow.Flow;

import java.util.Optional;

/**
 * The {@code @Managed("flow")} value, read and written as a {@link Flow}.
 *
 * <p>The flow editor opens one value through {@link com.botmaker.plugin.api.source.PluginValues}, reads the
 * {@link Flow} the host decoded and hands one back. Everything between — which file, which package, which
 * buffer is unsaved, how the write becomes one undo step — is the host's.
 *
 * <p><b>Reading may answer nothing, and that is ordinary.</b> A hand-written {@code flow()} body, a call to
 * something other than {@code Flow.of}, an activity whose body is a lambda rather than a method reference —
 * each of them leaves the expression exactly as the user wrote it and answers {@link Flow#NONE} here. The
 * editor's job then is to say so, not to replace it.
 */
public final class FlowValue {

    /** The id the plugin declares and the shipped {@code Sdk.java} annotates its method with. */
    public static final String ID = "flow";

    /**
     * The open project's host services, or {@code null} between projects.
     *
     * <p>Held because two readers of the flow have no {@link ValueContext} to ask through and no business
     * growing one: the tag picklist behind Capture Templates takes a resources directory, and so does the
     * template library under it. A value in the bot's own Java is the host's to open, so the host has to be reachable from somewhere that is not a value
     * cell. This is that somewhere, written and cleared by {@code SdkPlugin} on the two lifecycle methods
     * the contract already has for it.
     *
     * <p>Touched only on the JavaFX thread — a project open, a project close and a dialog are all there —
     * and {@code volatile} anyway, because being wrong about that costs a stale project's flow.
     */
    private static volatile StudioServices bound;

    private FlowValue() {}

    /** Takes the project being bound; see {@link #bound}. */
    public static void bind(StudioServices services) {
        bound = services;
    }

    /** Lets go of the closing project: reading one the user has left is worse than reading none. */
    public static void unbind() {
        bound = null;
    }

    /** The value behind {@code flow}, or empty when the project has none to edit. */
    public static Optional<ValueContext> open(StudioServices services) {
        return services == null ? Optional.empty() : services.pluginValues().open(ID);
    }

    /**
     * The flow of the project currently open, or {@link Flow#NONE}.
     *
     * <p>Read afresh every time and cached nowhere, which is the rule the file it replaced was read under:
     * the flow is edited by a canvas, and a copy here would be one more thing to invalidate.
     */
    public static Flow current() {
        return open(bound).map(FlowValue::read).orElse(Flow.NONE);
    }

    /**
     * The flow {@code ctx} currently holds, or {@link Flow#NONE} when its expression is not one this
     * plugin wrote.
     */
    public static Flow read(ValueContext ctx) {
        // The host decodes it, through the ComponentTypes this plugin declares in FlowTypes, so there is
        // one reader of this Java and it cannot disagree with itself.
        return ctx == null ? Flow.NONE : ctx.value(Flow.class).orElse(Flow.NONE);
    }

    /**
     * Whether {@code ctx} holds an expression this plugin can read — the question the editor asks before it
     * offers to draw one, since an unreadable value is shown rather than replaced.
     */
    public static boolean readable(ValueContext ctx) {
        return ctx != null && ctx.value(Flow.class).isPresent();
    }

    /**
     * Writes {@code flow} into {@code ctx}, on the JavaFX application thread.
     *
     * @return null when it was written, else why it could not be
     */
    public static String write(ValueContext ctx, Flow flow) {
        if (ctx == null) return "This project has no Sdk.flow() to write to.";
        // The value, and the host spells it: it walks FlowTypes' five declarations, writes each
        // component by its own rule and arranges the imports. There is no failure to report from here any
        // more — an activity whose body is not a method reference is refused where it is typed, which is
        // one screen earlier and names the activity.
        ctx.set(flow);
        return null;
    }
}
