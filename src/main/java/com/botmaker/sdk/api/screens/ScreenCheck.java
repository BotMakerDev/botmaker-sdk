// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.shared.emulator.tree.UiTree;

import java.util.ArrayList;
import java.util.List;

/**
 * For a bot's own tests: which of its screens an app's captured tree shows, by the elements that tell each, as a run
 * tells them — {@code assertEquals(List.of("EMAIL"), ScreenCheck.which(Screens.class, xml))}. Only screens told by
 * elements alone can be checked: one told by a picture or by words read off a frame needs the frame.
 */
public final class ScreenCheck {

    private ScreenCheck() {}

    /**
     * The constants of {@code screens}, the bot's {@code Screens} class, whose markers the tree {@code treeXml} shows, in
     * the order the class declares them: every element that tells it found, none it is told apart by, and each that
     * must be picked picked. A screen nothing tells is never one.
     *
     * @param treeXml a {@code uiautomator dump} of the app
     * @throws IllegalArgumentException when {@code treeXml} is no tree, or a screen is told by a picture or by words
     */
    public static List<String> which(Class<?> screens, String treeXml) {
        UiTree tree = UiTree.parse(treeXml);
        ElementMatch.Matcher matcher = ElementMatch.in(tree);
        ScreenGraph graph = ScreenMaps.read(screens);
        List<String> shown = new ArrayList<>();
        for (String constant : graph.screens().keySet()) {
            Markers markers = graph.markers(constant);
            if (!markers.shown().isEmpty() || !markers.either().isEmpty() || !markers.absent().isEmpty()
                    || !markers.texts().isEmpty() || !markers.absentTexts().isEmpty()) {
                throw new IllegalArgumentException(constant + " is told by a picture or by words read off a frame:"
                        + " a tree alone cannot tell it");
            }
            if (!markers.any()) continue;
            boolean on = markers.elements().stream().allMatch(e -> !matcher.matches(e).isEmpty())
                    && markers.absentElements().stream().allMatch(e -> matcher.matches(e).isEmpty())
                    && markers.picked().stream().allMatch(e -> matcher.matches(e).stream()
                            .anyMatch(node -> ElementMatch.state(node) == State.SELECTED));
            if (on) shown.add(constant);
        }
        return List.copyOf(shown);
    }
}
