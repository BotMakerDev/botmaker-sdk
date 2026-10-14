package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The pictures of each screen of the bot's map, read off its {@code Screens} class rather than stored (the user's
 * call, 2026-10-14): a screen's group is the pictures its marker and its inputs name. Like an activity's tag it is
 * nobody's to edit here; a picture used on two screens is under both, and a renamed picture follows its constant.
 */
final class ScreenGroups {

    private static final ManagedSet<GameScreen> SCREENS = ManagedSet.of(SdkValues.SCREEN_MAP);

    private ScreenGroups() {}

    /** Each screen's group of {@code files}, keyed by {@link TemplateGalleryModel#screenKey}. On the FX thread. */
    static Map<String, List<Path>> of(StudioServices services, List<Path> files) {
        PluginValues values = services.pluginValues();
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        try {
            if (values != null) {
                for (Member member : SCREENS.members(values)) {
                    SCREENS.read(values, member).ifPresent(screen -> screens.put(member.name(), screen));
                }
            }
        } catch (RuntimeException unreadable) {
            // A map that will not read costs its groups, never the pictures window.
            return Map.of();
        }
        return groups(ScreenGraph.of(screens), files);
    }

    /**
     * {@code graph}'s screens, each to the {@code files} it names, in the map's order; a screen naming none of them
     * is still a group, the way a declared tag with nothing in it is a row.
     */
    static Map<String, List<Path>> groups(ScreenGraph graph, List<Path> files) {
        Map<String, Path> byName = new LinkedHashMap<>();
        for (Path file : files) byName.putIfAbsent(TemplateLibrary.baseName(file), file);
        Map<String, List<Path>> groups = new LinkedHashMap<>();
        for (String constant : graph.screens().keySet()) {
            List<Path> named = graph.pictures(constant).stream().map(byName::get).filter(Objects::nonNull).toList();
            groups.merge(TemplateGalleryModel.screenKey(graph.shown(constant)), named,
                    (a, b) -> Stream.concat(a.stream(), b.stream()).distinct().toList());
        }
        return groups;
    }
}
