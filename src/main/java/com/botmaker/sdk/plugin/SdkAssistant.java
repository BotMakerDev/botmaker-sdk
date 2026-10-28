package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.AssistantHolder;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.sdk.plugin.assist.SdkAssist;
import com.botmaker.sdk.plugin.map.MapReport;

import java.util.List;

/**
 * The assistant's tools: {@link SdkAssist}'s and the map's ({@link MapReport}), which {@code assist} cannot name
 * because no two packages under {@code plugin} name each other ({@code PluginLayersTest}).
 */
final class SdkAssistant implements AssistantHolder {

    @Override
    public List<AssistantTool<?>> tools() {
        return SdkAssist.and(MapReport.TOOLS);
    }
}
