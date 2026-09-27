package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.basics.managed.ManagedValues;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Flows;

/**
 * Which of a bot's {@code @Managed} values are this plugin's, and what happens to each one.
 *
 * <p>Four ids, and they are the four the SDK ships a declaration for in {@code Sdk.java}: {@code "flow"} is
 * the activity flow, {@code "capture"} is where pixels are read from, {@code "settings"} is how it clicks and
 * looks, and {@code "flow.layout"} is where the flow editor draws each card, which a run ignores (2026-09-27). A bot's own file declares them; this says what {@code Flows}, {@code Source} and
 * {@code BotSettings} do with what it declared.
 *
 * <p><b>It is {@code internal} because a bot never names it.</b> {@link com.botmaker.sdk.api.bot.Bot#run}
 * calls {@link #claim()} before installing, which is the only ordering anyone has to get right — and the
 * only reason it is a call rather than a static initialiser is that a static initialiser would need
 * something to have loaded this class first, and nothing would have.
 *
 * <p>A cast that fails here cannot happen from a file the editor wrote: the id and the method's declared
 * return type are paired by the same plugin. A hand-edited {@code @Managed("flow") String flow()} would,
 * and {@code ManagedValues} reports it by name rather than taking the bot down.
 */
public final class SdkValues {

    private static boolean claimed;

    private SdkValues() {}

    /**
     * Registers this plugin's ids with {@link ManagedValues}. Idempotent, and cheap enough to be called on
     * every {@code Bot.run} rather than guarded by its caller.
     */
    public static synchronized void claim() {
        if (claimed) {
            return;
        }
        claimed = true;
        ManagedValues.claim("flow", value -> Flows.use((Flow) value));
        ManagedValues.claim("capture", value -> Source.set((CaptureSource) value));
        ManagedValues.claim("settings", value -> BotSettings.use((BotSettings) value));
        // Where the flow editor's cards sit: the editor's, and nothing at runtime reads it. Claimed so a run
        // does not report it as a value nobody takes.
        ManagedValues.claim("flow.layout", value -> { });
    }
}
