package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Flows;
import com.botmaker.sdk.internal.vision.TemplateNames;

import java.util.List;

/**
 * The SDK's {@code @Managed} values, each declared once, and what a run does with each one.
 *
 * <p>Five declarations, and every id is spelled here and nowhere else (2026-09-28): the plugin lists
 * {@link #ALL} as its {@code managedValues()}, its windows open each one through a toolkit
 * {@code ManagedHandle}, and {@link #claim()} hands each to the runtime typed. {@code "flow"} is the activity
 * flow, {@code "flow.layout"} where the flow editor draws each card (a run ignores it), {@code "capture"} where
 * pixels are read from, {@code "settings"} how it clicks and looks, and {@code "pictures"} the class of picture
 * constants the bot grows.
 *
 * <p><b>It is {@code internal} because a bot never names it</b>, and the plugin half may. It names contract
 * and {@code api} types only, so it is safe in a bot. {@link com.botmaker.sdk.api.bot.Bot#run} calls
 * {@link #claim()} before installing, which is the only ordering anyone has to get right.
 */
public final class SdkValues {

    /** The class the method-shaped values live in: {@code plugins/sdk/Sdk.java}, named by the bot's {@code main}. */
    public static final String HOLDER = "Sdk";

    public static final ManagedValue<Flow> FLOW = ManagedValue.method("flow")
            .in(HOLDER)
            .holds(Flow.class, Flow.NONE)
            .because("This is the bot's activity flow. Draw it in 🔀 Activity Flow, which keeps the activities,"
                    + " the wires and the layout in step.");

    public static final ManagedValue<FlowLayout> FLOW_LAYOUT = ManagedValue.method("flow.layout")
            .in(HOLDER)
            .holds(FlowLayout.class, FlowLayout.NONE)
            .because("These are where the Activity Flow's cards sit. Drag them in 🔀 Activity Flow, which keeps"
                    + " them in step with the activities' names.");

    public static final ManagedValue<CaptureSource> CAPTURE = ManagedValue.method("capture")
            .in(HOLDER)
            .holds(CaptureSource.class, CaptureSource.desktop())
            .because("This is where the bot reads pixels from. Choose it in 🎯 Capture Source.");

    public static final ManagedValue<BotSettings> SETTINGS = ManagedValue.method("settings")
            .in(HOLDER)
            .holds(BotSettings.class, BotSettings.DEFAULTS)
            .because("These are the bot's settings — delays, confidence, input and its private display. Change"
                    + " them in ⚙ Bot Settings.");

    /**
     * The picture constants — {@code static final ImageTemplate COLLECT = new ImageTemplate(…)}. 🖼 Manage
     * Pictures renames the file, the constant and every use of it together; the canvas can only rename the one
     * it is looking at, which would leave the bot calling a name that is gone.
     */
    public static final ManagedValue<Void> PICTURES = ManagedValue.openSet("pictures")
            .in(TemplateNames.CLASS_NAME)
            .because("Picture constants are managed in 🖼 Manage Pictures, which renames the picture and every"
                    + " use of it together.");

    /** All five, in the order the plugin declares them. */
    public static final List<ManagedValue<?>> ALL = List.of(FLOW, FLOW_LAYOUT, CAPTURE, SETTINGS, PICTURES);

    private static boolean claimed;

    private SdkValues() {}

    /**
     * Registers this plugin's values with {@link ManagedValues}. Idempotent, and cheap enough to be called on
     * every {@code Bot.run} rather than guarded by its caller.
     */
    public static synchronized void claim() {
        if (claimed) {
            return;
        }
        claimed = true;
        ManagedValues.claim(FLOW, Flows::use);
        ManagedValues.claim(CAPTURE, Source::set);
        ManagedValues.claim(SETTINGS, BotSettings::use);
        // Where the flow editor's cards sit: the editor's, and nothing at runtime reads it. Claimed so a run
        // does not report it as a value nobody takes.
        ManagedValues.claim(FLOW_LAYOUT, layout -> { });
    }
}
