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
 * <p>Both are handles on {@link SdkValues}' declarations, so no id is spelled here.
 *
 * <p><b>Reading may answer nothing, and that is ordinary.</b> A hand-written {@code flow()} body, a call to
 * something other than {@code Flow.of}, an activity whose body is a lambda rather than a method reference —
 * each of them leaves the expression exactly as the user wrote it and answers {@link Flow#NONE} here. The
 * editor's job then is to say so, not to replace it.
 */
public final class FlowValue {

    /** The activity flow. */
    public static final ManagedHandle<Flow> FLOW = ManagedHandle.of(SdkValues.FLOW);

    /** Where each card sits, {@code Sdk.flowLayout()}. */
    public static final ManagedHandle<FlowLayout> LAYOUT = ManagedHandle.of(SdkValues.FLOW_LAYOUT);

    private FlowValue() {}

    /** The flow {@code services}' project holds, or {@link Flow#NONE}. Read afresh every time. */
    public static Flow read(StudioServices services) {
        return FLOW.read(services).orElse(Flow.NONE);
    }

    /**
     * How {@code activity}'s body is written in the file — {@code Collect::body} — or {@code ""}: the text the
     * host also reports as a slot's enclosing method, so an editor can tell which activity a body belongs to.
     */
    public static String bodySource(Flow.Step step) {
        return com.botmaker.sdk.plugin.types.FlowTypes.sourceOf(step.body());
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
