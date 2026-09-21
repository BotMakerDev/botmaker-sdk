package com.botmaker.sdk.internal.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.basics.values.BasicsValueTypes;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;

import java.util.List;
import java.util.Optional;

/**
 * The {@code @Managed("flow")} value, read and written as a {@link Flow}.
 *
 * <h2>The whole of what replaced {@code activities.json}</h2>
 *
 * <p>The flow editor used to read a file, parse it into a {@code ProjectModel} and write the file back. It
 * now opens one value through {@link com.botmaker.plugin.api.source.PluginValues}, reads the expression that value
 * holds and writes one expression back. Everything between — which file, which package, which buffer is
 * unsaved, how the write becomes one undo step — is the host's, and this class is the two lines of
 * translation left over.
 *
 * <h2>Why the catalog is merged here</h2>
 *
 * <p>{@link ValueCatalog#valueOf} resolves a container <em>by id, in the catalog it is called on</em>, so a
 * catalog must know every container the form mentions. A flow's form mentions plugin-basics' {@code LIST}
 * and text and yes/no leaves as well as this plugin's five shapes, which is why the merge is the same one
 * {@code SdkPlugin} builds for its parameter store rather than {@code SdkValueTypes.CATALOG} alone.
 *
 * <p><b>Reading may answer nothing, and that is ordinary.</b> A hand-written {@code flow()} body, a call to
 * something other than {@code Flow.of}, an activity whose body is a lambda rather than a method reference —
 * each of them leaves the expression exactly as the user wrote it and answers {@link Flow#NONE} here. The
 * editor's job then is to say so, not to replace it.
 */
public final class FlowValue {

    /** The id the plugin declares and the shipped {@code Sdk.java} annotates its method with. */
    public static final String ID = "flow";

    /** Every container and codec a flow's parts can mention: plugin-basics' vocabulary and this plugin's. */
    private static final ValueCatalog CATALOG = BasicsValueTypes.CATALOG.merge(SdkValueTypes.CATALOG);

    /**
     * The open project's host services, or {@code null} between projects.
     *
     * <p>Held because two readers of the flow have no {@link ValueContext} to ask through and no business
     * growing one: the tag picklist behind Capture Templates takes a resources directory, and so does the
     * template library under it. While a flow was a file, a path was enough to read it; a value in the bot's
     * own Java is the host's to open, so the host has to be reachable from somewhere that is not a value
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
        if (ctx == null) return Flow.NONE;
        return CATALOG.valueOf(ctx.form(), ctx.source())
                .filter(Flow.class::isInstance)
                .map(Flow.class::cast)
                .orElse(Flow.NONE);
    }

    /**
     * Whether {@code ctx} holds an expression this plugin can read — the question the editor asks before it
     * offers to draw one, since an unreadable value is shown rather than replaced.
     */
    public static boolean readable(ValueContext ctx) {
        return ctx != null && CATALOG.valueOf(ctx.form(), ctx.source()).isPresent();
    }

    /**
     * Writes {@code flow} into {@code ctx}, on the JavaFX application thread.
     *
     * @return null when it was written, else why it could not be
     */
    public static String write(ValueContext ctx, Flow flow) {
        if (ctx == null) return "This project has no Sdk.flow() to write to.";
        Optional<String> expression = CATALOG.initializer(ctx.form(), flow);
        if (expression.isEmpty()) {
            // Only two things reach here: a body that is not a method reference, and a part of the flow
            // whose codec declined. Both mean the editor is holding something it cannot spell, which is a
            // refusal to write rather than a write of something else.
            return "This flow can't be written back as Java — it holds something the SDK can't spell.";
        }
        ctx.set(expression.get(), imports(ctx));
        return null;
    }

    /** The imports the written expression needs, as {@link ValueContext#set} takes them. */
    private static String[] imports(ValueContext ctx) {
        List<String> imports = CATALOG.imports(ctx.form());
        return imports.toArray(new String[0]);
    }
}
