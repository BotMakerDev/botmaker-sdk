package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.internal.bot.SdkValues;

/**
 * The {@code @Managed("flow")} value and the card positions beside it, read and written as a {@link Flow} and
 * a {@link FlowLayout}.
 *
 * <p>Both are handles on {@link SdkValues}' declarations, so no id is spelled here (2026-09-28). What is left
 * is the one thing a handle cannot know: the project currently bound, for the two readers of the flow that
 * have no value cell to ask through.
 *
 * <p><b>Reading may answer nothing, and that is ordinary.</b> A hand-written {@code flow()} body, a call to
 * something other than {@code Flow.of}, an activity whose body is a lambda rather than a method reference —
 * each of them leaves the expression exactly as the user wrote it and answers {@link Flow#NONE} here. The
 * editor's job then is to say so, not to replace it.
 */
public final class FlowValue {

    /** The activity flow. */
    public static final ManagedHandle<Flow> FLOW = ManagedHandle.of(SdkValues.FLOW);

    /** Where each card sits, {@code Sdk.flowLayout()} (2026-09-27). */
    public static final ManagedHandle<FlowLayout> LAYOUT = ManagedHandle.of(SdkValues.FLOW_LAYOUT);

    /**
     * The open project's host services, or {@code null} between projects.
     *
     * <p>Held because two readers of the flow have no {@link ValueContext} to ask through and no business
     * growing one: the tag picklist behind Capture Templates takes a resources directory, and so does the
     * template library under it. Written and cleared by {@code SdkPlugin} on the two lifecycle methods the
     * contract already has for it; {@code volatile} because being wrong costs a stale project's flow.
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

    /** The flow of the project currently open, or {@link Flow#NONE}. Read afresh every time. */
    public static Flow current() {
        return FLOW.read(bound).orElse(Flow.NONE);
    }

    /** The flow {@code services}' project holds, or {@link Flow#NONE}. */
    public static Flow read(StudioServices services) {
        return FLOW.read(services).orElse(Flow.NONE);
    }

    /** The flow {@code ctx} holds, or {@link Flow#NONE} when its expression is not one this plugin wrote. */
    public static Flow read(ValueContext ctx) {
        return FLOW.read(ctx).orElse(Flow.NONE);
    }

    /**
     * Writes {@code flow} into {@code ctx}, on the JavaFX application thread. An activity whose body is not a
     * method reference is refused where it is typed, so there is nothing else to report from here.
     *
     * @return null when it was written, else why it could not be
     */
    public static String write(ValueContext ctx, Flow flow) {
        if (ctx == null) return "This project has no Sdk.flow() to write to.";
        ctx.set(flow);
        return null;
    }
}
