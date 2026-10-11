package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;

import java.util.ArrayList;
import java.util.List;

/**
 * The SDK's tools for the AI assistant that drives Studio: look at what the bot sees ({@link LookTools}), make
 * its pictures and places ({@link PictureTools}), shape its activity flow ({@link FlowTools}), and set where it
 * looks and how it runs ({@link SetupTools}).
 *
 * <p>Coordinates are the <b>screenshot's</b> pixels: {@code (0, 0)} is the top-left of what {@code screenshot}
 * returned, so the assistant crops what it was shown. Every write goes through the same path as the SDK's own
 * windows, on the FX thread ({@link FxCall}): the picture files and their {@code Pictures} constants, the
 * {@code Points} and {@code Regions} constants, the flow and its {@code Activities}/{@code Outcomes} constants,
 * {@code Sdk.captureSource()} and {@code Sdk.settings()}, and this machine's launch target. Nothing clicks,
 * types or starts anything; the assistant acts on the game only by running the bot.
 */
public final class SdkAssist {

    /** Every tool, by group. */
    public static final List<AssistantTool<?>> ALL = join(LookTools.TOOLS, PictureTools.TOOLS, FlowTools.TOOLS,
            SetupTools.TOOLS);

    private SdkAssist() {}

    @SafeVarargs
    private static List<AssistantTool<?>> join(List<AssistantTool<?>>... groups) {
        List<AssistantTool<?>> all = new ArrayList<>();
        for (List<AssistantTool<?>> group : groups) all.addAll(group);
        return List.copyOf(all);
    }
}
